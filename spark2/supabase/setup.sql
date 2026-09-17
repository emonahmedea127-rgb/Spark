-- Spark 0.1.0. Run ONCE in a NEW, EMPTY Supabase project (PostgreSQL 15+).
-- This transaction intentionally fails on existing names instead of replacing data.
begin;
create schema spark_private;
revoke all on schema spark_private from public, anon;
grant usage on schema spark_private to authenticated, service_role;

create table public.profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username text not null unique check (username ~ '^[a-z0-9_]{3,24}$'),
  display_name text not null default 'New spark' check (char_length(btrim(display_name)) between 1 and 60),
  bio text not null default '' check (char_length(bio) <= 160),
  avatar_path text,
  is_private boolean not null default true,
  deleting_at timestamptz,
  created_at timestamptz not null default now(),
  check (avatar_path is null or avatar_path ~ ('^' || id::text || '/[a-f0-9-]{36}\.jpg$'))
);
create table public.follows (
  follower_id uuid not null references public.profiles(id) on delete cascade,
  following_id uuid not null references public.profiles(id) on delete cascade,
  status text not null default 'pending' check (status in ('pending','accepted')),
  created_at timestamptz not null default now(),
  primary key (follower_id, following_id), check (follower_id <> following_id)
);
create index follows_target_status_idx on public.follows(following_id, status, follower_id);
create table public.posts (
  id uuid primary key default gen_random_uuid(),
  author_id uuid not null references public.profiles(id) on delete cascade,
  image_path text not null unique,
  caption text not null default '' check (char_length(caption) <= 2200),
  created_at timestamptz not null default now(),
  check (image_path = author_id::text || '/' || id::text || '.jpg')
);
create index posts_feed_idx on public.posts(created_at desc, id desc);
create index posts_author_feed_idx on public.posts(author_id, created_at desc, id desc);
create table public.likes (
  user_id uuid not null references public.profiles(id) on delete cascade,
  post_id uuid not null references public.posts(id) on delete cascade,
  created_at timestamptz not null default now(), primary key(user_id, post_id)
);
create index likes_post_idx on public.likes(post_id);
create table public.saves (
  user_id uuid not null references public.profiles(id) on delete cascade,
  post_id uuid not null references public.posts(id) on delete cascade,
  created_at timestamptz not null default now(), primary key(user_id, post_id)
);
create index saves_post_idx on public.saves(post_id);
create table public.comments (
  id uuid primary key default gen_random_uuid(),
  post_id uuid not null references public.posts(id) on delete cascade,
  author_id uuid not null references public.profiles(id) on delete cascade,
  body text not null check (char_length(btrim(body)) between 1 and 1000),
  created_at timestamptz not null default now()
);
create index comments_post_page_idx on public.comments(post_id, created_at desc, id desc);
create index comments_author_time_idx on public.comments(author_id, created_at desc);
create table public.blocks (
  blocker_id uuid not null references public.profiles(id) on delete cascade,
  blocked_id uuid not null references public.profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key(blocker_id, blocked_id), check (blocker_id <> blocked_id)
);
create index blocks_target_idx on public.blocks(blocked_id, blocker_id);
create table public.reports (
  id uuid primary key default gen_random_uuid(),
  reporter_id uuid not null references public.profiles(id) on delete cascade,
  post_id uuid references public.posts(id) on delete set null,
  reason text not null check (reason in ('spam','harassment','unsafe_content','other')),
  created_at timestamptz not null default now(),
  unique(reporter_id, post_id)
);
create index reports_post_idx on public.reports(post_id);
create index reports_reporter_time_idx on public.reports(reporter_id, created_at desc);
create index follows_requester_time_idx on public.follows(follower_id, created_at desc);

-- These tightly scoped lookup helpers must see incoming blocks and follow rows
-- hidden by their own RLS. The viewer is ALWAYS auth.uid(), never client supplied.
-- Keep spark_private OUT of the Data API exposed-schemas setting.
create function spark_private.active_user() returns boolean
language sql stable security definer set search_path = '' as $$
  select (select auth.uid()) is not null and exists (
    select 1 from public.profiles where id = (select auth.uid()) and deleting_at is null
  );
