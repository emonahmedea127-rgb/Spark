-- Spark 0.3: apply once after setup.sql. No existing user data is removed.
begin;
create table public.friendships (
 id uuid primary key default gen_random_uuid(),
 requester_id uuid not null references public.profiles on delete cascade,
 addressee_id uuid not null references public.profiles on delete cascade,
 status text not null default 'pending' check(status in ('pending','accepted')),
 created_at timestamptz not null default now(), check(requester_id<>addressee_id)
);
create unique index friendship_pair on public.friendships(least(requester_id,addressee_id),greatest(requester_id,addressee_id));
create index friendship_target on public.friendships(addressee_id,status);
create index friendship_sender on public.friendships(requester_id,status);
create function spark_private.are_friends(target uuid) returns boolean language sql stable security definer set search_path='' as $$
 select spark_private.can_discover(target) and exists(select 1 from public.friendships where status='accepted' and
 ((requester_id=auth.uid() and addressee_id=target) or (addressee_id=auth.uid() and requester_id=target)));
$$;
create or replace function spark_private.can_view_author(target uuid) returns boolean
language sql stable security definer set search_path='' as $$
 select spark_private.can_discover(target) and exists(select 1 from public.profiles p where p.id=target and
 (p.id=auth.uid() or not p.is_private or spark_private.are_friends(target) or exists(
 select 1 from public.follows f where f.follower_id=auth.uid() and f.following_id=p.id and f.status='accepted')));
$$;
alter table public.friendships enable row level security;
create policy friend_read on public.friendships for select to authenticated using (
 spark_private.can_discover(requester_id) and spark_private.can_discover(addressee_id) and
 (auth.uid() in (requester_id,addressee_id) or (status='accepted' and spark_private.can_view_author(requester_id) and spark_private.can_view_author(addressee_id))));
create policy friend_create on public.friendships for insert to authenticated with check(requester_id=auth.uid() and status='pending' and spark_private.can_discover(addressee_id));
create policy friend_accept on public.friendships for update to authenticated using(addressee_id=auth.uid() and spark_private.can_discover(requester_id)) with check(addressee_id=auth.uid() and status='accepted' and spark_private.can_discover(requester_id));
create policy friend_remove on public.friendships for delete to authenticated using(auth.uid() in (requester_id,addressee_id) and spark_private.active_user());
revoke all on public.friendships from public,anon,authenticated;
grant select,delete on public.friendships to authenticated;
grant insert(requester_id,addressee_id) on public.friendships to authenticated;
grant update(status) on public.friendships to authenticated;

drop policy follows_read on public.follows;
create policy follows_read on public.follows for select to authenticated using(spark_private.active_user() and
 not spark_private.blocked_with(follower_id) and not spark_private.blocked_with(following_id) and
 (auth.uid() in (follower_id,following_id) or (status='accepted' and spark_private.can_view_author(following_id))));
create view public.spark_profile_totals with(security_invoker=true) as select p.id,
 (select count(*)::int from public.follows where following_id=p.id and status='accepted') followers,
 (select count(*)::int from public.follows where follower_id=p.id and status='accepted') following,
 (select count(*)::int from public.friendships where status='accepted' and p.id in (requester_id,addressee_id)) friends
 from public.profiles p;

