-- Effective connections are derived, so existing friendships need no backfill.
-- Pending requests imply one-way following; accepted friends imply mutual followers.
-- Following intentionally excludes friends (Spark's requested display rule).
create or replace function sparknew_v1_private.profile_connections(profile_id uuid)
returns table(person_id uuid,kind text)
language sql stable security definer set search_path='' as $$
 with friends as (
  select case when f.sender_id=profile_id then f.receiver_id else f.sender_id end id
  from public.sparknew_friendships f
  where f.status='accepted' and profile_id in(f.sender_id,f.receiver_id)
 ), candidates as (
  select f.follower_id id,'followers'::text kind from public.sparknew_follows f where f.following_id=profile_id
  union select f.sender_id,'followers' from public.sparknew_friendships f where f.receiver_id=profile_id
  union select id,'followers' from friends
  union select f.following_id,'following' from public.sparknew_follows f where f.follower_id=profile_id
  union select f.receiver_id,'following' from public.sparknew_friendships f where f.sender_id=profile_id and f.status='pending'
  union select id,'friends' from friends
 )
 select c.id,c.kind from candidates c
 where auth.uid() is not null and not sparknew_v1_private.blocked(profile_id)
 and not sparknew_v1_private.blocked(c.id)
 and not exists(select 1 from public.sparknew_blocks b where
  (b.owner_id=profile_id and b.target_id=c.id) or (b.owner_id=c.id and b.target_id=profile_id))
 and (c.kind<>'following' or not exists(select 1 from friends f where f.id=c.id));
$$;
revoke all on function sparknew_v1_private.profile_connections(uuid) from public,anon;
grant execute on function sparknew_v1_private.profile_connections(uuid) to authenticated;

create or replace function public.sparknew_profile_counts(profile_id uuid)
returns table(followers bigint,following bigint,friends bigint,posts bigint)
language sql stable security invoker set search_path='' as $$
 select count(*) filter(where c.kind='followers'),count(*) filter(where c.kind='following'),count(*) filter(where c.kind='friends'),
 (select count(*) from public.sparknew_posts p where p.author_id=profile_id and p.kind in('post','reel'))
 from sparknew_v1_private.profile_connections(profile_id) c;
$$;
revoke all on function public.sparknew_profile_counts(uuid) from public,anon;
grant execute on function public.sparknew_profile_counts(uuid) to authenticated;

create or replace function public.sparknew_profile_people(profile_id uuid,connection_kind text,page_size integer default 20,page_offset integer default 0)
returns table(id uuid,display_name text,avatar_path text)
language sql stable security invoker set search_path='' as $$
 select p.id,p.display_name,p.avatar_path from sparknew_v1_private.profile_connections(profile_id) c
 join public.sparknew_profiles p on p.id=c.person_id where c.kind=connection_kind
 order by lower(p.display_name),p.id limit greatest(1,least(coalesce(page_size,20),50)) offset greatest(0,least(coalesce(page_offset,0),10000));
$$;
revoke all on function public.sparknew_profile_people(uuid,text,integer,integer) from public,anon;
grant execute on function public.sparknew_profile_people(uuid,text,integer,integer) to authenticated;
notify pgrst,'reload schema';
