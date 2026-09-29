-- Feed uses only accepted friends and the viewer's own posts. RLS still applies.
create function public.sparknew_ranked_feed(page_offset integer default 0,page_size integer default 20)
 returns setof public.sparknew_posts language sql stable security invoker set search_path='' as $$
 select p.* from public.sparknew_posts p
 where p.kind='post' and p.community_id is null
  and (p.author_id=auth.uid() or exists (
   select 1 from public.sparknew_friendships f
   where f.status='accepted' and
    ((f.sender_id=auth.uid() and f.receiver_id=p.author_id)
     or (f.receiver_id=auth.uid() and f.sender_id=p.author_id))))
 order by (
  100.0/(1.0+greatest(extract(epoch from (now()-p.created_at))/86400.0,0))
  + 3*(select count(*) from public.sparknew_comments c where c.post_id=p.id)
  + 2*(select count(*) from public.sparknew_reactions r where r.post_id=p.id)
 ) desc,p.created_at desc,p.id desc
 limit least(greatest(page_size,1),40) offset greatest(page_offset,0);
 $$;
revoke all on function public.sparknew_ranked_feed(integer,integer) from public,anon;
grant execute on function public.sparknew_ranked_feed(integer,integer) to authenticated;

create function public.sparknew_unread_friend_posts(since_time timestamptz)
 returns bigint language sql stable security invoker set search_path='' as $$
 select count(*) from public.sparknew_posts p
 where p.kind='post' and p.community_id is null
  and p.created_at>greatest(since_time,now()-interval '30 days')
  and exists(select 1 from public.sparknew_friendships f
   where f.status='accepted' and
    ((f.sender_id=auth.uid() and f.receiver_id=p.author_id)
     or (f.receiver_id=auth.uid() and f.sender_id=p.author_id)));
 $$;
revoke all on function public.sparknew_unread_friend_posts(timestamptz) from public,anon;
grant execute on function public.sparknew_unread_friend_posts(timestamptz) to authenticated;

-- Replies by the owner to a specific comment close that community item.
create function public.sparknew_pending_comments()
 returns setof public.sparknew_comments language sql stable security invoker set search_path='' as $$
 select c.* from public.sparknew_comments c
 join public.sparknew_posts p on p.id=c.post_id
 where p.author_id=auth.uid() and c.author_id<>auth.uid()
  and not exists(select 1 from public.sparknew_comments reply
   where reply.parent_id=c.id and reply.author_id=auth.uid())
 order by c.created_at desc,c.id desc limit 100;
 $$;
revoke all on function public.sparknew_pending_comments() from public,anon;
grant execute on function public.sparknew_pending_comments() to authenticated;

create or replace function public.sparknew_content_analytics(days_back integer default 28)
 returns jsonb language sql stable security invoker set search_path='' as $$
 with owned as (
  select p.id,p.kind,p.body,p.media_path,p.media_type,p.created_at
  from public.sparknew_posts p where p.author_id=auth.uid() and p.kind in ('post','reel')
 ), metrics as (
  select p.*,
   (select count(*) from public.sparknew_post_views v where v.post_id=p.id
    and v.viewed_on >= (now() at time zone 'utc')::date-least(greatest(days_back,1),28)) views,
   (select count(*) from public.sparknew_reactions r where r.post_id=p.id) reactions,
   (select count(*) from public.sparknew_comments c where c.post_id=p.id) comments
  from owned p
 )
 select jsonb_build_object(
  'summary',jsonb_build_object(
   'views',coalesce(sum(views),0),
   'engagement',coalesce(sum(reactions+comments) filter
    (where created_at>=now()-make_interval(days=>least(greatest(days_back,1),28))),0),
   'content_count',count(*) filter
    (where created_at>=now()-make_interval(days=>least(greatest(days_back,1),28))),
   'followers',(select count(*) from public.sparknew_follows f where f.following_id=auth.uid())),
  'content',coalesce(jsonb_agg(to_jsonb(metrics) order by created_at desc),'[]'::jsonb))
 from metrics;
 $$;
notify pgrst,'reload schema';
