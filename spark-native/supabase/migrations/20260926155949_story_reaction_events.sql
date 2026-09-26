-- Preserve each story reaction tap without changing one-reaction-per-post semantics.
create table public.sparknew_story_reaction_events (
 id uuid primary key,
 post_id uuid not null references public.sparknew_posts(id) on delete cascade,
 user_id uuid not null default auth.uid() references public.sparknew_profiles(id) on delete cascade,
 reaction text not null check(reaction in ('like','love','haha','wow','sad','angry')),
 created_at timestamptz not null default now()
);
create index sparknew_story_reaction_post_user on public.sparknew_story_reaction_events(post_id,user_id);
create index sparknew_story_reaction_user on public.sparknew_story_reaction_events(user_id);
alter table public.sparknew_story_reaction_events enable row level security;
revoke all on public.sparknew_story_reaction_events from public,anon,authenticated;
grant select on public.sparknew_story_reaction_events to authenticated;
grant insert(id,post_id,user_id,reaction) on public.sparknew_story_reaction_events to authenticated;
create policy story_reaction_add on public.sparknew_story_reaction_events for insert to authenticated
 with check (user_id=(select auth.uid()) and exists(select 1 from public.sparknew_posts p where p.id=post_id and p.kind='story' and p.expires_at>now()));
create policy story_reaction_read on public.sparknew_story_reaction_events for select to authenticated
 using (exists(select 1 from public.sparknew_posts p where p.id=post_id and (p.author_id=(select auth.uid()) or user_id=(select auth.uid()))));
create function public.sparknew_react_to_story(content_id uuid,reaction_value text,event_id uuid) returns void
 language plpgsql security invoker set search_path='' as $$
begin
 insert into public.sparknew_story_reaction_events(id,post_id,user_id,reaction)
 values(event_id,content_id,auth.uid(),reaction_value) on conflict(id) do nothing;
 if found then
  insert into public.sparknew_reactions(post_id,user_id,reaction) values(content_id,auth.uid(),reaction_value)
  on conflict(post_id,user_id) do update set reaction=excluded.reaction;
 end if;
end;$$;
create function public.sparknew_story_reaction_totals(content_id uuid)
 returns table(user_id uuid,reaction text,total bigint)
 language sql stable security invoker set search_path='' as $$
 select e.user_id,e.reaction,count(*) from public.sparknew_story_reaction_events e
 where e.post_id=content_id and exists(select 1 from public.sparknew_posts p where p.id=content_id and p.author_id=auth.uid())
 group by e.user_id,e.reaction order by e.user_id,e.reaction;
$$;
revoke all on function public.sparknew_react_to_story(uuid,text,uuid),public.sparknew_story_reaction_totals(uuid) from public,anon;
grant execute on function public.sparknew_react_to_story(uuid,text,uuid),public.sparknew_story_reaction_totals(uuid) to authenticated;
