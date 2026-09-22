-- Spark new app. Isolated from all pre-existing app tables.
create schema sparknew_v1_private;
revoke all on schema sparknew_v1_private from public, anon;
grant usage on schema sparknew_v1_private to authenticated;
create table public.sparknew_profiles (
 id uuid primary key references auth.users(id) on delete cascade,
 display_name text not null check(char_length(display_name) between 1 and 80),
 bio text not null default '' check(char_length(bio)<=400),
 avatar_path text, cover_path text, created_at timestamptz not null default now()
);
create table public.sparknew_blocks (
 owner_id uuid references public.sparknew_profiles(id) on delete cascade,
 target_id uuid references public.sparknew_profiles(id) on delete cascade,
 primary key(owner_id,target_id), check(owner_id<>target_id)
);
create index sparknew_blocks_target on public.sparknew_blocks(target_id);
create table public.sparknew_friendships (
 id uuid primary key default gen_random_uuid(),
 sender_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 receiver_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 status text not null default 'pending' check(status in ('pending','accepted')),
 created_at timestamptz not null default now(), check(sender_id<>receiver_id)
);
create unique index sparknew_friend_pair on public.sparknew_friendships(least(sender_id,receiver_id),greatest(sender_id,receiver_id));
create index sparknew_friend_receiver on public.sparknew_friendships(receiver_id,status);
create index sparknew_friend_sender on public.sparknew_friendships(sender_id,status);
create table public.sparknew_follows (
 follower_id uuid references public.sparknew_profiles(id) on delete cascade,
 following_id uuid references public.sparknew_profiles(id) on delete cascade,
 primary key(follower_id,following_id), check(follower_id<>following_id)
);
create index sparknew_follow_target on public.sparknew_follows(following_id);
create table public.sparknew_communities (
 id uuid primary key default gen_random_uuid(), owner_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 name text not null check(char_length(name) between 1 and 100), description text not null default '' check(char_length(description)<=2000),
 kind text not null check(kind in ('group','page')), created_at timestamptz not null default now()
);
create index sparknew_community_owner on public.sparknew_communities(owner_id);
create table public.sparknew_memberships (
 community_id uuid references public.sparknew_communities(id) on delete cascade,
 user_id uuid references public.sparknew_profiles(id) on delete cascade,
 primary key(community_id,user_id)
);
create index sparknew_member_user on public.sparknew_memberships(user_id);
create table public.sparknew_posts (
 id uuid primary key default gen_random_uuid(), author_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 body text not null default '' check(char_length(body)<=10000), media_path text, media_type text check(media_type in ('image','video')),
 kind text not null default 'post' check(kind in ('post','story','reel')),
 visibility text not null default 'public' check(visibility in ('public','friends','private')),
 community_id uuid references public.sparknew_communities(id) on delete cascade,
 created_at timestamptz not null default now(), expires_at timestamptz,
 check(length(trim(body))>0 or media_path is not null),
 check((media_path is null)=(media_type is null)),
 check(kind<>'reel' or media_type='video'),
 check((kind='story' and expires_at is not null) or (kind<>'story' and expires_at is null))
);
create index sparknew_posts_feed on public.sparknew_posts(kind,created_at desc,id desc);
create index sparknew_posts_author on public.sparknew_posts(author_id,created_at desc);
create index sparknew_posts_community on public.sparknew_posts(community_id,created_at desc);
create index sparknew_posts_media on public.sparknew_posts(media_path) where media_path is not null;
create table public.sparknew_reactions (
 post_id uuid references public.sparknew_posts(id) on delete cascade,
 user_id uuid references public.sparknew_profiles(id) on delete cascade,
 reaction text not null check(reaction in ('like','love','haha','wow','sad','angry')), primary key(post_id,user_id)
);
create index sparknew_reactions_user on public.sparknew_reactions(user_id);
create table public.sparknew_comments (
 id uuid primary key default gen_random_uuid(), post_id uuid not null references public.sparknew_posts(id) on delete cascade,
 author_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 body text not null check(char_length(trim(body)) between 1 and 3000), created_at timestamptz not null default now()
);
create index sparknew_comments_post on public.sparknew_comments(post_id,created_at);
create index sparknew_comments_author on public.sparknew_comments(author_id);
create table public.sparknew_saved (
 post_id uuid references public.sparknew_posts(id) on delete cascade,
 user_id uuid references public.sparknew_profiles(id) on delete cascade, primary key(post_id,user_id)
);
create index sparknew_saved_user on public.sparknew_saved(user_id);
create table public.sparknew_conversations (
 id uuid primary key default gen_random_uuid(),
 user_a uuid not null references public.sparknew_profiles(id) on delete cascade,
 user_b uuid not null references public.sparknew_profiles(id) on delete cascade,
 created_at timestamptz not null default now(), check(user_a<user_b), unique(user_a,user_b)
);
create index sparknew_conversation_b on public.sparknew_conversations(user_b);
create table public.sparknew_messages (
 id uuid primary key default gen_random_uuid(), conversation_id uuid not null references public.sparknew_conversations(id) on delete cascade,
 sender_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 body text not null default '' check(char_length(body)<=10000), media_path text,
 media_type text check(media_type in ('image','video')), created_at timestamptz not null default now(),
 check(length(trim(body))>0 or media_path is not null), check((media_path is null)=(media_type is null))
);
create index sparknew_messages_conversation on public.sparknew_messages(conversation_id,created_at desc,id desc);
create index sparknew_messages_sender on public.sparknew_messages(sender_id);
create index sparknew_messages_media on public.sparknew_messages(media_path) where media_path is not null;
create table public.sparknew_notifications (
 id uuid primary key default gen_random_uuid(), recipient_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 actor_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 kind text not null, target_id uuid, is_read boolean not null default false, created_at timestamptz not null default now()
);
create index sparknew_notifications_recipient on public.sparknew_notifications(recipient_id,created_at desc);
create index sparknew_notifications_actor on public.sparknew_notifications(actor_id);
create table public.sparknew_listings (
 id uuid primary key default gen_random_uuid(), seller_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 title text not null check(char_length(title) between 1 and 150), description text not null default '' check(char_length(description)<=5000),
 price numeric(12,2) not null check(price>=0), location text not null default '', media_path text,
 sold boolean not null default false, created_at timestamptz not null default now()
);
create index sparknew_listings_seller on public.sparknew_listings(seller_id);
create index sparknew_listings_created on public.sparknew_listings(created_at desc);
create index sparknew_listings_media on public.sparknew_listings(media_path) where media_path is not null;
create table public.sparknew_reports (
 id uuid primary key default gen_random_uuid(), reporter_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 target_type text not null check(target_type in ('post','profile','listing','message')), target_id uuid not null,
 reason text not null check(char_length(trim(reason)) between 1 and 2000), created_at timestamptz not null default now()
);
create index sparknew_reports_reporter on public.sparknew_reports(reporter_id);
create table public.sparknew_calls (
 id uuid primary key default gen_random_uuid(), conversation_id uuid not null references public.sparknew_conversations(id) on delete cascade,
 caller_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 callee_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 video boolean not null default false, status text not null default 'ringing' check(status in ('ringing','accepted','ended','declined')),
 offer jsonb, answer jsonb, created_at timestamptz not null default now(), check(caller_id<>callee_id)
);
create index sparknew_calls_callee on public.sparknew_calls(callee_id,status,created_at desc);
create index sparknew_calls_caller on public.sparknew_calls(caller_id);
create index sparknew_calls_conversation on public.sparknew_calls(conversation_id);
create table public.sparknew_ice (
 id bigint generated always as identity primary key, call_id uuid not null references public.sparknew_calls(id) on delete cascade,
 user_id uuid not null references public.sparknew_profiles(id) on delete cascade, candidate jsonb not null,
 created_at timestamptz not null default now()
);
create index sparknew_ice_call on public.sparknew_ice(call_id,id);
create index sparknew_ice_user on public.sparknew_ice(user_id);
-- Private helpers deliberately bypass only relationship-table RLS to avoid policy recursion.
-- They always bind one side of the relationship to auth.uid().
create function sparknew_v1_private.blocked(other uuid) returns boolean language sql stable security definer set search_path='' as $$
 select auth.uid() is null or exists(select 1 from public.sparknew_blocks b where
 (b.owner_id=auth.uid() and b.target_id=other) or (b.target_id=auth.uid() and b.owner_id=other)); $$;
