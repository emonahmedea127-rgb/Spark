const headers={'Content-Type':'application/json','Cache-Control':'no-store'};
const reply=(status:number,message:string)=>new Response(JSON.stringify({message}),{status,headers});
const enc=new TextEncoder();
const b64=(v:Uint8Array)=>btoa(String.fromCharCode(...v)).replace(/=/g,'').replace(/\+/g,'-').replace(/\//g,'_');
let cachedToken='',tokenUntil=0;
async function accessToken(account:any):Promise<string>{
 if(cachedToken&&Date.now()<tokenUntil)return cachedToken;
 const now=Math.floor(Date.now()/1000);
 const head=b64(enc.encode(JSON.stringify({alg:'RS256',typ:'JWT'})));
 const claims=b64(enc.encode(JSON.stringify({iss:account.client_email,scope:'https://www.googleapis.com/auth/firebase.messaging',aud:'https://oauth2.googleapis.com/token',iat:now,exp:now+3600})));
 const pem=account.private_key.replace(/-----[^-]+-----|\s/g,'');
 const bytes=Uint8Array.from(atob(pem),(c:string)=>c.charCodeAt(0));
 const key=await crypto.subtle.importKey('pkcs8',bytes,{name:'RSASSA-PKCS1-v1_5',hash:'SHA-256'},false,['sign']);
 const signature=new Uint8Array(await crypto.subtle.sign('RSASSA-PKCS1-v1_5',key,enc.encode(`${head}.${claims}`)));
 const response=await fetch('https://oauth2.googleapis.com/token',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({grant_type:'urn:ietf:params:oauth:grant-type:jwt-bearer',assertion:`${head}.${claims}.${b64(signature)}`}),signal:AbortSignal.timeout(10000)});
 if(!response.ok)throw Error('Messaging authorization failed');
 const data=await response.json();cachedToken=data.access_token;tokenUntil=Date.now()+Math.min(Number(data.expires_in)||3600,3500)*1000;return cachedToken;
}
Deno.serve(async(req:Request)=>{
 if(req.method!=='POST')return reply(405,'Use POST');
 const secret=Deno.env.get('SPARK_PUSH_WEBHOOK_SECRET');
 if(!secret||secret.length<32)return reply(503,'Push is not configured');
 const incoming=req.headers.get('X-Spark-Push-Secret')??'';
 // Compare fixed-length digests without leaking the shared credential.
 const hash=async(s:string)=>new Uint8Array(await crypto.subtle.digest('SHA-256',enc.encode(s)));
 const [a,b]=await Promise.all([hash(secret),hash(incoming)]);let diff=0;for(let i=0;i<a.length;i++)diff|=a[i]^b[i];
 if(diff!==0)return reply(401,'Unauthorized');
 const config=Deno.env.get('FIREBASE_SERVICE_ACCOUNT_JSON');
 if(!config)return reply(503,'Firebase is not configured');
 const url=Deno.env.get('SUPABASE_URL')!,service=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
 const dbHeaders={apikey:service,Authorization:`Bearer ${service}`,'Content-Type':'application/json'};
 async function db(path:string,method='GET',body?:unknown){const r=await fetch(`${url}/rest/v1/${path}`,{method,headers:dbHeaders,body:body===undefined?undefined:JSON.stringify(body),signal:AbortSignal.timeout(10000)});if(!r.ok)throw Error('Queue unavailable');return r.status===204?null:await r.json();}
 try{
  const account=JSON.parse(config),oauth=await accessToken(account);
  const batch=await db('rpc/sparknew_claim_push','POST',{});
  let sent=0;
  for(const job of batch){
   try{
    const call=job.event_type==='call';
    const rows=await db(`${call?'sparknew_calls':'sparknew_notifications'}?id=eq.${job.event_id}&select=*`),event=rows[0];
    if(!event||(call?event.callee_id:event.recipient_id)!==job.recipient_id||(call&&(event.status!=='ringing'||Date.parse(event.created_at)<Date.now()-90000))||(!call&&event.is_read)){
     await db(`sparknew_push_queue?id=eq.${job.id}`,'PATCH',{state:'expired'});continue;
    }
    const actor=call?event.caller_id:event.actor_id;
    const blocked=await db(`sparknew_blocks?or=(and(owner_id.eq.${actor},target_id.eq.${job.recipient_id}),and(owner_id.eq.${job.recipient_id},target_id.eq.${actor}))&select=owner_id&limit=1`);
    if(blocked.length){await db(`sparknew_push_queue?id=eq.${job.id}`,'PATCH',{state:'expired'});continue;}
    const devices=await db(`sparknew_push_devices?user_id=eq.${job.recipient_id}&select=token&limit=20`);
    let failed=false;
    for(const device of devices){
     const remaining=Math.max(0,Math.min(call?90:86400,Math.floor((Date.parse(job.expires_at)-Date.now())/1000)));
     const response=await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(account.project_id)}/messages:send`,{method:'POST',headers:{Authorization:`Bearer ${oauth}`,'Content-Type':'application/json'},body:JSON.stringify({message:{token:device.token,data:{event_id:job.id,recipient_id:job.recipient_id,kind:call?'call':String(event.kind??'notice'),target_id:String((call?event.id:event.target_id)??''),title:call?(event.video?'Incoming video call':'Incoming audio call'):'Spark',body:call?'Tap to answer':event.kind==='message'?'You have a new message':'You have a new notification',expires_at:job.expires_at},android:{priority:'HIGH',ttl:`${remaining}s`}}}),signal:AbortSignal.timeout(10000)});
     if(!response.ok){const error=await response.json().catch(()=>({}));const gone=(error.error?.details??[]).some((x:any)=>x.errorCode==='UNREGISTERED');if(gone)await db(`sparknew_push_devices?token=eq.${encodeURIComponent(device.token)}`,'DELETE');else failed=true;}
    }
    await db(`sparknew_push_queue?id=eq.${job.id}`,'PATCH',{state:failed?(job.attempts>=5?'failed':'pending'):'sent',available_at:new Date(Date.now()+60000).toISOString()});if(!failed)sent++;
   }catch{await db(`sparknew_push_queue?id=eq.${job.id}`,'PATCH',{state:job.attempts>=5?'failed':'pending',available_at:new Date(Date.now()+60000).toISOString()}).catch(()=>{});}
  }
  return new Response(JSON.stringify({processed:batch.length,sent}),{headers});
 }catch{return reply(503,'Push delivery temporarily unavailable');}
});
