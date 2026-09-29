-- Run only after Firebase and the matching Edge/Vault webhook secret are configured.
create extension if not exists pg_net with schema extensions;
create extension if not exists pg_cron;
create or replace function sparknew_v1_private.dispatch_push() returns void
language plpgsql security invoker set search_path='' as $$
declare credential text;
begin
 select decrypted_secret into credential from vault.decrypted_secrets where name='spark_push_webhook_secret' limit 1;
 if credential is null or length(credential)<32 then return; end if;
 if not exists(select 1 from public.sparknew_push_queue where state in ('pending','processing') and available_at<=now() and expires_at>now()) then return; end if;
 perform net.http_post(url:='https://twywavuyghftkzsflfrf.supabase.co/functions/v1/spark-push',
  headers:=jsonb_build_object('Content-Type','application/json','X-Spark-Push-Secret',credential),body:='{}'::jsonb,timeout_milliseconds:=5000);
end $$;
revoke all on function sparknew_v1_private.dispatch_push() from public,anon,authenticated;
create or replace function sparknew_v1_private.dispatch_push_trigger() returns trigger
language plpgsql security definer set search_path='' as $$
begin
 -- Trigger-only. Keep delivery failures from aborting message creation.
 begin perform sparknew_v1_private.dispatch_push(); exception when others then null; end;
 return null;
end $$;
revoke all on function sparknew_v1_private.dispatch_push_trigger() from public,anon,authenticated;
drop trigger if exists sparknew_dispatch_push on public.sparknew_push_queue;
create trigger sparknew_dispatch_push after insert on public.sparknew_push_queue for each statement execute function sparknew_v1_private.dispatch_push_trigger();
select cron.schedule('spark-push-retry','* * * * *','select sparknew_v1_private.dispatch_push();');