create function sparknew_v1_private.friend(other uuid) returns boolean language sql stable security definer set search_path='' as $$
 select auth.uid() is not null and exists(select 1 from public.sparknew_friendships f where f.status='accepted' and
 ((f.sender_id=auth.uid() and f.receiver_id=other) or (f.receiver_id=auth.uid() and f.sender_id=other))); $$;
create function sparknew_v1_private.in_chat(chat uuid) returns boolean language sql stable security definer set search_path='' as $$
 select auth.uid() is not null and exists(select 1 from public.sparknew_conversations c where c.id=chat and auth.uid() in(c.user_a,c.user_b)
 and not sparknew_v1_private.blocked(case when c.user_a=auth.uid() then c.user_b else c.user_a end)); $$;
create function sparknew_v1_private.own_path(path text) returns boolean language sql stable set search_path='' as $$
 select path is null or (auth.uid() is not null and split_part(path,'/',1)=auth.uid()::text and path !~ '\.\.'); $$;
alter table public.sparknew_profiles enable row level security;
revoke all on public.sparknew_profiles from anon, authenticated;
grant select on public.sparknew_profiles to authenticated;
alter table public.sparknew_blocks enable row level security;
revoke all on public.sparknew_blocks from anon, authenticated;
grant select on public.sparknew_blocks to authenticated;
alter table public.sparknew_friendships enable row level security;
revoke all on public.sparknew_friendships from anon, authenticated;
grant select on public.sparknew_friendships to authenticated;
alter table public.sparknew_follows enable row level security;
revoke all on public.sparknew_follows from anon, authenticated;
grant select on public.sparknew_follows to authenticated;
alter table public.sparknew_communities enable row level security;
revoke all on public.sparknew_communities from anon, authenticated;
grant select on public.sparknew_communities to authenticated;
alter table public.sparknew_memberships enable row level security;
revoke all on public.sparknew_memberships from anon, authenticated;
grant select on public.sparknew_memberships to authenticated;
alter table public.sparknew_posts enable row level security;
revoke all on public.sparknew_posts from anon, authenticated;
grant select on public.sparknew_posts to authenticated;
alter table public.sparknew_reactions enable row level security;
revoke all on public.sparknew_reactions from anon, authenticated;
grant select on public.sparknew_reactions to authenticated;
alter table public.sparknew_comments enable row level security;
revoke all on public.sparknew_comments from anon, authenticated;
grant select on public.sparknew_comments to authenticated;
alter table public.sparknew_saved enable row level security;
revoke all on public.sparknew_saved from anon, authenticated;
grant select on public.sparknew_saved to authenticated;
alter table public.sparknew_conversations enable row level security;
revoke all on public.sparknew_conversations from anon, authenticated;
grant select on public.sparknew_conversations to authenticated;
alter table public.sparknew_messages enable row level security;
revoke all on public.sparknew_messages from anon, authenticated;
grant select on public.sparknew_messages to authenticated;
alter table public.sparknew_notifications enable row level security;
revoke all on public.sparknew_notifications from anon, authenticated;
grant select on public.sparknew_notifications to authenticated;
alter table public.sparknew_listings enable row level security;
revoke all on public.sparknew_listings from anon, authenticated;
grant select on public.sparknew_listings to authenticated;
alter table public.sparknew_reports enable row level security;
revoke all on public.sparknew_reports from anon, authenticated;
grant select on public.sparknew_reports to authenticated;
alter table public.sparknew_calls enable row level security;
revoke all on public.sparknew_calls from anon, authenticated;
grant select on public.sparknew_calls to authenticated;
alter table public.sparknew_ice enable row level security;
revoke all on public.sparknew_ice from anon, authenticated;
grant select on public.sparknew_ice to authenticated;
create policy profile_read on public.sparknew_profiles for select to authenticated using (not sparknew_v1_private.blocked(id));
create policy profile_add on public.sparknew_profiles for insert to authenticated with check (id=(select auth.uid()) and sparknew_v1_private.own_path(avatar_path) and sparknew_v1_private.own_path(cover_path));
create policy profile_edit on public.sparknew_profiles for update to authenticated using (id=(select auth.uid())) with check (id=(select auth.uid()) and sparknew_v1_private.own_path(avatar_path) and sparknew_v1_private.own_path(cover_path));
grant insert, update(display_name,bio,avatar_path,cover_path) on public.sparknew_profiles to authenticated;
create policy block_read on public.sparknew_blocks for select to authenticated using (owner_id=(select auth.uid()));
create policy block_add on public.sparknew_blocks for insert to authenticated with check (owner_id=(select auth.uid()));
create policy block_remove on public.sparknew_blocks for delete to authenticated using (owner_id=(select auth.uid()));
grant insert,delete on public.sparknew_blocks to authenticated;
create policy friend_read on public.sparknew_friendships for select to authenticated using ((select auth.uid()) in(sender_id,receiver_id) and not sparknew_v1_private.blocked(case when sender_id=(select auth.uid()) then receiver_id else sender_id end));
create policy friend_add on public.sparknew_friendships for insert to authenticated with check (sender_id=(select auth.uid()) and status='pending' and not sparknew_v1_private.blocked(receiver_id));
create policy friend_accept on public.sparknew_friendships for update to authenticated using (receiver_id=(select auth.uid()) and status='pending' and not sparknew_v1_private.blocked(sender_id)) with check (receiver_id=(select auth.uid()) and status='accepted' and not sparknew_v1_private.blocked(sender_id));
create policy friend_remove on public.sparknew_friendships for delete to authenticated using ((select auth.uid()) in(sender_id,receiver_id));
grant insert,delete,update(status) on public.sparknew_friendships to authenticated;
create policy follow_read on public.sparknew_follows for select to authenticated using (not sparknew_v1_private.blocked(follower_id) and not sparknew_v1_private.blocked(following_id));
create policy follow_add on public.sparknew_follows for insert to authenticated with check (follower_id=(select auth.uid()) and not sparknew_v1_private.blocked(following_id));
create policy follow_remove on public.sparknew_follows for delete to authenticated using (follower_id=(select auth.uid()));
grant insert,delete on public.sparknew_follows to authenticated;
create policy community_read on public.sparknew_communities for select to authenticated using (not sparknew_v1_private.blocked(owner_id));
create policy community_add on public.sparknew_communities for insert to authenticated with check (owner_id=(select auth.uid()));
create policy community_edit on public.sparknew_communities for update to authenticated using (owner_id=(select auth.uid())) with check (owner_id=(select auth.uid()));
create policy community_remove on public.sparknew_communities for delete to authenticated using (owner_id=(select auth.uid()));
grant insert,delete,update(name,description) on public.sparknew_communities to authenticated;
create policy member_read on public.sparknew_memberships for select to authenticated using (exists(select 1 from public.sparknew_communities c where c.id=community_id));
create policy member_add on public.sparknew_memberships for insert to authenticated with check (user_id=(select auth.uid()) and exists(select 1 from public.sparknew_communities c where c.id=community_id));
create policy member_remove on public.sparknew_memberships for delete to authenticated using (user_id=(select auth.uid()));
grant insert,delete on public.sparknew_memberships to authenticated;
create policy post_read on public.sparknew_posts for select to authenticated using (not sparknew_v1_private.blocked(author_id) and (expires_at is null or expires_at>now()) and (author_id=(select auth.uid()) or visibility='public' or (visibility='friends' and sparknew_v1_private.friend(author_id))));
create policy post_add on public.sparknew_posts for insert to authenticated with check (author_id=(select auth.uid()) and sparknew_v1_private.own_path(media_path) and (community_id is null or exists(select 1 from public.sparknew_communities c where c.id=community_id and (c.owner_id=(select auth.uid()) or (c.kind='group' and exists(select 1 from public.sparknew_memberships m where m.community_id=c.id and m.user_id=(select auth.uid())))))));
create policy post_edit on public.sparknew_posts for update to authenticated using (author_id=(select auth.uid())) with check (author_id=(select auth.uid()) and sparknew_v1_private.own_path(media_path) and (community_id is null or exists(select 1 from public.sparknew_communities c where c.id=community_id and (c.owner_id=(select auth.uid()) or (c.kind='group' and exists(select 1 from public.sparknew_memberships m where m.community_id=c.id and m.user_id=(select auth.uid())))))));
create policy post_remove on public.sparknew_posts for delete to authenticated using (author_id=(select auth.uid()));
grant insert,delete,update(body,visibility) on public.sparknew_posts to authenticated;
create policy reaction_read on public.sparknew_reactions for select to authenticated using (exists(select 1 from public.sparknew_posts p where p.id=post_id));
create policy reaction_add on public.sparknew_reactions for insert to authenticated with check (user_id=(select auth.uid()) and exists(select 1 from public.sparknew_posts p where p.id=post_id));
create policy reaction_edit on public.sparknew_reactions for update to authenticated using (user_id=(select auth.uid())) with check (user_id=(select auth.uid()) and exists(select 1 from public.sparknew_posts p where p.id=post_id));
create policy reaction_remove on public.sparknew_reactions for delete to authenticated using (user_id=(select auth.uid()));
grant insert,delete,update(reaction) on public.sparknew_reactions to authenticated;
create policy comment_read on public.sparknew_comments for select to authenticated using (not sparknew_v1_private.blocked(author_id) and exists(select 1 from public.sparknew_posts p where p.id=post_id));
create policy comment_add on public.sparknew_comments for insert to authenticated with check (author_id=(select auth.uid()) and exists(select 1 from public.sparknew_posts p where p.id=post_id));
create policy comment_remove on public.sparknew_comments for delete to authenticated using (author_id=(select auth.uid()) or exists(select 1 from public.sparknew_posts p where p.id=post_id and p.author_id=(select auth.uid())));
grant insert,delete on public.sparknew_comments to authenticated;
create policy saved_read on public.sparknew_saved for select to authenticated using (user_id=(select auth.uid()));
create policy saved_add on public.sparknew_saved for insert to authenticated with check (user_id=(select auth.uid()) and exists(select 1 from public.sparknew_posts p where p.id=post_id));
create policy saved_remove on public.sparknew_saved for delete to authenticated using (user_id=(select auth.uid()));
grant insert,delete on public.sparknew_saved to authenticated;
create policy chat_read on public.sparknew_conversations for select to authenticated using ((select auth.uid()) in(user_a,user_b) and not sparknew_v1_private.blocked(case when user_a=(select auth.uid()) then user_b else user_a end));
create policy chat_add on public.sparknew_conversations for insert to authenticated with check ((select auth.uid()) in(user_a,user_b) and not sparknew_v1_private.blocked(case when user_a=(select auth.uid()) then user_b else user_a end));
grant insert on public.sparknew_conversations to authenticated;
create policy message_read on public.sparknew_messages for select to authenticated using (sparknew_v1_private.in_chat(conversation_id));
create policy message_add on public.sparknew_messages for insert to authenticated with check (sender_id=(select auth.uid()) and sparknew_v1_private.in_chat(conversation_id) and sparknew_v1_private.own_path(media_path));
create policy message_remove on public.sparknew_messages for delete to authenticated using (sender_id=(select auth.uid()));
grant insert,delete on public.sparknew_messages to authenticated;
create policy notification_read on public.sparknew_notifications for select to authenticated using (recipient_id=(select auth.uid()) and not sparknew_v1_private.blocked(actor_id));
create policy notification_edit on public.sparknew_notifications for update to authenticated using (recipient_id=(select auth.uid())) with check (recipient_id=(select auth.uid()));
create policy notification_remove on public.sparknew_notifications for delete to authenticated using (recipient_id=(select auth.uid()));
grant update(is_read),delete on public.sparknew_notifications to authenticated;
create policy listing_read on public.sparknew_listings for select to authenticated using (not sparknew_v1_private.blocked(seller_id));
create policy listing_add on public.sparknew_listings for insert to authenticated with check (seller_id=(select auth.uid()) and sparknew_v1_private.own_path(media_path));
create policy listing_edit on public.sparknew_listings for update to authenticated using (seller_id=(select auth.uid())) with check (seller_id=(select auth.uid()) and sparknew_v1_private.own_path(media_path));
create policy listing_remove on public.sparknew_listings for delete to authenticated using (seller_id=(select auth.uid()));
grant insert,delete,update(title,description,price,location,sold) on public.sparknew_listings to authenticated;
create policy report_read on public.sparknew_reports for select to authenticated using (reporter_id=(select auth.uid()));
create policy report_add on public.sparknew_reports for insert to authenticated with check (reporter_id=(select auth.uid()));
grant insert on public.sparknew_reports to authenticated;
create policy call_read on public.sparknew_calls for select to authenticated using ((select auth.uid()) in(caller_id,callee_id) and sparknew_v1_private.in_chat(conversation_id));
create policy call_add on public.sparknew_calls for insert to authenticated with check (caller_id=(select auth.uid()) and status='ringing' and answer is null and sparknew_v1_private.in_chat(conversation_id) and exists(select 1 from public.sparknew_conversations c where c.id=conversation_id and callee_id in(c.user_a,c.user_b)));
create policy call_edit on public.sparknew_calls for update to authenticated using ((select auth.uid()) in(caller_id,callee_id) and sparknew_v1_private.in_chat(conversation_id)) with check ((select auth.uid()) in(caller_id,callee_id) and sparknew_v1_private.in_chat(conversation_id));
grant insert,update(status,offer,answer) on public.sparknew_calls to authenticated;
create policy ice_read on public.sparknew_ice for select to authenticated using (exists(select 1 from public.sparknew_calls c where c.id=call_id));
create policy ice_add on public.sparknew_ice for insert to authenticated with check (user_id=(select auth.uid()) and exists(select 1 from public.sparknew_calls c where c.id=call_id and c.status in ('ringing','accepted')));
grant insert on public.sparknew_ice to authenticated;
grant usage on sequence public.sparknew_ice_id_seq to authenticated;
-- Enforce caller/callee signaling roles and one-way terminal call states.
create function sparknew_v1_private.guard_call() returns trigger language plpgsql set search_path='' as $$
begin
 if old.status in ('ended','declined') then raise exception 'Call has ended'; end if;
 if new.offer is distinct from old.offer and auth.uid()<>old.caller_id then raise exception 'Only caller can offer'; end if;
 if new.answer is distinct from old.answer and auth.uid()<>old.callee_id then raise exception 'Only recipient can answer'; end if;
 if new.status='accepted' and (auth.uid()<>old.callee_id or old.status<>'ringing') then raise exception 'Invalid acceptance'; end if;
 if new.status='declined' and (auth.uid()<>old.callee_id or old.status<>'ringing') then raise exception 'Invalid decline'; end if;
 if new.status='ringing' and old.status<>'ringing' then raise exception 'Invalid transition'; end if;
 return new;
