-- Administrator-only acceptance checks; all fixtures rolled back, no email/media upload.
begin;
do $verify$
declare a uuid:=gen_random_uuid(); b uuid:=gen_random_uuid(); c uuid:=gen_random_uuid(); chat uuid; call_id uuid;
begin
 insert into auth.users(id) values(a),(b),(c);
 insert into public.profiles(id,username) values
 (a,'verify_'||left(replace(a::text,'-',''),16)),
 (b,'verify_'||left(replace(b::text,'-',''),16)),
 (c,'verify_'||left(replace(c::text,'-',''),16));
 perform set_config('request.jwt.claims',json_build_object('sub',a,'role','authenticated')::text,true);
 set local role authenticated;
 insert into public.user_preferences(user_id) values(a);
 insert into public.friendships(requester_id,addressee_id) values(a,b);
 update public.friendships set status='accepted' where requester_id=a and addressee_id=b;
 if exists(select 1 from public.friendships where requester_id=a and status='accepted') then raise exception 'Self acceptance'; end if;
 perform public.spark_record_visit(b);
 perform set_config('request.jwt.claims',json_build_object('sub',b,'role','authenticated')::text,true);
 if exists(select 1 from public.spark_visits where visitor_id=a) then raise exception 'Ghost leak'; end if;
 update public.friendships set status='accepted' where requester_id=a and addressee_id=b;
 if not spark_private.are_friends(a) then raise exception 'Friend acceptance failed'; end if;
 perform set_config('request.jwt.claims',json_build_object('sub',a,'role','authenticated')::text,true);
 update public.user_preferences set ghost_mode=false where user_id=a;
 perform public.spark_record_visit(b);
 -- Match the app: insert with minimal response, then read the authorized conversation.
 insert into public.conversations(member_a,member_b) values(least(a,b),greatest(a,b));
 select id into strict chat from public.conversations where member_a=least(a,b) and member_b=greatest(a,b);
 insert into public.messages(conversation_id,sender_id,body) values(chat,a,'Rollback verification');
 insert into public.call_sessions(conversation_id,caller_id,callee_id,offer) values(chat,a,b,'rollback-offer') returning id into call_id;
 perform set_config('request.jwt.claims',json_build_object('sub',c,'role','authenticated')::text,true);
 if exists(select 1 from public.messages where conversation_id=chat) then raise exception 'Chat leak'; end if;
 if exists(select 1 from public.call_sessions where id=call_id) then raise exception 'Call leak'; end if;
 perform set_config('request.jwt.claims',json_build_object('sub',b,'role','authenticated')::text,true);
 if not exists(select 1 from public.spark_visits where visitor_id=a) then raise exception 'Visible visit missing'; end if;
 if not exists(select 1 from public.messages where conversation_id=chat) then raise exception 'Recipient message missing'; end if;
 update public.call_sessions set status='accepted',answer='rollback-answer' where id=call_id;
 if not exists(select 1 from public.call_sessions where id=call_id and status='accepted') then raise exception 'Call answer failed'; end if;
 insert into public.blocks(blocker_id,blocked_id) values(b,a);
 if exists(select 1 from public.friendships where requester_id=a and addressee_id=b) then raise exception 'Friend block failure'; end if;
 if exists(select 1 from public.messages where conversation_id=chat) then raise exception 'Blocked chat leak'; end if;
 if exists(select 1 from public.call_sessions where id=call_id) then raise exception 'Blocked call leak'; end if;
 if exists(select 1 from public.spark_visits where visitor_id=a) then raise exception 'Blocked visit leak'; end if;
 reset role;
end $verify$;
rollback;
select 'PASS: 12 social authorization assertions; all fixtures rolled back' result;
