begin;
insert into auth.users(id) values('11111111-1111-4111-8111-111111111111'),('22222222-2222-4222-8222-222222222222');
insert into public.sparknew_profiles(id,display_name) values('11111111-1111-4111-8111-111111111111','Push test one'),('22222222-2222-4222-8222-222222222222','Push test two') on conflict(id) do nothing;
set local role authenticated;
select set_config('request.jwt.claim.sub','11111111-1111-4111-8111-111111111111',true);
select public.sparknew_register_push('spark-test-device-token-123456789');
do $$ begin
 if (select count(*) from public.sparknew_push_devices where token='spark-test-device-token-123456789')<>1 then raise exception 'Own registration failed'; end if;
 if has_function_privilege(current_user,'public.sparknew_claim_push()','execute') then raise exception 'Queue claim exposed'; end if;
 if has_function_privilege(current_user,'public.sparknew_reserve_turn(uuid,integer)','execute') then raise exception 'Quota RPC exposed'; end if;
 if has_table_privilege(current_user,'public.sparknew_push_queue','select') then raise exception 'Queue exposed'; end if;
end $$;
select set_config('request.jwt.claim.sub','22222222-2222-4222-8222-222222222222',true);
do $$ begin
 if exists(select 1 from public.sparknew_push_devices where token='spark-test-device-token-123456789') then raise exception 'Device privacy failed'; end if;
end $$;
select public.sparknew_register_push('spark-test-device-token-123456789');
reset role;
do $$ begin
 if (select user_id from public.sparknew_push_devices where token='spark-test-device-token-123456789')<>'22222222-2222-4222-8222-222222222222'::uuid then raise exception 'Account switch failed'; end if;
end $$;
insert into public.sparknew_notifications(id,recipient_id,actor_id,kind) values('33333333-3333-4333-8333-333333333333','22222222-2222-4222-8222-222222222222','11111111-1111-4111-8111-111111111111','message');
do $$ begin
 if (select count(*) from public.sparknew_push_queue where event_id='33333333-3333-4333-8333-333333333333' and recipient_id='22222222-2222-4222-8222-222222222222')<>1 then raise exception 'Enqueue failed'; end if;
end $$;
set local role service_role;
-- Prevent unrelated real jobs being claimed inside this rollback-only test.
update public.sparknew_push_queue set available_at=now()+interval '1 day' where event_id<>'33333333-3333-4333-8333-333333333333';
do $$ begin
 if (select count(*) from public.sparknew_claim_push())<>1 then raise exception 'Claim failed'; end if;
 if (select count(*) from public.sparknew_claim_push())<>0 then raise exception 'Claim duplicated'; end if;
 if not public.sparknew_reserve_turn('11111111-1111-4111-8111-111111111111',2147483647) then raise exception 'Quota initial allocation failed'; end if;
 if public.sparknew_reserve_turn('11111111-1111-4111-8111-111111111111',1) then raise exception 'Quota limit bypass'; end if;
end $$;
reset role;
select 'push and TURN security checks passed' as result;
rollback;
