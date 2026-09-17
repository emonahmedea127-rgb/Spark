-- Administrator-only integration check for Spark's own new development project.
-- All synthetic Auth identities and application rows are rolled back.
-- No emails are sent and no Storage objects are created or removed.
begin;
do $verify$
declare
  alice uuid := gen_random_uuid();
  bob uuid := gen_random_uuid();
  photo uuid := gen_random_uuid();
begin
  insert into auth.users(id) values(alice),(bob);
  insert into public.profiles(id,username,display_name) values
    (alice,'test_' || left(replace(alice::text,'-',''),16),'Rollback Alice'),
    (bob,'test_' || left(replace(bob::text,'-',''),16),'Rollback Bob');
  perform set_config('request.jwt.claims',json_build_object('sub',alice,'role','authenticated')::text,true);
  -- Synthetic post metadata is visible only inside this uncommitted test.
  insert into public.posts(id,author_id,image_path,caption)
    values(photo,alice,alice::text || '/' || photo::text || '.jpg','Rollback verification');
  set local role authenticated;
  if not exists(select 1 from public.posts where id=photo) then raise exception 'Owner read failed'; end if;
  perform set_config('request.jwt.claims',json_build_object('sub',bob,'role','authenticated')::text,true);
  if exists(select 1 from public.posts where id=photo) then raise exception 'Private post leaked'; end if;
  insert into public.follows(follower_id,following_id) values(bob,alice);
  if not exists(select 1 from public.follows where follower_id=bob and following_id=alice and status='pending') then raise exception 'Pending request failed'; end if;
  update public.follows set status='accepted' where follower_id=bob and following_id=alice;
  if exists(select 1 from public.follows where follower_id=bob and following_id=alice and status='accepted') then raise exception 'Requester self-approved'; end if;
  perform set_config('request.jwt.claims',json_build_object('sub',alice,'role','authenticated')::text,true);
  update public.follows set status='accepted' where follower_id=bob and following_id=alice;
  perform set_config('request.jwt.claims',json_build_object('sub',bob,'role','authenticated')::text,true);
  if not exists(select 1 from public.spark_feed where id=photo) then raise exception 'Approved feed read failed'; end if;
  insert into public.likes(user_id,post_id) values(bob,photo);
  insert into public.saves(user_id,post_id) values(bob,photo);
  if not exists(select 1 from public.spark_feed where id=photo and like_count=1 and liked and saved) then raise exception 'Reaction view failed'; end if;
  perform set_config('request.jwt.claims',json_build_object('sub',alice,'role','authenticated')::text,true);
  if exists(select 1 from public.saves where post_id=photo) then raise exception 'Private bookmark leaked'; end if;
  insert into public.blocks(blocker_id,blocked_id) values(alice,bob);
  if exists(select 1 from public.follows where follower_id=bob and following_id=alice) then raise exception 'Block cleanup failed'; end if;
  perform set_config('request.jwt.claims',json_build_object('sub',bob,'role','authenticated')::text,true);
  if exists(select 1 from public.profiles where id=alice) then raise exception 'Blocked profile leaked'; end if;
  if exists(select 1 from public.spark_feed where id=photo) then raise exception 'Blocked feed leaked'; end if;
  reset role;
  update public.profiles set deleting_at=now() where id=alice;
  perform set_config('request.jwt.claims',json_build_object('sub',alice,'role','authenticated')::text,true);
  set local role authenticated;
  if spark_private.active_user() then raise exception 'Deleted identity remains active'; end if;
  if exists(select 1 from public.posts where id=photo) then raise exception 'Tombstone access leaked'; end if;
  reset role;
end
$verify$;
rollback;
select 'PASS: 12 live authorization assertions; every fixture rolled back' as result;
