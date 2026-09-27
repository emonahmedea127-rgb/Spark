const headers={'Content-Type':'application/json','Cache-Control':'no-store'};
const reply=(status:number,message:string)=>new Response(JSON.stringify({message}),{status,headers});
Deno.serve(async(req:Request)=>{
 if(req.method!=='POST')return reply(405,'Use POST');
 const url=Deno.env.get('SUPABASE_URL')!,anon=Deno.env.get('SUPABASE_ANON_KEY')!,service=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
 const authorization=req.headers.get('Authorization')??'';
 try {
  const userResponse=await fetch(`${url}/auth/v1/user`,{headers:{apikey:anon,Authorization:authorization}});
  if(!userResponse.ok)return reply(401,'Sign in required');
  const user=await userResponse.json(),input=await req.json();
  if(!/^[0-9a-f-]{36}$/i.test(input.call_id??''))return reply(400,'Invalid call');
  const result=await fetch(`${url}/rest/v1/sparknew_calls?id=eq.${input.call_id}&select=id,status,created_at`,{headers:{apikey:anon,Authorization:authorization}});
  if(!result.ok)return reply(403,'Call unavailable');
  const call=(await result.json())[0];
  if(!call||!['ringing','accepted'].includes(call.status)||Date.parse(call.created_at)<Date.now()-3600000)return reply(403,'Call unavailable');
  const key=Deno.env.get('CLOUDFLARE_TURN_KEY_ID'),token=Deno.env.get('CLOUDFLARE_TURN_API_TOKEN');
  const budget=Number(Deno.env.get('TURN_MONTHLY_ISSUE_LIMIT')??'0');
  if(Deno.env.get('TURN_ENABLED')!=='true'||!key||!token||!Number.isSafeInteger(budget)||budget<1)return reply(503,'TURN is not configured');
  const reserve=await fetch(`${url}/rest/v1/rpc/sparknew_reserve_turn`,{method:'POST',headers:{apikey:service,Authorization:`Bearer ${service}`,'Content-Type':'application/json'},body:JSON.stringify({actor_id:user.id,monthly_limit:budget})});
  if(!reserve.ok||!(await reserve.json()))return reply(429,'Relay allocation limit reached');
  const response=await fetch(`https://rtc.live.cloudflare.com/v1/turn/keys/${encodeURIComponent(key)}/credentials/generate-ice-servers`,{method:'POST',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/json'},body:JSON.stringify({ttl:3600}),signal:AbortSignal.timeout(10000)});
  if(!response.ok)return reply(503,'Relay temporarily unavailable');
  const data=await response.json();
  if(!Array.isArray(data.iceServers))return reply(503,'Relay temporarily unavailable');
  return new Response(JSON.stringify({iceServers:data.iceServers,expiresIn:3600}),{headers});
 }catch{return reply(503,'Relay temporarily unavailable');}
});
