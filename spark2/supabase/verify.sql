-- Read-only checks for the chosen Spark project. Run as a project administrator.
-- This does not upload, delete, modify or repair anything.
select c.relname as table_name, c.relrowsecurity as rls_enabled
from pg_class c join pg_namespace n on n.oid=c.relnamespace
where n.nspname='public' and c.relname in
('profiles','posts','likes','comments','saves','follows','blocks','reports')
order by c.relname;

select schemaname, tablename, policyname, roles, cmd
from pg_policies where schemaname='public' or policyname like 'spark_media_%'
order by schemaname, tablename, policyname;

select relname, reloptions from pg_class
where relname in ('spark_feed','spark_comments');

select id, public, file_size_limit, allowed_mime_types from storage.buckets
where id in ('spark-media','spark-avatars');

-- Expected: zero rows. Database posts should point to actual Storage metadata.
select p.id, p.author_id, p.image_path from public.posts p
left join storage.objects o on o.bucket_id='spark-media' and o.name=p.image_path
where o.id is null;

-- Interrupted uploads may leave unlinked files. Review age/ownership first.
-- Remove approved orphan files via the Storage API, NOT SQL DELETE.
select o.bucket_id, o.name, o.created_at
from storage.objects o
where (o.bucket_id='spark-media' and not exists(select 1 from public.posts p where p.image_path=o.name))
   or (o.bucket_id='spark-avatars' and not exists(select 1 from public.profiles p where p.avatar_path=o.name));

select id, username, deleting_at from public.profiles where deleting_at is not null;

-- Administrator moderation queue. A human must review reports; no auto-punishment.
select id, post_id, reason, created_at from public.reports order by created_at desc limit 100;