end; $$;
create trigger sparknew_call_guard before update on public.sparknew_calls for each row execute function sparknew_v1_private.guard_call();
-- Database timestamps and story expiry are server-controlled on insert.
create function sparknew_v1_private.post_timestamp() returns trigger language plpgsql set search_path='' as $$
begin
 new.created_at=now();
 new.expires_at=case when new.kind='story' then now()+interval '24 hours' else null end;
 return new;
end; $$;
create trigger sparknew_post_time before insert on public.sparknew_posts for each row execute function sparknew_v1_private.post_timestamp();
-- Trigger-only privilege is necessary to create recipient notifications, which clients cannot insert.
create function sparknew_v1_private.notify() returns trigger language plpgsql security definer set search_path='' as $$
declare recipient uuid; actor uuid; target uuid; event text;
begin
 if auth.uid() is null then return new; end if;
 if TG_TABLE_NAME='sparknew_comments' then
   actor=new.author_id; target=new.post_id; event='comment'; select author_id into recipient from public.sparknew_posts where id=new.post_id;
 elsif TG_TABLE_NAME='sparknew_reactions' then
   actor=new.user_id; target=new.post_id; event='reaction'; select author_id into recipient from public.sparknew_posts where id=new.post_id;
 elsif TG_TABLE_NAME='sparknew_friendships' then
   if TG_OP='INSERT' then actor=new.sender_id; recipient=new.receiver_id; event='friend_request';
   else actor=new.receiver_id; recipient=new.sender_id; event='friend_accepted'; end if; target=new.id;
 elsif TG_TABLE_NAME='sparknew_messages' then
   actor=new.sender_id; target=new.conversation_id; event='message';
   select case when user_a=actor then user_b else user_a end into recipient from public.sparknew_conversations where id=new.conversation_id;
 end if;
 if actor=auth.uid() and recipient is not null and actor<>recipient and not sparknew_v1_private.blocked(recipient) then
 insert into public.sparknew_notifications(recipient_id,actor_id,kind,target_id) values(recipient,actor,event,target);
 end if; return new;
