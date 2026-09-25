create table public.sparknew_profile_highlights (
 id uuid primary key default gen_random_uuid(),
 owner_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 post_id uuid not null references public.sparknew_posts(id) on delete cascade,
 title text not null check(char_length(trim(title)) between 1 and 50),
 created_at timestamptz not null default now(),
 unique(owner_id,post_id)
);
create index sparknew_highlight_post on public.sparknew_profile_highlights(post_id);
alter table public.sparknew_profile_highlights enable row level security;
grant select,insert,delete on public.sparknew_profile_highlights to authenticated;
create policy highlight_read on public.sparknew_profile_highlights for select to authenticated using (
 not sparknew_v1_private.blocked(owner_id) and exists(select 1 from public.sparknew_posts p where p.id=post_id));
create policy highlight_create on public.sparknew_profile_highlights for insert to authenticated with check (
 owner_id=(select auth.uid()) and exists(select 1 from public.sparknew_posts p where p.id=post_id and p.author_id=(select auth.uid()) and p.kind='post' and p.media_type='image'));
create policy highlight_delete on public.sparknew_profile_highlights for delete to authenticated using(owner_id=(select auth.uid()));
notify pgrst,'reload schema';
