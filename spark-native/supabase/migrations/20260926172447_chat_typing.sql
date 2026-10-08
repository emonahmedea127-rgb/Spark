create table public.sparknew_chat_typing (
 conversation_id uuid not null references public.sparknew_conversations(id) on delete cascade,
 user_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 typed_at timestamptz not null default now(),
 primary key(conversation_id,user_id)
);
create index sparknew_chat_typing_user on public.sparknew_chat_typing(user_id);
alter table public.sparknew_chat_typing enable row level security;
revoke all on public.sparknew_chat_typing from public,anon,authenticated;
grant select,insert,update,delete on public.sparknew_chat_typing to authenticated;
create policy typing_read on public.sparknew_chat_typing for select to authenticated using(sparknew_v1_private.in_chat(conversation_id));
create policy typing_add on public.sparknew_chat_typing for insert to authenticated with check(user_id=(select auth.uid()) and typed_at<=now()+interval '1 second' and sparknew_v1_private.in_chat(conversation_id));
create policy typing_edit on public.sparknew_chat_typing for update to authenticated using(user_id=(select auth.uid()) and sparknew_v1_private.in_chat(conversation_id)) with check(user_id=(select auth.uid()) and typed_at<=now()+interval '1 second' and sparknew_v1_private.in_chat(conversation_id));
create policy typing_remove on public.sparknew_chat_typing for delete to authenticated using(user_id=(select auth.uid()) and sparknew_v1_private.in_chat(conversation_id));
create function public.sparknew_set_typing(chat_id uuid,is_typing boolean) returns void language plpgsql security invoker set search_path='' as $$
begin
 if is_typing then
  insert into public.sparknew_chat_typing(conversation_id,user_id,typed_at) values(chat_id,auth.uid(),now())
  on conflict(conversation_id,user_id) do update set typed_at=now();
 else
  delete from public.sparknew_chat_typing where conversation_id=chat_id and user_id=auth.uid();
 end if;
end;$$;
revoke all on function public.sparknew_set_typing(uuid,boolean) from public,anon;
grant execute on function public.sparknew_set_typing(uuid,boolean) to authenticated;
