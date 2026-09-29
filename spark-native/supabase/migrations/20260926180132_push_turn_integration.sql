-- Device tokens are private. Registration may reassign this device after account switching.
create table public.sparknew_push_devices (
 token text primary key check(length(token) between 20 and 4096),
 user_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 updated_at timestamptz not null default now()
);
create index sparknew_push_devices_user on public.sparknew_push_devices(user_id);
alter table public.sparknew_push_devices enable row level security;
revoke all on public.sparknew_push_devices from public,anon,authenticated;
grant select,delete on public.sparknew_push_devices to authenticated;
grant all on public.sparknew_push_devices to service_role;
create policy own_push_read on public.sparknew_push_devices for select to authenticated using(user_id=(select auth.uid()));
create policy own_push_delete on public.sparknew_push_devices for delete to authenticated using(user_id=(select auth.uid()));
create function sparknew_v1_private.register_push(device_token text) returns void
language plpgsql security definer set search_path='' as $$
begin
 if auth.uid() is null then raise exception 'Sign in required'; end if;
 if device_token is null or length(device_token) not between 20 and 4096 then raise exception 'Invalid device token'; end if;
 insert into public.sparknew_push_devices(token,user_id) values(device_token,auth.uid())
 on conflict(token) do update set user_id=auth.uid(),updated_at=now();
end $$;
revoke all on function sparknew_v1_private.register_push(text) from public,anon;
grant execute on function sparknew_v1_private.register_push(text) to authenticated;
create function public.sparknew_register_push(device_token text) returns void
language sql security invoker set search_path='' as $$ select sparknew_v1_private.register_push(device_token); $$;
revoke all on function public.sparknew_register_push(text) from public,anon;
grant execute on function public.sparknew_register_push(text) to authenticated;

-- Server-only queue. No client RLS policies by design.
create table public.sparknew_push_queue (
 id uuid primary key default gen_random_uuid(),event_type text not null check(event_type in ('notice','call')),
 event_id uuid not null,recipient_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 created_at timestamptz not null default now(),expires_at timestamptz not null,
 state text not null default 'pending' check(state in ('pending','processing','sent','expired','failed')),
 attempts integer not null default 0,available_at timestamptz not null default now(),
 unique(event_type,event_id)
);
create index sparknew_push_queue_pending on public.sparknew_push_queue(available_at) where state in ('pending','processing');
create index sparknew_push_queue_recipient on public.sparknew_push_queue(recipient_id);
alter table public.sparknew_push_queue enable row level security;
revoke all on public.sparknew_push_queue from public,anon,authenticated;
grant all on public.sparknew_push_queue to service_role;
create function sparknew_v1_private.enqueue_push() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 -- Trigger-only function, not an RPC; no auth.uid requirement for server-created events.
 if tg_table_name='sparknew_calls' then
  if new.status='ringing' then insert into public.sparknew_push_queue(event_type,event_id,recipient_id,expires_at)
   values('call',new.id,new.callee_id,now()+interval '90 seconds') on conflict do nothing; end if;
 else
  insert into public.sparknew_push_queue(event_type,event_id,recipient_id,expires_at)
   values('notice',new.id,new.recipient_id,now()+interval '1 day') on conflict do nothing;
 end if;
 return new;
end $$;
revoke all on function sparknew_v1_private.enqueue_push() from public,anon,authenticated;
create trigger sparknew_enqueue_notice after insert on public.sparknew_notifications for each row execute function sparknew_v1_private.enqueue_push();
create trigger sparknew_enqueue_call after insert on public.sparknew_calls for each row execute function sparknew_v1_private.enqueue_push();
create function public.sparknew_claim_push() returns setof public.sparknew_push_queue
language plpgsql security invoker set search_path='' as $$
begin
 delete from public.sparknew_push_queue where created_at<now()-interval '7 days';
 update public.sparknew_push_queue set state='expired' where expires_at<=now() and state in ('pending','processing');
 update public.sparknew_push_queue set state='failed' where attempts>=5 and state='processing' and available_at<=now();
 return query with picked as (
  select id from public.sparknew_push_queue where state in ('pending','processing') and attempts<5
   and available_at<=now() and expires_at>now() order by created_at limit 5 for update skip locked
 ) update public.sparknew_push_queue q set state='processing',attempts=q.attempts+1,available_at=now()+interval '10 minutes'
 from picked where q.id=picked.id returning q.*;
end $$;
revoke all on function public.sparknew_claim_push() from public,anon,authenticated;
grant execute on function public.sparknew_claim_push() to service_role;

create table public.sparknew_turn_issues(user_id uuid not null references public.sparknew_profiles(id) on delete cascade,issued_at timestamptz not null default now());
create index sparknew_turn_issues_time on public.sparknew_turn_issues(issued_at);
create index sparknew_turn_issues_user on public.sparknew_turn_issues(user_id,issued_at);
alter table public.sparknew_turn_issues enable row level security;
revoke all on public.sparknew_turn_issues from public,anon,authenticated;
grant all on public.sparknew_turn_issues to service_role;
create function public.sparknew_reserve_turn(actor_id uuid,monthly_limit integer) returns boolean
language plpgsql security invoker set search_path='' as $$
begin
 if actor_id is null or monthly_limit<1 or monthly_limit is null then return false; end if;
 perform pg_catalog.pg_advisory_xact_lock(734210201);
 delete from public.sparknew_turn_issues where issued_at<now()-interval '35 days';
 if (select count(*) from public.sparknew_turn_issues where issued_at>=date_trunc('month',now()))>=monthly_limit
 or (select count(*) from public.sparknew_turn_issues where user_id=actor_id and issued_at>now()-interval '1 hour')>=20 then return false; end if;
 insert into public.sparknew_turn_issues(user_id) values(actor_id);
 return true;
end $$;
revoke all on function public.sparknew_reserve_turn(uuid,integer) from public,anon,authenticated;
grant execute on function public.sparknew_reserve_turn(uuid,integer) to service_role;
