begin;
create temp table spark_discovery_checks(name text,passed boolean);
grant select,insert on spark_discovery_checks to authenticated;
create function pg_temp.verify(ok boolean,label text) returns void language plpgsql as $$begin
 if ok is not true then raise exception 'FAILED: %',label;end if;
 insert into spark_discovery_checks values(label,true);
end;$$;
select set_config('test.viewer',gen_random_uuid()::text,true),set_config('test.friend',gen_random_uuid()::text,true),set_config('test.mutual',gen_random_uuid()::text,true),set_config('test.shared',gen_random_uuid()::text,true),set_config('test.stranger',gen_random_uuid()::text,true);
insert into auth.users(id,email,raw_user_meta_data) select current_setting('test.'||who)::uuid,'spark-discovery-'||current_setting('test.'||who)||'@example.invalid','{}'::jsonb from unnest(array['viewer','friend','mutual','shared','stranger'])who;
insert into public.sparknew_profiles(id,display_name) select current_setting('test.'||who)::uuid,who from unnest(array['viewer','friend','mutual','shared','stranger'])who;
insert into public.sparknew_friendships(sender_id,receiver_id,status) values
(current_setting('test.viewer')::uuid,current_setting('test.friend')::uuid,'accepted'),
(current_setting('test.friend')::uuid,current_setting('test.mutual')::uuid,'accepted');
insert into public.sparknew_profile_details(owner_id,kind,title,visibility,pinned) values
(current_setting('test.viewer')::uuid,'city','Spark Test City','public',true),
(current_setting('test.viewer')::uuid,'work','Secret employer','private',false),
(current_setting('test.shared')::uuid,'city','  SPARK test CITY  ','public',false),
(current_setting('test.stranger')::uuid,'city','Spark Test City','private',true),
(current_setting('test.stranger')::uuid,'work','Secret employer','public',false),
(current_setting('test.friend')::uuid,'phone','private phone','private',true),
(current_setting('test.friend')::uuid,'hobby','friends hobby','friends',true);
set local role authenticated;
select set_config('request.jwt.claims',json_build_object('sub',current_setting('test.viewer'),'role','authenticated')::text,true);
select pg_temp.verify((select count(*)=2 from public.sparknew_profile_details where owner_id=current_setting('test.viewer')::uuid),'Owner sees own private and public fields');
select pg_temp.verify((select count(*)=1 from public.sparknew_profile_details where owner_id=current_setting('test.friend')::uuid),'Friend sees friends-only but not private phone');
select pg_temp.verify((select count(*)=1 from public.sparknew_profile_details where owner_id=current_setting('test.stranger')::uuid),'Pinning never exposes a private detail');
select pg_temp.verify((select count(*)=0 from public.sparknew_friendships where sender_id=current_setting('test.friend')::uuid and receiver_id=current_setting('test.mutual')::uuid),'Ranking does not expose other friendship edges');
select pg_temp.verify((select mutual_count=1 and rank_score=100 and reason='1 mutual friend' from public.sparknew_suggestions(50,0) where id=current_setting('test.mutual')::uuid),'Mutual-friend score and count are accurate');
select pg_temp.verify((select rank_score=8 and reason='Location in common' from public.sparknew_suggestions(50,0) where id=current_setting('test.shared')::uuid),'Public matching normalises case and spaces');
select pg_temp.verify((select rank_score=0 and reason='Discover someone new' from public.sparknew_suggestions(50,0) where id=current_setting('test.stranger')::uuid),'Private fields from either side never affect ranking');
select pg_temp.verify((select count(*)=0 from public.sparknew_suggestions(50,0) where id in(current_setting('test.viewer')::uuid,current_setting('test.friend')::uuid)),'Self and existing friends are excluded');
select pg_temp.verify((select id=current_setting('test.mutual')::uuid from public.sparknew_suggestions(1,0)),'Mutual friend ranks before public profile match');
select pg_temp.verify((select id=current_setting('test.shared')::uuid from public.sparknew_suggestions(1,1)),'Pagination preserves ranked ordering');
insert into public.sparknew_friendships(sender_id,receiver_id) values(current_setting('test.viewer')::uuid,current_setting('test.shared')::uuid);
select pg_temp.verify((select count(*)=0 from public.sparknew_suggestions(50,0) where id=current_setting('test.shared')::uuid),'Outgoing pending request excluded');
delete from public.sparknew_friendships where sender_id=current_setting('test.viewer')::uuid and receiver_id=current_setting('test.shared')::uuid;
select set_config('request.jwt.claims',json_build_object('sub',current_setting('test.shared'),'role','authenticated')::text,true);
insert into public.sparknew_friendships(sender_id,receiver_id) values(current_setting('test.shared')::uuid,current_setting('test.viewer')::uuid);
select set_config('request.jwt.claims',json_build_object('sub',current_setting('test.viewer'),'role','authenticated')::text,true);
select pg_temp.verify((select count(*)=0 from public.sparknew_suggestions(50,0) where id=current_setting('test.shared')::uuid),'Incoming pending request excluded');
insert into public.sparknew_suggestion_dismissals(owner_id,target_id) values(current_setting('test.viewer')::uuid,current_setting('test.stranger')::uuid);
select pg_temp.verify((select count(*)=0 from public.sparknew_suggestions(50,0) where id=current_setting('test.stranger')::uuid),'Dismissal persists across ranked loads');
insert into public.sparknew_blocks(owner_id,target_id) values(current_setting('test.viewer')::uuid,current_setting('test.friend')::uuid);
select pg_temp.verify((select count(*)=0 from public.sparknew_profile_details where owner_id=current_setting('test.friend')::uuid),'Blocked profile details are hidden');
select pg_temp.verify((select mutual_count=0 from public.sparknew_suggestions(50,0) where id=current_setting('test.mutual')::uuid),'Blocked mutual friend contributes no count');
select set_config('request.jwt.claims',json_build_object('sub',current_setting('test.mutual'),'role','authenticated')::text,true);
insert into public.sparknew_blocks(owner_id,target_id) values(current_setting('test.mutual')::uuid,current_setting('test.viewer')::uuid);
select set_config('request.jwt.claims',json_build_object('sub',current_setting('test.viewer'),'role','authenticated')::text,true);
select pg_temp.verify((select count(*)=0 from public.sparknew_suggestions(50,0) where id=current_setting('test.mutual')::uuid),'Reverse block removes candidate');
-- Attempts to alter someone else's visible detail affect zero rows.
update public.sparknew_profile_details set title='tampered' where owner_id=current_setting('test.stranger')::uuid;
select pg_temp.verify((select title='Secret employer' from public.sparknew_profile_details where owner_id=current_setting('test.stranger')::uuid),'Cannot edit another profile');
select set_config('request.jwt.claims','{}',true);
select pg_temp.verify((select count(*)=0 from public.sparknew_suggestions(50,0)),'Missing identity cannot query suggestions');
reset role;
select pg_temp.verify(not has_function_privilege('anon','public.sparknew_suggestions(integer,integer)','execute'),'Anonymous callers have no ranking access');
select pg_temp.verify((select bool_and(relrowsecurity) from pg_class where relname in('sparknew_profile_details','sparknew_suggestion_dismissals')),'New tables enforce RLS');
select count(*) as checks_passed,bool_and(passed) as all_passed from spark_discovery_checks;
rollback;
