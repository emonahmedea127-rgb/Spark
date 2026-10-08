-- The Home rail contains only the signed-in viewer's own and accepted friends' reels.
create function public.sparknew_friend_reels()
 returns setof public.sparknew_posts language sql stable security invoker set search_path='' as $$
 select p.* from public.sparknew_posts p
 where p.kind='reel' and p.community_id is null and
  (p.author_id=auth.uid() or exists(select 1 from public.sparknew_friendships f
   where f.status='accepted' and
    ((f.sender_id=auth.uid() and f.receiver_id=p.author_id) or
     (f.receiver_id=auth.uid() and f.sender_id=p.author_id))))
 order by p.created_at desc,p.id desc limit 12;
 $$;
revoke all on function public.sparknew_friend_reels() from public,anon;
grant execute on function public.sparknew_friend_reels() to authenticated;

-- One row per playback session, recorded after actual foreground video playback.
create table public.sparknew_reel_playbacks (
 id uuid primary key default gen_random_uuid(),
 post_id uuid not null references public.sparknew_posts(id) on delete cascade,
 viewer_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 watched_ms integer not null check(watched_ms between 1000 and 14400000),
 media_duration_ms integer not null check(media_duration_ms between 1000 and 14400000),
 created_at timestamptz not null default now()
);
create index sparknew_reel_playbacks_post_date on public.sparknew_reel_playbacks(post_id,created_at desc);
alter table public.sparknew_reel_playbacks enable row level security;
create policy reel_playback_add on public.sparknew_reel_playbacks for insert to authenticated
 with check(viewer_id=(select auth.uid()) and exists(
  select 1 from public.sparknew_posts p where p.id=post_id and p.kind='reel' and p.media_type='video'
   and p.author_id<>(select auth.uid())));
create policy reel_playback_owner on public.sparknew_reel_playbacks for select to authenticated
 using(exists(select 1 from public.sparknew_posts p where p.id=post_id and p.author_id=(select auth.uid())));
grant select,insert on public.sparknew_reel_playbacks to authenticated;

create function public.sparknew_record_reel_playback(content_id uuid,played_ms integer,media_ms integer)
 returns void language sql security invoker set search_path='' as $$
 insert into public.sparknew_reel_playbacks(post_id,viewer_id,watched_ms,media_duration_ms)
 select content_id,auth.uid(),played_ms,media_ms
 where played_ms between 1000 and 14400000 and media_ms between 1000 and 14400000;
 $$;
revoke all on function public.sparknew_record_reel_playback(uuid,integer,integer) from public,anon;
grant execute on function public.sparknew_record_reel_playback(uuid,integer,integer) to authenticated;

-- Discoverable public reels compete on engagement and recency.
-- Friends' non-public reels remain visible to their accepted friends under posts RLS.
create function public.sparknew_ranked_reels(page_offset integer default 0,page_size integer default 20)
 returns setof public.sparknew_posts language sql stable security invoker set search_path='' as $$
 select p.* from public.sparknew_posts p
 where p.kind='reel' and p.media_type='video' and p.community_id is null
  and (p.visibility='public' or p.author_id=auth.uid() or exists(
   select 1 from public.sparknew_friendships f where f.status='accepted' and
    ((f.sender_id=auth.uid() and f.receiver_id=p.author_id) or
     (f.receiver_id=auth.uid() and f.sender_id=p.author_id))))
 order by (
  20.0/(1.0+greatest(extract(epoch from(now()-p.created_at))/86400.0,0)/7)
  + 3*(select count(*) from public.sparknew_reactions r where r.post_id=p.id)
  + 5*(select count(*) from public.sparknew_comments c where c.post_id=p.id)
 ) desc,p.created_at desc,p.id desc
 limit least(greatest(page_size,1),40) offset greatest(page_offset,0);
 $$;
revoke all on function public.sparknew_ranked_reels(integer,integer) from public,anon;
grant execute on function public.sparknew_ranked_reels(integer,integer) to authenticated;

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
   (select count(*) from public.sparknew_comments c where c.post_id=p.id) comments,
   coalesce(play.plays,0) plays,
   coalesce(play.unique_viewers,0) unique_viewers,
   coalesce(play.total_watch_seconds,0) total_watch_seconds,
   coalesce(play.avg_view_seconds,0) avg_view_seconds,
   coalesce(play.completion_rate,0) completion_rate,
   coalesce(play.avg_video_seconds,0) avg_video_seconds
  from owned p left join lateral (
   select count(*) plays,count(distinct x.viewer_id) unique_viewers,
    round(sum(x.watched_ms)/1000.0,1) total_watch_seconds,
    round(avg(x.watched_ms)/1000.0,1) avg_view_seconds,
    round(100.0*count(*) filter(where x.watched_ms>=x.media_duration_ms*.9)/nullif(count(*),0),1) completion_rate,
    round(avg(x.media_duration_ms)/1000.0,1) avg_video_seconds
   from public.sparknew_reel_playbacks x
   where x.post_id=p.id and x.created_at>=now()-make_interval(days=>least(greatest(days_back,1),28))
  ) play on p.kind='reel'
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
