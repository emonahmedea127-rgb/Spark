const test=require('node:test'),assert=require('node:assert/strict'),vm=require('node:vm'),fs=require('node:fs');
const {stripTypeScriptTypes}=require('node:module');
function load(name,env={},fetch=()=>{throw Error('Unexpected outbound request')}) {
 let handler;
 const context={Deno:{env:{get:key=>env[key]},serve:fn=>handler=fn},Response,Request,TextEncoder,Uint8Array,URLSearchParams,AbortSignal,crypto:require('node:crypto').webcrypto,atob,btoa,fetch,Date};
 vm.runInNewContext(stripTypeScriptTypes(fs.readFileSync(`backend/functions/${name}/index.ts`,'utf8')),context);
 return (body={},headers={})=>handler(new Request('https://example.test',{method:'POST',headers,body:JSON.stringify(body)}));
}
const id='11111111-1111-4111-8111-111111111111';
const call={id,status:'ringing',created_at:new Date().toISOString()};
function turnFetch({user=true,calls=[call],quota=true}={}) {return async(url)=>{
 if(url.includes('/auth/'))return Response.json({id},{status:user?200:401});
 if(url.includes('/sparknew_calls'))return Response.json(calls);
 if(url.includes('/rpc/'))return Response.json(quota);
 if(url.includes('rtc.live'))return Response.json({iceServers:[{urls:['turn:example.test'],username:'short-lived',credential:'temporary'}]});
 throw Error('Unexpected URL');
};}
const env={SUPABASE_URL:'https://example.test',SUPABASE_ANON_KEY:'anon',SUPABASE_SERVICE_ROLE_KEY:'server',TURN_ENABLED:'true',CLOUDFLARE_TURN_KEY_ID:'key',CLOUDFLARE_TURN_API_TOKEN:'secret',TURN_MONTHLY_ISSUE_LIMIT:'100'};
test('TURN denies unsigned users',async()=>assert.equal((await load('spark-turn',env,turnFetch({user:false}))({call_id:id})).status,401));
test('TURN denies inaccessible calls',async()=>assert.equal((await load('spark-turn',env,turnFetch({calls:[]}))({call_id:id})).status,403));
test('TURN denies ended calls',async()=>assert.equal((await load('spark-turn',env,turnFetch({calls:[{...call,status:'ended'}]}))({call_id:id})).status,403));
test('TURN disabled without provider configuration',async()=>assert.equal((await load('spark-turn',{},turnFetch())({call_id:id})).status,503));
test('TURN quota rejection stops credential issuance',async()=>assert.equal((await load('spark-turn',env,turnFetch({quota:false}))({call_id:id})).status,429));
test('TURN authorized call gets ephemeral ICE credentials',async()=>{const r=await load('spark-turn',env,turnFetch())({call_id:id});assert.equal(r.status,200);assert.equal((await r.json()).expiresIn,3600)});
test('Push is disabled without webhook secret',async()=>assert.equal((await load('spark-push')()).status,503));
test('Push rejects incorrect webhook credentials',async()=>assert.equal((await load('spark-push',{SPARK_PUSH_WEBHOOK_SECRET:'a'.repeat(32)})()).status,401));
test('Push does not call Firebase without provider credentials',async()=>assert.equal((await load('spark-push',{SPARK_PUSH_WEBHOOK_SECRET:'a'.repeat(32)})({}, {'X-Spark-Push-Secret':'a'.repeat(32)})).status,503));
