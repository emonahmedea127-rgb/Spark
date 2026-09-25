-- Comment reactions retain existing likes as the default reaction.
alter table public.sparknew_comment_likes add column reaction text not null default 'like' check(reaction in('like','love','haha','wow','sad','angry'));
grant update(reaction) on public.sparknew_comment_likes to authenticated;
create policy comment_reaction_edit on public.sparknew_comment_likes for update to authenticated using(user_id=(select auth.uid()) and exists(select 1 from public.sparknew_comments c where c.id=comment_id)) with check(user_id=(select auth.uid()) and exists(select 1 from public.sparknew_comments c where c.id=comment_id));
alter table public.sparknew_comments add column edited_at timestamptz;
grant update(body) on public.sparknew_comments to authenticated;
create policy comment_edit on public.sparknew_comments for update to authenticated using(author_id=(select auth.uid()) and exists(select 1 from public.sparknew_posts p where p.id=post_id)) with check(author_id=(select auth.uid()) and exists(select 1 from public.sparknew_posts p where p.id=post_id));
create function sparknew_v1_private.comment_edited() returns trigger language plpgsql security invoker set search_path='' as $$begin if new.body is distinct from old.body then new.edited_at=now();end if;return new;end;$$;
revoke all on function sparknew_v1_private.comment_edited() from public,anon;
create trigger sparknew_comment_edited before update on public.sparknew_comments for each row execute function sparknew_v1_private.comment_edited();

alter table public.sparknew_messages add column seen_at timestamptz,add column edited_at timestamptz,add column unsent_at timestamptz,add column view_once boolean not null default false,add column opened_at timestamptz;
-- Clients cannot forge server timestamps or a view-once envelope.
revoke insert on public.sparknew_messages from authenticated;
grant insert(conversation_id,sender_id,body,media_path,media_type) on public.sparknew_messages to authenticated;
create table public.sparknew_once_media(message_id uuid primary key references public.sparknew_messages(id) on delete cascade,media_path text not null unique);
alter table public.sparknew_once_media enable row level security;
revoke all on public.sparknew_once_media from public,anon,authenticated;
grant all on public.sparknew_once_media to service_role;

create function sparknew_v1_private.mark_seen(message_ids uuid[]) returns void language sql security definer set search_path='' as $$
 update public.sparknew_messages m set seen_at=now() where auth.uid() is not null and m.id=any(message_ids[1:100]) and m.sender_id<>auth.uid() and m.seen_at is null and m.unsent_at is null and sparknew_v1_private.in_chat(m.conversation_id);
$$;
create function public.sparknew_mark_seen(message_ids uuid[]) returns void language sql security invoker set search_path='' as $$select sparknew_v1_private.mark_seen(message_ids);$$;
create function sparknew_v1_private.change_message(message_id uuid,action text,new_body text) returns void language plpgsql security definer set search_path='' as $$begin
 if auth.uid() is null or not exists(select 1 from public.sparknew_messages m where m.id=message_id and m.sender_id=auth.uid() and m.unsent_at is null and sparknew_v1_private.in_chat(m.conversation_id)) then raise exception 'Message unavailable' using errcode='42501';end if;
 if action='edit' then
  if char_length(trim(new_body)) not between 1 and 10000 then raise exception 'Write between 1 and 10000 characters';end if;
  update public.sparknew_messages set body=trim(new_body),edited_at=now() where id=message_id and unsent_at is null;
 elsif action='unsend' then
  update public.sparknew_messages set body='Message unsent',media_path=null,media_type=null,unsent_at=now() where id=message_id and unsent_at is null;
  delete from public.sparknew_once_media v where v.message_id=change_message.message_id;
 else raise exception 'Unknown action';end if;
end;$$;
create function public.sparknew_change_message(message_id uuid,action text,new_body text default '') returns void language sql security invoker set search_path='' as $$select sparknew_v1_private.change_message(message_id,action,new_body);$$;
create function sparknew_v1_private.send_once(chat_id uuid,path text,caption text) returns uuid language plpgsql security definer set search_path='' as $$declare result uuid;begin
 if auth.uid() is null or not sparknew_v1_private.in_chat(chat_id) or not sparknew_v1_private.own_path(path) or split_part(path,'/',2)<>'once' or path is null then raise exception 'Photo unavailable' using errcode='42501';end if;
 if not exists(select 1 from storage.objects where bucket_id='spark-media-v1' and name=path and metadata->>'mimetype' in('image/jpeg','image/png','image/webp')) then raise exception 'Upload the photo first';end if;
 insert into public.sparknew_messages(conversation_id,sender_id,body,view_once) values(chat_id,auth.uid(),coalesce(nullif(trim(caption),''),'View once photo'),true) returning id into result;
 insert into public.sparknew_once_media(message_id,media_path) values(result,path);
 return result;
end;$$;
create function public.sparknew_send_once(chat_id uuid,path text,caption text default '') returns uuid language sql security invoker set search_path='' as $$select sparknew_v1_private.send_once(chat_id,path,caption);$$;
revoke all on function sparknew_v1_private.mark_seen(uuid[]),sparknew_v1_private.change_message(uuid,text,text),sparknew_v1_private.send_once(uuid,text,text),public.sparknew_mark_seen(uuid[]),public.sparknew_change_message(uuid,text,text),public.sparknew_send_once(uuid,text,text) from public,anon;
grant execute on function sparknew_v1_private.mark_seen(uuid[]),sparknew_v1_private.change_message(uuid,text,text),sparknew_v1_private.send_once(uuid,text,text),public.sparknew_mark_seen(uuid[]),public.sparknew_change_message(uuid,text,text),public.sparknew_send_once(uuid,text,text) to authenticated;

-- Only the authenticated Edge Function's server-side service client can claim bytes.
-- One atomic UPDATE chooses the winner even for concurrent requests.
create function public.sparknew_claim_once(message_id uuid,viewer_id uuid) returns text language sql security invoker set search_path='' as $$
 with claimed as (
  update public.sparknew_messages m set opened_at=now(),seen_at=coalesce(m.seen_at,now())
  where m.id=message_id and m.view_once and m.opened_at is null and m.unsent_at is null and m.sender_id<>viewer_id
  and exists(select 1 from public.sparknew_once_media v where v.message_id=m.id)
  and exists(select 1 from public.sparknew_conversations c where c.id=m.conversation_id and viewer_id in(c.user_a,c.user_b)
   and not exists(select 1 from public.sparknew_blocks b where (b.owner_id=c.user_a and b.target_id=c.user_b) or (b.owner_id=c.user_b and b.target_id=c.user_a)))
  returning m.id
 ) select v.media_path from public.sparknew_once_media v join claimed c on c.id=v.message_id;
$$;
revoke all on function public.sparknew_claim_once(uuid,uuid) from public,anon,authenticated;
grant execute on function public.sparknew_claim_once(uuid,uuid) to service_role;
-- Reserved view-once uploads are never served through authenticated download/sign APIs.
alter policy sparknew_media_read on storage.objects using(bucket_id='spark-media-v1' and split_part(name,'/',2)<>'once' and sparknew_v1_private.media_visible(name));
notify pgrst,'reload schema';
