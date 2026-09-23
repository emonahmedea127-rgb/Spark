-- Profile photo changes and their feed announcements commit together.
create function sparknew_v1_private.announce_profile_photo() returns trigger
language plpgsql security invoker set search_path='' as $$
begin
 if new.avatar_path is distinct from old.avatar_path and new.avatar_path is not null then
  insert into public.sparknew_posts(author_id,body,media_path,media_type,kind,visibility)
  values(new.id,'updated their profile picture.',new.avatar_path,'image','post','public');
 end if;
 if new.cover_path is distinct from old.cover_path and new.cover_path is not null then
  insert into public.sparknew_posts(author_id,body,media_path,media_type,kind,visibility)
  values(new.id,'updated their cover photo.',new.cover_path,'image','post','public');
 end if;
 return new;
end; $$;
revoke all on function sparknew_v1_private.announce_profile_photo() from public,anon;
create trigger sparknew_announce_profile_photo after update of avatar_path,cover_path on public.sparknew_profiles
for each row execute function sparknew_v1_private.announce_profile_photo();