create table public.user_preferences (
 user_id uuid primary key references public.profiles on delete cascade,
 ghost_mode boolean not null default true, visit_notifications boolean not null default true
);
create table public.profile_visits (
 owner_id uuid not null references public.profiles on delete cascade,
 visitor_id uuid not null references public.profiles on delete cascade,
 visited_at timestamptz not null default now(), primary key(owner_id,visitor_id),check(owner_id<>visitor_id)
);
create index visits_owner_time on public.profile_visits(owner_id,visited_at desc);
create index visits_visitor on public.profile_visits(visitor_id);
alter table public.user_preferences enable row level security;
alter table public.profile_visits enable row level security;
create policy prefs_read on public.user_preferences for select to authenticated using(user_id=auth.uid() and spark_private.active_user());
create policy prefs_create on public.user_preferences for insert to authenticated with check(user_id=auth.uid() and spark_private.active_user());
create policy prefs_update on public.user_preferences for update to authenticated using(user_id=auth.uid() and spark_private.active_user()) with check(user_id=auth.uid());
create policy visits_read on public.profile_visits for select to authenticated using(owner_id=auth.uid() and spark_private.can_discover(visitor_id) and visited_at>now()-interval '30 days');
revoke all on public.user_preferences,public.profile_visits from public,anon,authenticated;
grant select on public.user_preferences,public.profile_visits to authenticated;
grant insert(user_id),update(ghost_mode,visit_notifications) on public.user_preferences to authenticated;
create function spark_private.record_visit(target uuid) returns void language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or target=auth.uid() or not spark_private.can_discover(target) then return; end if;
 -- Absence of settings is private by default. A modified client cannot bypass Ghost mode.
 if not exists(select 1 from public.user_preferences where user_id=auth.uid() and not ghost_mode) then return; end if;
 if exists(select 1 from public.user_preferences where user_id=target and not visit_notifications) then return; end if;
 insert into public.profile_visits(owner_id,visitor_id) values(target,auth.uid())
 on conflict(owner_id,visitor_id) do update set visited_at=now()
 where public.profile_visits.visited_at<now()-interval '1 hour';
 delete from public.profile_visits where owner_id=target and visited_at<now()-interval '30 days';
end; $$;
create function public.spark_record_visit(target uuid) returns void language sql security invoker set search_path='' as $$ select spark_private.record_visit(target); $$;
create view public.spark_visits with(security_invoker=true) as select v.*,p.username,p.display_name,p.avatar_path from public.profile_visits v join public.profiles p on p.id=v.visitor_id;

alter table public.posts add column media_kind text not null default 'photo' check(media_kind in ('photo','video'));
do $$ declare c record; begin
 for c in select conname from pg_constraint where conrelid='public.posts'::regclass and contype='c' and pg_get_constraintdef(oid) like '%image_path%' loop
 execute format('alter table public.posts drop constraint %I',c.conname); end loop;
end $$;
alter table public.posts add constraint posts_media_path check(image_path=author_id::text||'/'||id::text||case when media_kind='video' then '.mp4' else '.jpg' end);
grant insert(media_kind) on public.posts to authenticated;
drop policy posts_create on public.posts;
create policy posts_create on public.posts for insert to authenticated with check(author_id=auth.uid() and spark_private.active_user() and exists(
 select 1 from storage.objects where name=image_path and bucket_id=case when media_kind='video' then 'spark-videos' else 'spark-media' end));
create or replace view public.spark_feed with(security_invoker=true) as
 select p.id,p.author_id,p.image_path,p.caption,p.created_at,u.username,u.display_name,u.avatar_path,
 (select count(*)::int from public.likes l where l.post_id=p.id) like_count,
 (select count(*)::int from public.comments c where c.post_id=p.id) comment_count,
 exists(select 1 from public.likes l where l.post_id=p.id and l.user_id=auth.uid()) liked,
 exists(select 1 from public.saves s where s.post_id=p.id and s.user_id=auth.uid()) saved,p.media_kind
 from public.posts p join public.profiles u on u.id=p.author_id;

create table public.conversations (
 id uuid primary key default gen_random_uuid(),
 member_a uuid not null references public.profiles on delete cascade,
 member_b uuid not null references public.profiles on delete cascade,
 created_at timestamptz not null default now(),check(member_a<member_b),unique(member_a,member_b)
);
create index conversations_b on public.conversations(member_b);
create function spark_private.chat_allowed(target uuid) returns boolean language sql stable security definer set search_path='' as $$
 select spark_private.active_user() and exists(select 1 from public.conversations where id=target and
 ((member_a=auth.uid() and spark_private.are_friends(member_b)) or (member_b=auth.uid() and spark_private.are_friends(member_a))));
