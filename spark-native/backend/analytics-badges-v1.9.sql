-- Content analytics and manually awarded verification badges.
-- Unique viewer per content item per UTC day; no fabricated earnings.
create table public.sparknew_post_views (
 post_id uuid not null references public.sparknew_posts(id) on delete cascade,
 viewer_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 viewed_on date not null default (now() at time zone 'utc')::date,
 primary key (post_id,viewer_id,viewed_on)
);
create index sparknew_post_views_date on public.sparknew_post_views(viewed_on,post_id);
alter table public.sparknew_post_views enable row level security;
create policy post_views_add on public.sparknew_post_views for insert to authenticated
 with check (viewer_id=(select auth.uid()) and viewed_on=(now() at time zone 'utc')::date
  and exists(select 1 from public.sparknew_posts p where p.id=post_id and p.author_id<>viewer_id));
create policy post_views_owner on public.sparknew_post_views for select to authenticated
 using (exists(select 1 from public.sparknew_posts p where p.id=post_id and p.author_id=(select auth.uid())));
grant select,insert on public.sparknew_post_views to authenticated;

create function public.sparknew_record_view(content_id uuid) returns void language sql security invoker set search_path=''
 as $$ insert into public.sparknew_post_views(post_id,viewer_id) values(content_id,auth.uid()) on conflict do nothing; $$;
revoke all on function public.sparknew_record_view(uuid) from public,anon;
grant execute on function public.sparknew_record_view(uuid) to authenticated;

create function public.sparknew_content_analytics(days_back integer default 28) returns jsonb language sql stable security invoker set search_path=''
 as $$
 with window_posts as (
  select p.id,p.kind,p.body,p.created_at from public.sparknew_posts p
  where p.author_id=auth.uid() and p.kind in ('post','reel')
   and p.created_at>=now()-make_interval(days=>least(greatest(days_back,1),28))
 ),
 metrics as (
  select p.id,p.kind,p.body,p.created_at,
   (select count(*) from public.sparknew_post_views v where v.post_id=p.id and v.viewed_on >= (now() at time zone 'utc')::date-least(greatest(days_back,1),28)) views,
   (select count(*) from public.sparknew_reactions r where r.post_id=p.id) reactions,
   (select count(*) from public.sparknew_comments c where c.post_id=p.id) comments
  from window_posts p
 )
 select jsonb_build_object(
  'summary',jsonb_build_object(
   'views',coalesce(sum(views),0),'engagement',coalesce(sum(reactions+comments),0),
   'content_count',count(*),
   'followers',(select count(*) from public.sparknew_follows f where f.following_id=auth.uid())),
  'content',coalesce(jsonb_agg(to_jsonb(metrics) order by created_at desc),'[]'::jsonb))
 from metrics;
 $$;
revoke all on function public.sparknew_content_analytics(integer) from public,anon;
grant execute on function public.sparknew_content_analytics(integer) to authenticated;

create table public.sparknew_badge_admins (
 user_id uuid primary key references public.sparknew_profiles(id) on delete cascade
);
alter table public.sparknew_badge_admins enable row level security;
create policy badge_admin_self on public.sparknew_badge_admins for select to authenticated
 using(user_id=(select auth.uid()));
grant select on public.sparknew_badge_admins to authenticated;
-- An operator must explicitly assign the account by confirmed auth user ID
-- in a separate reviewed operation. Display names are user-editable.

create table public.sparknew_badges (
 profile_id uuid primary key references public.sparknew_profiles(id) on delete cascade,
 granted_by uuid not null references public.sparknew_badge_admins(user_id),
 granted_at timestamptz not null default now()
);
alter table public.sparknew_badges enable row level security;
create policy badge_read on public.sparknew_badges for select to authenticated using(true);
create policy badge_award on public.sparknew_badges for insert to authenticated
 with check(granted_by=(select auth.uid()) and exists(select 1 from public.sparknew_badge_admins a where a.user_id=(select auth.uid())));
create policy badge_revoke on public.sparknew_badges for delete to authenticated
 using(exists(select 1 from public.sparknew_badge_admins a where a.user_id=(select auth.uid())));
grant select,insert,delete on public.sparknew_badges to authenticated;

create table public.sparknew_badge_requests(
 id uuid primary key default gen_random_uuid(),
 requester_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 reason text not null check(length(trim(reason)) between 1 and 500),
 status text not null default 'pending' check(status in ('pending','approved','rejected')),
 created_at timestamptz not null default now()
);
create unique index sparknew_badge_pending on public.sparknew_badge_requests(requester_id) where status='pending';
alter table public.sparknew_badge_requests enable row level security;
create policy badge_request_read on public.sparknew_badge_requests for select to authenticated
 using(requester_id=(select auth.uid()) or exists(select 1 from public.sparknew_badge_admins a where a.user_id=(select auth.uid())));
create policy badge_request_create on public.sparknew_badge_requests for insert to authenticated
 with check(requester_id=(select auth.uid()) and status='pending');
create policy badge_request_review on public.sparknew_badge_requests for update to authenticated
 using(status='pending' and exists(select 1 from public.sparknew_badge_admins a where a.user_id=(select auth.uid())))
 with check(status in ('approved','rejected') and exists(select 1 from public.sparknew_badge_admins a where a.user_id=(select auth.uid())));
grant select,insert,update(status) on public.sparknew_badge_requests to authenticated;
