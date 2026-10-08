-- Existing v1.16 clients gain inbox reactions without an APK update.
-- One transaction keeps the reaction, view and inbox message consistent.
create or replace function public.sparknew_react_to_story(content_id uuid,reaction_value text,event_id uuid) returns void
language plpgsql security invoker set search_path='' as $$
declare
 story_owner uuid;
 story_caption text;
 chat_id uuid;
 emoji text;
begin
 insert into public.sparknew_story_reaction_events(id,post_id,user_id,reaction)
 values(event_id,content_id,auth.uid(),reaction_value) on conflict(id) do nothing;
 if not found then return; end if;
 insert into public.sparknew_reactions(post_id,user_id,reaction) values(content_id,auth.uid(),reaction_value)
 on conflict(post_id,user_id) do update set reaction=excluded.reaction;
 select p.author_id,left(p.body,200) into story_owner,story_caption from public.sparknew_posts p where p.id=content_id;
 if story_owner<>auth.uid() then
  perform public.sparknew_record_view(content_id);
  insert into public.sparknew_conversations(user_a,user_b)
  values(least(auth.uid(),story_owner),greatest(auth.uid(),story_owner))
  on conflict(user_a,user_b) do nothing;
  select c.id into chat_id from public.sparknew_conversations c
  where c.user_a=least(auth.uid(),story_owner) and c.user_b=greatest(auth.uid(),story_owner);
  emoji=case reaction_value when 'love' then '❤️' when 'like' then '👍' when 'haha' then '😆' when 'wow' then '😮' when 'sad' then '😢' when 'angry' then '😡' end;
  insert into public.sparknew_messages(conversation_id,sender_id,body)
  values(chat_id,auth.uid(),emoji||' reacted to your story'||case when coalesce(story_caption,'')='' then '' else E':\n'||story_caption end);
 end if;
end;$$;
revoke all on function public.sparknew_react_to_story(uuid,text,uuid) from public,anon;
grant execute on function public.sparknew_react_to_story(uuid,text,uuid) to authenticated;
