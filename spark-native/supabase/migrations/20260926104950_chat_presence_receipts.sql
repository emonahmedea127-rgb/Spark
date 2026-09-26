create table public.sparknew_presence(
 user_id uuid primary key references public.sparknew_profiles(id) on delete cascade,
 last_active timestamptz not null default now()
);
alter table public.sparknew_presence enable row level security;
revoke all on public.sparknew_presence from public,anon,authenticated;
grant select,insert,update on public.sparknew_presence to authenticated;
create policy presence_read on public.sparknew_presence for select to authenticated using(
 user_id=(select auth.uid()) or (not sparknew_v1_private.blocked(user_id) and
 (sparknew_v1_private.friend(user_id) or exists(select 1 from public.sparknew_conversations c
 where (c.user_a=auth.uid() and c.user_b=user_id) or (c.user_b=auth.uid() and c.user_a=user_id)))));
create policy presence_add on public.sparknew_presence for insert to authenticated
 with check(user_id=(select auth.uid()) and last_active between now()-interval '5 seconds' and now());
create policy presence_update on public.sparknew_presence for update to authenticated
 using(user_id=(select auth.uid())) with check(user_id=(select auth.uid()) and last_active between now()-interval '5 seconds' and now());
create function public.sparknew_presence_pulse() returns void language sql security invoker set search_path='' as $$
 insert into public.sparknew_presence(user_id,last_active) values(auth.uid(),now())
 on conflict(user_id) do update set last_active=now();
$$;
revoke all on function public.sparknew_presence_pulse() from public,anon;
grant execute on function public.sparknew_presence_pulse() to authenticated;

alter table public.sparknew_messages add column delivered_at timestamptz;
create function sparknew_v1_private.mark_delivered(message_ids uuid[]) returns void
 language sql security definer set search_path='' as $$
 update public.sparknew_messages m set delivered_at=now()
 where auth.uid() is not null and m.id=any(message_ids[1:100]) and m.sender_id<>auth.uid()
 and m.delivered_at is null and m.unsent_at is null and sparknew_v1_private.in_chat(m.conversation_id);
$$;
create function public.sparknew_mark_delivered(message_ids uuid[]) returns void
 language sql security invoker set search_path='' as $$select sparknew_v1_private.mark_delivered(message_ids);$$;
revoke all on function sparknew_v1_private.mark_delivered(uuid[]),public.sparknew_mark_delivered(uuid[]) from public,anon;
grant execute on function sparknew_v1_private.mark_delivered(uuid[]),public.sparknew_mark_delivered(uuid[]) to authenticated;
notify pgrst,'reload schema';