$$;
create table public.messages (
 id uuid primary key default gen_random_uuid(),conversation_id uuid not null references public.conversations on delete cascade,
 sender_id uuid not null references public.profiles on delete cascade,
 body text not null default '' check(char_length(body)<=2000),
 media_path text, media_kind text check(media_kind in ('photo','video')),
 post_id uuid references public.posts on delete set null,
 created_at timestamptz not null default now(),
 check((media_path is null and media_kind is null) or (media_path is not null and media_kind is not null and media_path=sender_id::text||'/'||id::text||case when media_kind='video' then '.mp4' else '.jpg' end))
);
create index messages_chat_time on public.messages(conversation_id,created_at desc,id desc);
create index messages_sender on public.messages(sender_id,created_at desc);
create index messages_post on public.messages(post_id);
create unique index messages_media on public.messages(media_path) where media_path is not null;
alter table public.conversations enable row level security;
alter table public.messages enable row level security;
create policy chats_read on public.conversations for select to authenticated using(spark_private.chat_allowed(id));
create policy chats_create on public.conversations for insert to authenticated with check(
 (member_a=auth.uid() and spark_private.are_friends(member_b)) or(member_b=auth.uid() and spark_private.are_friends(member_a)));
create policy messages_read on public.messages for select to authenticated using(spark_private.chat_allowed(conversation_id));
create policy messages_create on public.messages for insert to authenticated with check(sender_id=auth.uid() and spark_private.chat_allowed(conversation_id)
 and (char_length(btrim(body))>0 or media_path is not null or post_id is not null)
 and (post_id is null or spark_private.can_view_post(post_id))
 and (media_path is null or exists(select 1 from storage.objects where bucket_id='spark-chat' and name=media_path)));
create policy messages_remove on public.messages for delete to authenticated using(sender_id=auth.uid() and spark_private.active_user());
revoke all on public.conversations,public.messages from public,anon,authenticated;
grant select on public.conversations,public.messages to authenticated;
grant insert(member_a,member_b) on public.conversations to authenticated;
grant insert(id,conversation_id,sender_id,body,media_path,media_kind,post_id),delete on public.messages to authenticated;

create table public.call_sessions (
 id uuid primary key default gen_random_uuid(),conversation_id uuid not null references public.conversations on delete cascade,
 caller_id uuid not null references public.profiles on delete cascade,
 callee_id uuid not null references public.profiles on delete cascade,
 video boolean not null default false,status text not null default 'ringing' check(status in ('ringing','accepted','ended')),
 offer text not null check(char_length(offer) between 1 and 100000),answer text check(char_length(answer)<=100000),
 created_at timestamptz not null default now(),check(caller_id<>callee_id)
);
create index calls_callee_time on public.call_sessions(callee_id,created_at desc);
create index calls_caller_time on public.call_sessions(caller_id,created_at desc);
create index calls_chat on public.call_sessions(conversation_id);
alter table public.call_sessions enable row level security;
create policy call_read on public.call_sessions for select to authenticated using(auth.uid() in(caller_id,callee_id) and spark_private.chat_allowed(conversation_id));
create policy call_create on public.call_sessions for insert to authenticated with check(caller_id=auth.uid() and spark_private.chat_allowed(conversation_id) and exists(
 select 1 from public.conversations where id=conversation_id and callee_id in(member_a,member_b)));
create policy call_update on public.call_sessions for update to authenticated using(auth.uid() in(caller_id,callee_id) and spark_private.chat_allowed(conversation_id)) with check(auth.uid() in(caller_id,callee_id));
create function spark_private.call_transition() returns trigger language plpgsql security invoker set search_path='' as $$
begin
 if old.status='ended' then raise exception 'Call already ended'; end if;
 if new.status='ended' then new.answer=old.answer; return new; end if;
 if auth.uid()<>old.callee_id or old.status<>'ringing' or new.status<>'accepted' or new.answer is null or old.created_at<now()-interval '2 minutes' then
 raise exception 'Invalid call transition' using errcode='42501'; end if;
 return new;
end; $$;
create trigger call_state before update on public.call_sessions for each row execute function spark_private.call_transition();
revoke all on public.call_sessions from public,anon,authenticated;
grant select on public.call_sessions to authenticated;
grant insert(id,conversation_id,caller_id,callee_id,video,offer) on public.call_sessions to authenticated;
grant update(status,answer) on public.call_sessions to authenticated;

