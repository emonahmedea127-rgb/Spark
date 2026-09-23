-- Additive upgrade. Existing posts and comments are preserved.
alter table public.sparknew_comments add column parent_id uuid references public.sparknew_comments(id) on delete cascade;
alter table public.sparknew_comments add constraint sparknew_comment_not_self check(parent_id is null or parent_id<>id);
create index sparknew_comments_parent on public.sparknew_comments(parent_id);
create function sparknew_v1_private.validate_reply() returns trigger language plpgsql security invoker set search_path='' as $$
begin
 if new.parent_id is not null and not exists(select 1 from public.sparknew_comments c where c.id=new.parent_id and c.post_id=new.post_id) then
  raise exception 'The comment is unavailable for replies' using errcode='42501';
 end if;
 return new;
end; $$;
revoke all on function sparknew_v1_private.validate_reply() from public,anon;
create trigger sparknew_validate_reply before insert on public.sparknew_comments for each row execute function sparknew_v1_private.validate_reply();
create table public.sparknew_comment_likes (
 comment_id uuid not null references public.sparknew_comments(id) on delete cascade,
 user_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 primary key(comment_id,user_id)
);
create index sparknew_comment_likes_user on public.sparknew_comment_likes(user_id);
alter table public.sparknew_comment_likes enable row level security;
revoke all on public.sparknew_comment_likes from public,anon,authenticated;
grant select,insert,delete on public.sparknew_comment_likes to authenticated;
create policy comment_like_read on public.sparknew_comment_likes for select to authenticated using (exists(select 1 from public.sparknew_comments c where c.id=comment_id));
create policy comment_like_add on public.sparknew_comment_likes for insert to authenticated with check (user_id=(select auth.uid()) and exists(select 1 from public.sparknew_comments c where c.id=comment_id));
create policy comment_like_remove on public.sparknew_comment_likes for delete to authenticated using (user_id=(select auth.uid()));
notify pgrst,'reload schema';