$$;
create function spark_private.blocked_with(target uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select (select auth.uid()) is null or exists (
    select 1 from public.blocks
    where (blocker_id = (select auth.uid()) and blocked_id = target)
       or (blocked_id = (select auth.uid()) and blocker_id = target)
  );
$$;
create function spark_private.can_discover(target uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select (select auth.uid()) is not null and spark_private.active_user()
    and not spark_private.blocked_with(target)
    and exists (select 1 from public.profiles where id = target and deleting_at is null);
$$;
create function spark_private.can_view_author(target uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select (select auth.uid()) is not null and spark_private.can_discover(target) and exists (
    select 1 from public.profiles p where p.id = target and (
      p.id = (select auth.uid()) or not p.is_private or exists (
        select 1 from public.follows f where f.follower_id = (select auth.uid())
          and f.following_id = p.id and f.status = 'accepted'
      )
    )
  );
$$;
create function spark_private.can_view_post(target uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select (select auth.uid()) is not null and exists (
    select 1 from public.posts p where p.id = target and spark_private.can_view_author(p.author_id)
  );
$$;
create function spark_private.can_read_media(bucket text, object_name text) returns boolean
language sql stable security definer set search_path = '' as $$
  select (select auth.uid()) is not null and spark_private.active_user() and (
    split_part(object_name, '/', 1) = (select auth.uid())::text or
    (bucket = 'spark-media' and exists (
      select 1 from public.posts p where p.image_path = object_name and spark_private.can_view_author(p.author_id)
    )) or
    (bucket = 'spark-avatars' and exists (
      select 1 from public.profiles p where p.avatar_path = object_name and spark_private.can_discover(p.id)
    ))
  );
$$;
create function spark_private.media_unlinked(bucket text, object_name text) returns boolean
language sql stable security definer set search_path = '' as $$
  select (select auth.uid()) is not null and split_part(object_name, '/', 1) = (select auth.uid())::text
    and not exists(select 1 from public.posts where bucket = 'spark-media' and image_path = object_name)
    and not exists(select 1 from public.profiles where bucket = 'spark-avatars' and avatar_path = object_name);
$$;

-- Only owners create follow edges. A malicious client cannot self-approve a
-- private follow by inserting status='accepted'. Approval is a separate target-only UPDATE.
create function spark_private.prepare_follow() returns trigger
language plpgsql security invoker set search_path = '' as $$
begin
  if (select auth.uid()) is null or new.follower_id <> (select auth.uid()) then
    raise exception 'Not your follow request' using errcode = '42501';
  end if;
  select case when is_private then 'pending' else 'accepted' end into new.status
    from public.profiles where id = new.following_id;
  return new;
end;
$$;
create trigger follows_prepare before insert on public.follows for each row execute function spark_private.prepare_follow();

-- Serialize caller writes to enforce bounded anti-spam limits, including races.
create function spark_private.write_limit() returns trigger
language plpgsql security definer set search_path = '' as $$
declare caller uuid := (select auth.uid()); recent bigint;
begin
  if caller is null or not spark_private.active_user() then
    raise exception 'Sign in required' using errcode = '42501';
  end if;
  perform 1 from public.profiles where id = caller for update;
  if tg_table_name = 'posts' then
    select count(*) into recent from public.posts where author_id = caller and created_at > now() - interval '1 minute';
    if recent >= 5 then raise exception 'Please wait before posting again'; end if;
  elsif tg_table_name = 'comments' then
    select count(*) into recent from public.comments where author_id = caller and created_at > now() - interval '1 minute';
    if recent >= 30 then raise exception 'Please wait before commenting again'; end if;
  elsif tg_table_name = 'follows' then
    select count(*) into recent from public.follows where follower_id = caller and created_at > now() - interval '1 minute';
    if recent >= 60 then raise exception 'Please wait before following more people'; end if;
  elsif tg_table_name = 'reports' then
    select count(*) into recent from public.reports where reporter_id = caller and created_at > now() - interval '1 day';
    if recent >= 10 then raise exception 'Daily report limit reached'; end if;
  end if;
  return new;
end;
$$;
create trigger posts_limit before insert on public.posts for each row execute function spark_private.write_limit();
create trigger comments_limit before insert on public.comments for each row execute function spark_private.write_limit();
create trigger follows_limit before insert on public.follows for each row execute function spark_private.write_limit();
create trigger reports_limit before insert on public.reports for each row execute function spark_private.write_limit();

create function spark_private.block_cleanup() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
  if (select auth.uid()) is null or new.blocker_id <> (select auth.uid()) then
    raise exception 'Not your block' using errcode = '42501';
  end if;
  delete from public.follows where (follower_id = new.blocker_id and following_id = new.blocked_id)
    or (follower_id = new.blocked_id and following_id = new.blocker_id);
  return new;
end;
$$;
create trigger blocks_cleanup after insert on public.blocks for each row execute function spark_private.block_cleanup();

alter table public.profiles enable row level security;
alter table public.follows enable row level security;
alter table public.posts enable row level security;
alter table public.likes enable row level security;
alter table public.saves enable row level security;
alter table public.comments enable row level security;
alter table public.blocks enable row level security;
alter table public.reports enable row level security;

create policy profile_read on public.profiles for select to authenticated
  using (id = (select auth.uid()) or spark_private.can_discover(id));
create policy profile_create on public.profiles for insert to authenticated with check (id = (select auth.uid()));
create policy profile_edit on public.profiles for update to authenticated
  using (id = (select auth.uid()) and (select spark_private.active_user()))
  with check (id = (select auth.uid()) and deleting_at is null and (
    avatar_path is null or exists (select 1 from storage.objects where bucket_id = 'spark-avatars' and name = avatar_path)
  ));
create policy follows_read on public.follows for select to authenticated
  using ((select spark_private.active_user()) and (follower_id = (select auth.uid()) or following_id = (select auth.uid())));
create policy follows_create on public.follows for insert to authenticated
  with check (follower_id = (select auth.uid()) and spark_private.can_discover(following_id));
create policy follows_approve on public.follows for update to authenticated
  using (following_id = (select auth.uid()) and spark_private.can_discover(follower_id))
  with check (following_id = (select auth.uid()) and status = 'accepted' and spark_private.can_discover(follower_id));
create policy follows_remove on public.follows for delete to authenticated
  using ((select spark_private.active_user()) and (follower_id = (select auth.uid()) or following_id = (select auth.uid())));
create policy posts_read on public.posts for select to authenticated using (spark_private.can_view_author(author_id));
create policy posts_create on public.posts for insert to authenticated with check (
  author_id = (select auth.uid()) and (select spark_private.active_user()) and
  exists(select 1 from storage.objects where bucket_id = 'spark-media' and name = image_path)
);
create policy posts_remove on public.posts for delete to authenticated
  using (author_id = (select auth.uid()) and (select spark_private.active_user()));
create policy likes_read on public.likes for select to authenticated
  using (spark_private.can_view_post(post_id) and not spark_private.blocked_with(user_id));
create policy likes_create on public.likes for insert to authenticated
  with check (user_id = (select auth.uid()) and spark_private.can_view_post(post_id));
create policy likes_remove on public.likes for delete to authenticated
  using (user_id = (select auth.uid()) and (select spark_private.active_user()));
create policy saves_read on public.saves for select to authenticated
  using (user_id = (select auth.uid()) and spark_private.can_view_post(post_id));
create policy saves_create on public.saves for insert to authenticated
  with check (user_id = (select auth.uid()) and spark_private.can_view_post(post_id));
create policy saves_remove on public.saves for delete to authenticated
  using (user_id = (select auth.uid()) and (select spark_private.active_user()));
create policy comments_read on public.comments for select to authenticated
  using (spark_private.can_view_post(post_id) and not spark_private.blocked_with(author_id));
create policy comments_create on public.comments for insert to authenticated
  with check (author_id = (select auth.uid()) and spark_private.can_view_post(post_id));
create policy comments_remove on public.comments for delete to authenticated
  using ((select spark_private.active_user()) and (author_id = (select auth.uid()) or exists(
    select 1 from public.posts where id = post_id and author_id = (select auth.uid())
  )));
create policy blocks_read on public.blocks for select to authenticated
  using (blocker_id = (select auth.uid()) and (select spark_private.active_user()));
create policy blocks_create on public.blocks for insert to authenticated
  with check (blocker_id = (select auth.uid()) and (select spark_private.active_user()));
create policy blocks_remove on public.blocks for delete to authenticated
  using (blocker_id = (select auth.uid()) and (select spark_private.active_user()));
create policy reports_read on public.reports for select to authenticated
  using (reporter_id = (select auth.uid()) and (select spark_private.active_user()));
create policy reports_create on public.reports for insert to authenticated
  with check (reporter_id = (select auth.uid()) and spark_private.can_view_post(post_id));

-- Explicitly opt in only the needed operations. Column grants protect ownership,
-- timestamps, moderation/deletion flags and follow endpoints from reassignment.
revoke all on public.profiles, public.follows, public.posts, public.likes, public.saves,
  public.comments, public.blocks, public.reports from public, anon, authenticated;
grant select on public.profiles, public.follows, public.posts, public.likes, public.saves,
  public.comments, public.blocks, public.reports to authenticated;
grant insert(id, username, display_name) on public.profiles to authenticated;
grant update(username, display_name, bio, avatar_path, is_private) on public.profiles to authenticated;
grant insert(follower_id, following_id) on public.follows to authenticated;
grant update(status) on public.follows to authenticated;
grant insert(id, author_id, image_path, caption) on public.posts to authenticated;
grant insert(user_id, post_id) on public.likes, public.saves to authenticated;
grant insert(post_id, author_id, body) on public.comments to authenticated;
grant insert(blocker_id, blocked_id) on public.blocks to authenticated;
grant insert(reporter_id, post_id, reason) on public.reports to authenticated;
grant delete on public.follows, public.posts, public.likes, public.saves, public.comments, public.blocks to authenticated;
grant all on public.profiles, public.follows, public.posts, public.likes, public.saves,
  public.comments, public.blocks, public.reports to service_role;

revoke execute on all functions in schema spark_private from public, anon, authenticated;
grant execute on function spark_private.active_user(), spark_private.blocked_with(uuid),
  spark_private.can_discover(uuid), spark_private.can_view_author(uuid), spark_private.can_view_post(uuid),
  spark_private.can_read_media(text,text), spark_private.media_unlinked(text,text) to authenticated;

-- Both views retain the caller's RLS. Never remove security_invoker.
create view public.spark_feed with (security_invoker = true) as
select p.id, p.author_id, p.image_path, p.caption, p.created_at,
  u.username, u.display_name, u.avatar_path,
  (select count(*)::int from public.likes l where l.post_id = p.id) as like_count,
  (select count(*)::int from public.comments c where c.post_id = p.id) as comment_count,
  exists(select 1 from public.likes l where l.post_id = p.id and l.user_id = (select auth.uid())) as liked,
  exists(select 1 from public.saves s where s.post_id = p.id and s.user_id = (select auth.uid())) as saved
from public.posts p join public.profiles u on u.id = p.author_id;
create view public.spark_comments with (security_invoker = true) as
select c.*, u.username, u.display_name, u.avatar_path
from public.comments c join public.profiles u on u.id = c.author_id;
revoke all on public.spark_feed, public.spark_comments from public, anon;
grant select on public.spark_feed, public.spark_comments to authenticated, service_role;

insert into storage.buckets(id, name, public, file_size_limit, allowed_mime_types) values
  ('spark-media','spark-media',false,8388608,array['image/jpeg']),
  ('spark-avatars','spark-avatars',false,8388608,array['image/jpeg']);
-- Supabase owns storage schema RLS and API grants. Add only Spark-scoped policies.
create policy spark_media_read on storage.objects for select to authenticated
  using (bucket_id in ('spark-media','spark-avatars') and spark_private.can_read_media(bucket_id,name));
create policy spark_media_create on storage.objects for insert to authenticated
  with check (bucket_id in ('spark-media','spark-avatars') and (select spark_private.active_user())
    and name ~ ('^' || (select auth.uid())::text || '/[a-f0-9-]{36}\.jpg$'));
create policy spark_media_remove on storage.objects for delete to authenticated
  using (bucket_id in ('spark-media','spark-avatars') and (select spark_private.active_user())
    and spark_private.media_unlinked(bucket_id,name));
-- No UPDATE policy: clients cannot overwrite published photos.
commit;
