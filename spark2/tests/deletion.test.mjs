import { test } from 'node:test';
import assert from 'node:assert/strict';

let handler;
const uid='11111111-1111-4111-8111-111111111111';
globalThis.Deno={env:{get:name=>name==='SUPABASE_URL'?'https://example.supabase.co':'server-test-key'},serve:fn=>{handler=fn;}};
await import('../supabase/functions/delete-account/index.ts');
const request=(body={confirmation:'DELETE',password:'test-password-123'}, authorization='Bearer test-user-token')=>new Request('https://function.test/delete-account',{
  method:'POST',headers:{'Content-Type':'application/json',Authorization:authorization},body:JSON.stringify(body),
});
function fakeFetch(replies) {
  const calls=[];
  globalThis.fetch=async(url,init)=>{
    calls.push({url,init});
    const next=replies.shift();
    if(!next) throw new Error('Unexpected outbound call');
    return new Response(JSON.stringify(next.body??{}),{status:next.status??200,headers:{'Content-Type':'application/json'}});
  };
  return calls;
}
const user={body:{id:uid,email:'user@example.com'}};
const verified={body:{user:{id:uid},access_token:'fresh-test-token'}};
test('deletion endpoint refuses missing bearer tokens without any backend calls',async()=>{
  const calls=fakeFetch([]);assert.equal((await handler(request({},''))).status,401);assert.equal(calls.length,0);
});
test('deletion rejects an invalid JWT before admin operations',async()=>{
  const calls=fakeFetch([{status:401}]);assert.equal((await handler(request())).status,401);assert.equal(calls.length,1);
});
test('deletion needs an explicit confirmation and password',async()=>{
  const calls=fakeFetch([user]);assert.equal((await handler(request({confirmation:'NO'}))).status,400);assert.equal(calls.length,1);
});
test('wrong password never tombstones or deletes data',async()=>{
  const calls=fakeFetch([user,{status:400}]);assert.equal((await handler(request())).status,403);assert.equal(calls.length,2);
});
test('fresh password identity must match authenticated identity',async()=>{
  const calls=fakeFetch([user,{body:{user:{id:'different-user'},access_token:'test'}}]);assert.equal((await handler(request())).status,403);assert.equal(calls.length,2);
});
test('deletion hides data, revokes sessions, removes physical media, then deletes Auth user',async()=>{
  const calls=fakeFetch([user,verified,{}, {}, {body:[{name:'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg'}]}, {}, {body:[]}, {body:[]}, {}]);
  assert.equal((await handler(request())).status,200);
  assert.ok(calls[2].url.includes('rest/v1/profiles'));assert.ok(calls[3].url.includes('/logout?scope=global'));
  assert.equal(calls[5].init.method,'DELETE');assert.ok(calls[5].url.endsWith('storage/v1/object/spark-media'));
  assert.ok(calls.at(-1).url.endsWith(`auth/v1/admin/users/${uid}`));
  assert.deepEqual(JSON.parse(calls[5].init.body).prefixes,[`${uid}/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg`]);
});
test('storage failure never reports completed account deletion',async()=>{
  const calls=fakeFetch([user,verified,{}, {}, {status:503}]);
  assert.equal((await handler(request())).status,503);assert.ok(calls.every(c=>!c.url.includes('admin/users')));
});
test('unexpected object paths stop deletion for administrator review',async()=>{
  const calls=fakeFetch([user,verified,{}, {}, {body:[{name:'../other-user/file.jpg'}]}]);
  assert.equal((await handler(request())).status,503);assert.ok(calls.every(c=>c.init.method!=='DELETE'));
});