end; $$;
create trigger sparknew_comment_notice after insert on public.sparknew_comments for each row execute function sparknew_v1_private.notify();
create trigger sparknew_reaction_notice after insert on public.sparknew_reactions for each row execute function sparknew_v1_private.notify();
create trigger sparknew_friend_notice after insert or update on public.sparknew_friendships for each row execute function sparknew_v1_private.notify();
create trigger sparknew_message_notice after insert on public.sparknew_messages for each row execute function sparknew_v1_private.notify();
-- Private storage: uploading is owner-scoped; reading follows the referencing row's RLS.
insert into storage.buckets(id,name,public,file_size_limit,allowed_mime_types)
 values('spark-media-v1','spark-media-v1',false,26214400,array['image/jpeg','image/png','image/webp','video/mp4','video/webm']);
create function sparknew_v1_private.media_visible(path text) returns boolean language sql stable security invoker set search_path='' as $$
 select auth.uid() is not null and (
 split_part(path,'/',1)=auth.uid()::text
 or exists(select 1 from public.sparknew_profiles p where p.avatar_path=path or p.cover_path=path)
 or exists(select 1 from public.sparknew_posts p where p.media_path=path)
 or exists(select 1 from public.sparknew_messages m where m.media_path=path)
 or exists(select 1 from public.sparknew_listings l where l.media_path=path)); $$;
create policy sparknew_media_read on storage.objects for select to authenticated
 using(bucket_id='spark-media-v1' and sparknew_v1_private.media_visible(name));
create policy sparknew_media_add on storage.objects for insert to authenticated
 with check(bucket_id='spark-media-v1' and sparknew_v1_private.own_path(name));
create policy sparknew_media_delete on storage.objects for delete to authenticated
 using(bucket_id='spark-media-v1' and split_part(name,'/',1)=(select auth.uid())::text);
revoke all on all functions in schema sparknew_v1_private from public,anon,authenticated;
grant execute on function sparknew_v1_private.blocked(uuid),sparknew_v1_private.friend(uuid),sparknew_v1_private.in_chat(uuid),sparknew_v1_private.own_path(text),sparknew_v1_private.media_visible(text) to authenticated;
notify pgrst,'reload schema';
