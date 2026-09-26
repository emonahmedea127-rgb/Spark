begin;
create temp table spark_story_checks(name text,passed boolean);
grant select,insert on spark_story_checks to authenticated;
create function pg_temp.verify(ok boolean,label text) returns void language plpgsql as $$begin if ok is not true then raise exception 'FAILED: %',label;end if;insert into spark_story_checks values(label,true);end;$$;
create function pg_temp.deny(statement text,label text) returns void language plpgsql as $$begin begin execute statement;exception when insufficient_privilege or check_violation then perform pg_temp.verify(true,label);return;end;raise exception 'Unexpected permission: %',label;end;$$;
select set_config('test.a',gen_random_uuid()::text,true),set_config('test.b',gen_random_uuid()::text,true),set_config('test.c',gen_random_uuid()::text,true),set_config('test.story',gen_random_uuid()::text,true),set_config('test.private',gen_random_uuid()::text,true),set_config('test.post',gen_random_uuid()::text,true),set_config('test.event',gen_random_uuid()::text,true);
insert into auth.users(id,email,raw_user_meta_data) select current_setting('test.'||who)::uuid,'spark-story-'||current_setting('test.'||who)||'@example.invalid','{}'::jsonb from unnest(array['a','b','c'])who;
insert into public.sparknew_profiles(id,display_name) select current_setting('test.'||who)::uuid,who from unnest(array['a','b','c'])who;
insert into public.sparknew_posts(id,author_id,body,kind,visibility) values
(current_setting('test.story')::uuid,current_setting('test.a')::uuid,'Test story','story','public'),
(current_setting('test.private')::uuid,current_setting('test.a')::uuid,'Private story','story','private'),
(current_setting('test.post')::uuid,current_setting('test.a')::uuid,'Test post','post','public');
set local role authenticated;
select set_config('request.jwt.claims',json_build_object('sub',current_setting('test.b'),'role','authenticated')::text,true);
select public.sparknew_react_to_story(current_setting('test.story')::uuid,'love',current_setting('test.event')::uuid);
select public.sparknew_react_to_story(current_setting('test.story')::uuid,'love',current_setting('test.event')::uuid);
select pg_temp.verify((select count(*)=1 from public.sparknew_story_reaction_events where post_id=current_setting('test.story')::uuid),'Retry event is idempotent');
select public.sparknew_react_to_story(current_setting('test.story')::uuid,'love',gen_random_uuid());
select public.sparknew_react_to_story(current_setting('test.story')::uuid,'haha',gen_random_uuid());
select pg_temp.verify((select count(*)=3 from public.sparknew_story_reaction_events where post_id=current_setting('test.story')::uuid),'Repeated and different emoji taps persist');
select pg_temp.verify((select count(*)=1 from public.sparknew_reactions where post_id=current_setting('test.story')::uuid),'Post compatibility keeps one latest reaction');
select pg_temp.deny(format('select public.sparknew_react_to_story(%L::uuid,''love'',gen_random_uuid())',current_setting('test.private')),'Private story cannot be reacted to by outsider');
select pg_temp.deny(format('select public.sparknew_react_to_story(%L::uuid,''love'',gen_random_uuid())',current_setting('test.post')),'Nonstory cannot receive story events');
select pg_temp.deny(format('insert into public.sparknew_story_reaction_events(id,post_id,user_id,reaction) values(gen_random_uuid(),%L,%L,''love'')',current_setting('test.story'),current_setting('test.a')),'Cannot forge another user reaction');
select pg_temp.deny(format('select public.sparknew_react_to_story(%L::uuid,''invalid'',gen_random_uuid())',current_setting('test.story')),'Invalid emoji rejected');
select set_config('request.jwt.claims',json_build_object('sub',current_setting('test.c'),'role','authenticated')::text,true);
select pg_temp.verify((select count(*)=0 from public.sparknew_story_reaction_events where post_id=current_setting('test.story')::uuid),'Other viewer cannot read private reaction history');
select set_config('request.jwt.claims',json_build_object('sub',current_setting('test.a'),'role','authenticated')::text,true);
select pg_temp.verify((select total=2 from public.sparknew_story_reaction_totals(current_setting('test.story')::uuid) where reaction='love'),'Owner sees accurate repeated reaction count');
select name,passed from spark_story_checks;
rollback;