create function spark_private.social_limit() returns trigger language plpgsql security definer set search_path='' as $$
declare n bigint; begin
 if not spark_private.active_user() then raise exception 'Sign in required'; end if;
 perform 1 from public.profiles where id=auth.uid() for update;
 if tg_table_name='messages' then select count(*) into n from public.messages where sender_id=auth.uid() and created_at>now()-interval '1 minute';
 elsif tg_table_name='friendships' then select count(*) into n from public.friendships where requester_id=auth.uid() and created_at>now()-interval '1 minute';
 else select count(*) into n from public.call_sessions where caller_id=auth.uid() and created_at>now()-interval '1 minute'; end if;
 if n >= (case when tg_table_name='call_sessions' then 5 else 30 end) then raise exception 'Please wait before trying again'; end if;
 return new; end; $$;
create trigger social_limit before insert on public.friendships for each row execute function spark_private.social_limit();
create trigger social_limit before insert on public.messages for each row execute function spark_private.social_limit();
create trigger social_limit before insert on public.call_sessions for each row execute function spark_private.social_limit();

create or replace function spark_private.block_cleanup() returns trigger language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null or new.blocker_id<>auth.uid() then raise exception 'Not your block' using errcode='42501'; end if;
 delete from public.follows where (follower_id=new.blocker_id and following_id=new.blocked_id) or(follower_id=new.blocked_id and following_id=new.blocker_id);
 delete from public.friendships where (requester_id=new.blocker_id and addressee_id=new.blocked_id) or(requester_id=new.blocked_id and addressee_id=new.blocker_id);
 return new; end; $$;

insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types) values
 ('spark-videos','spark-videos',false,20971520,array['video/mp4']),
 ('spark-chat','spark-chat',false,20971520,array['image/jpeg','video/mp4']);
create function spark_private.social_media_read(bucket text,object_name text) returns boolean language sql stable security definer set search_path='' as $$
 select spark_private.active_user() and (split_part(object_name,'/',1)=auth.uid()::text or
 (bucket='spark-videos' and exists(select 1 from public.posts where image_path=object_name and media_kind='video' and spark_private.can_view_author(author_id))) or
 (bucket='spark-chat' and exists(select 1 from public.messages where media_path=object_name and spark_private.chat_allowed(conversation_id))));
$$;
create function spark_private.social_media_unlinked(bucket text,object_name text) returns boolean language sql stable security definer set search_path='' as $$
 select spark_private.active_user() and split_part(object_name,'/',1)=auth.uid()::text
 and not exists(select 1 from public.posts where bucket='spark-videos' and image_path=object_name)
 and not exists(select 1 from public.messages where bucket='spark-chat' and media_path=object_name);
$$;
create policy spark_social_media_read on storage.objects for select to authenticated using(bucket_id in('spark-videos','spark-chat') and spark_private.social_media_read(bucket_id,name));
create policy spark_social_media_create on storage.objects for insert to authenticated with check(spark_private.active_user() and
 ((bucket_id='spark-videos' and name ~ ('^'||auth.uid()::text||'/[a-f0-9-]{36}\.mp4$')) or
 (bucket_id='spark-chat' and name ~ ('^'||auth.uid()::text||'/[a-f0-9-]{36}\.(jpg|mp4)$'))));
create policy spark_social_media_remove on storage.objects for delete to authenticated using(bucket_id in('spark-videos','spark-chat') and spark_private.social_media_unlinked(bucket_id,name));

revoke all on public.spark_profile_totals,public.spark_visits from public,anon;
grant select on public.spark_profile_totals,public.spark_visits to authenticated,service_role;
grant all on public.friendships,public.user_preferences,public.profile_visits,public.conversations,public.messages,public.call_sessions to service_role;
revoke execute on function public.spark_record_visit(uuid) from public,anon;
grant execute on function public.spark_record_visit(uuid) to authenticated;
revoke execute on all functions in schema spark_private from public,anon,authenticated;
grant execute on function spark_private.active_user(),spark_private.blocked_with(uuid),spark_private.can_discover(uuid),
 spark_private.can_view_author(uuid),spark_private.can_view_post(uuid),spark_private.can_read_media(text,text),spark_private.media_unlinked(text,text),
 spark_private.are_friends(uuid),spark_private.chat_allowed(uuid),spark_private.record_visit(uuid),
 spark_private.social_media_read(text,text),spark_private.social_media_unlinked(text,text) to authenticated;
commit;
