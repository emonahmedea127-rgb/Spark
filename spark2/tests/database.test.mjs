import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { PGlite } from '@electric-sql/pglite';

// Real PostgreSQL RLS, constraints, grants, functions and triggers in PGlite.
// Only the external Auth/Storage metadata schemas are stubbed. This is NOT a
// hosted Supabase or actual object-upload integration test.
test('Spark database authorization and data integrity', async (t) => {
  const db = new PGlite();
  const A='11111111-1111-4111-8111-111111111111', B='22222222-2222-4222-8222-222222222222';
  const C='33333333-3333-4333-8333-333333333333', D='44444444-4444-4444-8444-444444444444';
  const E='55555555-5555-4555-8555-555555555555';
  const P='aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', Q='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
  const orphan='cccccccc-cccc-4ccc-8ccc-cccccccccccc';
  const act = async (id) => {
    await db.exec('reset role');
    await db.query("select set_config('request.jwt.claim.sub', $1, false)", [id ?? '']);
    await db.exec(id === null ? 'set role anon' : 'set role authenticated');
  };
  const admin = async () => { await db.exec('reset role'); };
  const count = async (sql) => Number((await db.query(sql)).rows[0].n);
  const deny = async (sql) => { await assert.rejects(db.exec(sql)); };
  const run = (name, fn) => t.test(name, fn);
  await db.exec(`
    create role anon; create role authenticated; create role service_role bypassrls;
    create schema auth; create schema storage;
    create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    grant usage on schema auth, storage, public to anon, authenticated, service_role;
    create table storage.buckets(id text primary key, name text, public boolean, file_size_limit bigint, allowed_mime_types text[]);
    create table storage.objects(id uuid primary key default gen_random_uuid(), bucket_id text references storage.buckets, name text, unique(bucket_id,name));
    alter table storage.objects enable row level security;
    grant select,insert,update,delete on storage.objects to anon,authenticated;
  `);
  await db.exec(await readFile(new URL('../supabase/setup.sql', import.meta.url), 'utf8'));
  await db.exec(`insert into auth.users values('${A}'),('${B}'),('${C}'),('${D}'),('${E}');`);
  for (const [id, username] of [[A,'alice'],[B,'blake'],[C,'casey'],[D,'drew'],[E,'erin']]) {
    await act(id);
    await db.query('insert into public.profiles(id,username) values($1,$2)', [id,username]);
  }
  await act(C); await db.exec(`update public.profiles set is_private=false where id='${C}'`);
  const makePost = async (user, id) => {
    await act(user);
    await db.exec(`insert into storage.objects(bucket_id,name) values('spark-media','${user}/${id}.jpg');
      insert into public.posts(id,author_id,image_path,caption) values('${id}','${user}','${user}/${id}.jpg','A quiet moment');`);
  };
  await makePost(A,P); await makePost(C,Q);

  await run('new accounts are private by default', async () => { await act(A); assert.equal((await db.query(`select is_private from profiles where id='${A}'`)).rows[0].is_private,true); });
  await run('owners read their own posts and private media', async () => { await act(A); assert.equal(await count(`select count(*) n from posts where id='${P}'`),1); assert.equal(await count(`select count(*) n from storage.objects where name='${A}/${P}.jpg'`),1); });
  await run('strangers cannot read private posts, views or bytes', async () => { await act(B); for (const table of ['posts','spark_feed']) assert.equal(await count(`select count(*) n from ${table} where id='${P}'`),0); assert.equal(await count(`select count(*) n from storage.objects where name='${A}/${P}.jpg'`),0); });
  await run('public posts are readable by signed-in users', async () => { assert.equal(await count(`select count(*) n from spark_feed where id='${Q}'`),1); });
  await run('guests cannot access application tables', async () => { await act(null); await deny('select * from public.posts'); await deny('select * from public.profiles'); assert.equal(await count('select count(*) n from storage.objects'),0); });
  await run('clients cannot self-approve private follows on insert', async () => { await act(B); await deny(`insert into follows(follower_id,following_id,status) values('${B}','${A}','accepted')`); });
  await run('private follow is pending until target approves', async () => { await act(B); await db.exec(`insert into follows(follower_id,following_id) values('${B}','${A}')`); assert.equal((await db.query(`select status from follows where following_id='${A}'`)).rows[0].status,'pending'); assert.equal(await count(`select count(*) n from posts where id='${P}'`),0); });
  await run('requester cannot approve their own request', async () => { const rows = await db.query(`update follows set status='accepted' where following_id='${A}' returning *`); assert.equal(rows.rows.length,0); });
  await run('follow endpoints cannot be reassigned', async () => { await deny(`update follows set following_id='${C}' where following_id='${A}'`); });
  await run('target approval unlocks post and media access', async () => { await act(A); await db.exec(`update follows set status='accepted' where follower_id='${B}'`); await act(B); assert.equal(await count(`select count(*) n from spark_feed where id='${P}'`),1); assert.equal(await count(`select count(*) n from storage.objects where name='${A}/${P}.jpg'`),1); });
  await run('public follow is accepted by the server', async () => { await db.exec(`insert into follows(follower_id,following_id) values('${B}','${C}')`); assert.equal((await db.query(`select status from follows where following_id='${C}'`)).rows[0].status,'accepted'); });
  await run('likes are unique and feed totals match', async () => { await db.exec(`insert into likes(user_id,post_id) values('${B}','${Q}')`); await deny(`insert into likes(user_id,post_id) values('${B}','${Q}')`); const row=(await db.query(`select * from spark_feed where id='${Q}'`)).rows[0]; assert.equal(row.like_count,1); assert.equal(row.liked,true); });
  await run('users cannot spoof the author of a like', async () => { await deny(`insert into likes(user_id,post_id) values('${A}','${Q}')`); });
  await run('saved posts remain private to the saver', async () => { await db.exec(`insert into saves(user_id,post_id) values('${B}','${Q}')`); await act(C); assert.equal(await count('select count(*) n from saves'),0); assert.equal((await db.query(`select saved from spark_feed where id='${Q}'`)).rows[0].saved,false); });
  await run('comments reject empty text and impersonation', async () => { await act(B); await deny(`insert into comments(post_id,author_id,body) values('${Q}','${B}','  ')`); await deny(`insert into comments(post_id,author_id,body) values('${Q}','${A}','spoof')`); });
  await run('post owners can remove an unwanted comment', async () => { await db.exec(`insert into comments(post_id,author_id,body) values('${Q}','${B}','Beautiful light')`); await act(D); assert.equal((await db.query(`delete from comments where post_id='${Q}' returning *`)).rows.length,0); await act(C); assert.equal((await db.query(`delete from comments where post_id='${Q}' returning *`)).rows.length,1); });
  await run('reports are visible only to the reporter and administrators', async () => { await act(B); await db.exec(`insert into reports(reporter_id,post_id,reason) values('${B}','${Q}','spam')`); await act(C); assert.equal(await count('select count(*) n from reports'),0); });
  await run('storage uploads must use the caller folder', async () => { await act(B); await deny(`insert into storage.objects(bucket_id,name) values('spark-media','${A}/${orphan}.jpg')`); });
  await run('post metadata cannot reference a missing upload', async () => { await deny(`insert into posts(id,author_id,image_path) values('${orphan}','${B}','${B}/${orphan}.jpg')`); });
  await run('unattached uploads are never readable by others', async () => { await act(C); await db.exec(`insert into storage.objects(bucket_id,name) values('spark-media','${C}/${orphan}.jpg')`); await act(B); assert.equal(await count(`select count(*) n from storage.objects where name='${C}/${orphan}.jpg'`),0); });
  await run('published media cannot be overwritten or deleted first', async () => { await act(C); assert.equal((await db.query(`update storage.objects set name='replaced.jpg' where name='${C}/${Q}.jpg' returning *`)).rows.length,0); assert.equal((await db.query(`delete from storage.objects where name='${C}/${Q}.jpg' returning *`)).rows.length,0); });
  await run('users cannot alter ownership, timestamps or deletion flags', async () => { await act(B); await deny(`update profiles set id='${D}' where id='${B}'`); await deny(`update profiles set deleting_at=now() where id='${B}'`); await deny(`update posts set created_at=now() where id='${Q}'`); });
  await run('private avatars require an existing owner-controlled object', async () => { await deny(`update profiles set avatar_path='${B}/${orphan}.jpg' where id='${B}'`); });
  await run('blocking removes follow edges and hides both directions', async () => { await act(A); await db.exec(`insert into blocks(blocker_id,blocked_id) values('${A}','${B}')`); assert.equal(await count(`select count(*) n from profiles where id='${B}'`),0); await act(B); assert.equal(await count(`select count(*) n from profiles where id='${A}'`),0); assert.equal(await count(`select count(*) n from follows where following_id='${A}'`),0); assert.equal(await count(`select count(*) n from storage.objects where name='${A}/${P}.jpg'`),0); });
  await run('blocked users cannot refollow or like private content', async () => { await deny(`insert into follows(follower_id,following_id) values('${B}','${A}')`); await deny(`insert into likes(user_id,post_id) values('${B}','${P}')`); });
  await run('unblock does not restore an old approval', async () => { await act(A); await db.exec(`delete from blocks where blocked_id='${B}'`); await act(B); assert.equal(await count(`select count(*) n from posts where id='${P}'`),0); });
  await run('deleting a post cascades reactions and unlocks cleanup', async () => { await act(C); await db.exec(`delete from posts where id='${Q}'`); assert.equal((await db.query(`delete from storage.objects where name='${C}/${Q}.jpg' returning *`)).rows.length,1); await admin(); assert.equal(await count(`select count(*) n from likes where post_id='${Q}'`),0); });
  await run('server-side per-caller posting limit is enforced', async () => { for(let i=1;i<=5;i++) await makePost(E,`eeeeeeee-eeee-4eee-8eee-${String(i).padStart(12,'0')}`); await assert.rejects(makePost(E,'eeeeeeee-eeee-4eee-8eee-000000000006'),/wait before posting/); });
  await run('deletion tombstone invalidates data access for existing JWT identities', async () => { await admin(); await db.exec(`update profiles set deleting_at=now() where id='${E}'`); await act(E); assert.equal(await count('select count(*) n from posts'),0); await deny(`insert into storage.objects(bucket_id,name) values('spark-media','${E}/${orphan}.jpg')`); });
  await run('all eight application tables have RLS enabled', async () => { await admin(); assert.equal(await count("select count(*) n from pg_class c join pg_namespace n on n.oid=c.relnamespace where n.nspname='public' and c.relkind='r' and c.relrowsecurity"),8); });
  await run('both views execute as the caller, not the owner', async () => { const {rows}=await db.query("select reloptions from pg_class where relname in ('spark_feed','spark_comments')"); assert.equal(rows.length,2); rows.forEach(row=>assert.ok(row.reloptions.includes('security_invoker=true'))); });
  await run('no privileged functions are exposed in the public schema', async () => { assert.equal(await count("select count(*) n from pg_proc p join pg_namespace n on n.oid=p.pronamespace where n.nspname='public' and p.prosecdef"),0); });
  await run('both buckets are private and restrict size and media type', async () => { const {rows}=await db.query('select * from storage.buckets'); assert.equal(rows.length,2); rows.forEach(row=>{assert.equal(row.public,false);assert.equal(Number(row.file_size_limit),8388608);assert.deepEqual(row.allowed_mime_types,['image/jpeg']);}); });
  await run('anonymous clients cannot call private security helpers', async () => { await act(null); await deny(`select spark_private.can_view_author('${A}')`); });
  await db.close();
});
