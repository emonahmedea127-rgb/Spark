// No secret or reusable media URL is returned to Android.
const headers = { 'Content-Type': 'application/json', 'Cache-Control': 'no-store, private' };
const reply = (status:number, message:string) => new Response(JSON.stringify({message}), {status,headers});
Deno.serve(async (req:Request) => {
  if(req.method !== 'POST') return reply(405,'Use POST');
  const authorization=req.headers.get('Authorization')??'';
  if(!authorization.startsWith('Bearer ')) return reply(401,'Sign in required');
  const url=Deno.env.get('SUPABASE_URL')!;
  const anon=Deno.env.get('SUPABASE_ANON_KEY')!;
  const service=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!;
  try {
    const auth=await fetch(`${url}/auth/v1/user`,{headers:{apikey:anon,Authorization:authorization}});
    if(!auth.ok)return reply(401,'Sign in again');
    const user=await auth.json();
    const input=await req.json();
    if(!/^[0-9a-f-]{36}$/i.test(input.message_id??''))return reply(400,'Invalid message');
    const serverHeaders={apikey:service,Authorization:`Bearer ${service}`,'Content-Type':'application/json'};
    const claim=await fetch(`${url}/rest/v1/rpc/sparknew_claim_once`,{method:'POST',headers:serverHeaders,body:JSON.stringify({message_id:input.message_id,viewer_id:user.id})});
    if(!claim.ok)return reply(503,'Unable to open photo');
    const path=await claim.json();
    if(typeof path!=='string'||!path)return reply(410,'This photo has already been opened or is unavailable');
    const media=await fetch(`${url}/storage/v1/object/authenticated/spark-media-v1/${path.split('/').map(encodeURIComponent).join('/')}`,{headers:serverHeaders});
    if(!media.ok)return reply(410,'Photo is no longer available');
    const bytes=await media.arrayBuffer();
    // Delete stored bytes after the one-time response has been prepared.
    await fetch(`${url}/storage/v1/object/spark-media-v1`,{method:'DELETE',headers:serverHeaders,body:JSON.stringify({prefixes:[path]})});
    return new Response(bytes,{headers:{'Content-Type':media.headers.get('Content-Type')??'image/jpeg','Cache-Control':'no-store, private','Pragma':'no-cache','X-Content-Type-Options':'nosniff'}});
  }catch{return reply(503,'Unable to open photo. Please check your connection');}
});
