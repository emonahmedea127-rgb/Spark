-- Quality is an aggregate only; raw viewers remain private to the creator.
create function sparknew_v1_private.reel_quality(content_id uuid) returns numeric
language plpgsql stable security definer set search_path='' as $$
declare result numeric;
begin
 if auth.uid() is null or not exists(select 1 from public.sparknew_posts p where p.id=content_id and p.kind='reel' and not p.moderation_hidden and not sparknew_v1_private.blocked(p.author_id) and (p.author_id=auth.uid() or p.visibility='public' or (p.visibility='friends' and sparknew_v1_private.friend(p.author_id)))) then return 0; end if;
 select coalesce(30*avg(q.ratio)*count(*)/(count(*)+10.0)+15*avg(q.completed)*count(*)/(count(*)+10.0),0) into result
 from (select x.viewer_id,avg(least(x.watched_ms::numeric/x.media_duration_ms,1)) ratio,
 avg(case when x.watched_ms>=x.media_duration_ms*.9 then 1.0 else 0.0 end) completed
 from public.sparknew_reel_playbacks x where x.post_id=content_id and x.created_at>=now()-interval '28 days'
 group by x.viewer_id)q;
 return result;
end;$$;
revoke all on function sparknew_v1_private.reel_quality(uuid) from public,anon;
grant execute on function sparknew_v1_private.reel_quality(uuid) to authenticated;

alter table public.sparknew_posts add column moderation_hidden boolean not null default false;
create policy post_moderation_visibility on public.sparknew_posts as restrictive for select to authenticated using(not moderation_hidden);
alter table public.sparknew_reports add column status text not null default 'pending' check(status in ('pending','dismissed','hidden','restored'));
alter table public.sparknew_reports add column resolved_by uuid references public.sparknew_profiles(id);
alter table public.sparknew_reports add column resolved_at timestamptz;
create index sparknew_reports_status on public.sparknew_reports(status,created_at);
create index sparknew_reports_resolver on public.sparknew_reports(resolved_by);
revoke insert on public.sparknew_reports from authenticated;
grant insert(reporter_id,target_type,target_id,reason) on public.sparknew_reports to authenticated;
create policy reports_admin_read on public.sparknew_reports for select to authenticated using(exists(select 1 from public.sparknew_badge_admins a where a.user_id=(select auth.uid())));
create function sparknew_v1_private.review_report(report_id uuid,decision text) returns void
language plpgsql security definer set search_path='' as $$
declare r public.sparknew_reports;
begin
 if auth.uid() is null or not exists(select 1 from public.sparknew_badge_admins a where a.user_id=auth.uid()) then raise insufficient_privilege;end if;
 select * into r from public.sparknew_reports where id=report_id for update;
 if not found then raise exception 'Report not found';end if;
 if decision='dismiss' and r.status='pending' then
  update public.sparknew_reports set status='dismissed',resolved_by=auth.uid(),resolved_at=now() where id=report_id;
 elsif decision='hide' and r.target_type='post' and r.status='pending' then
  update public.sparknew_posts set moderation_hidden=true where id=r.target_id;
  if not found then raise exception 'Content no longer exists';end if;
  update public.sparknew_reports set status='hidden',resolved_by=auth.uid(),resolved_at=now() where id=report_id;
 elsif decision='restore' and r.target_type='post' and r.status='hidden' then
  update public.sparknew_posts set moderation_hidden=false where id=r.target_id;
  update public.sparknew_reports set status='restored',resolved_by=auth.uid(),resolved_at=now() where target_id=r.target_id and target_type='post' and status='hidden';
 else raise exception 'This action is not available for this report';end if;
end;$$;
revoke all on function sparknew_v1_private.review_report(uuid,text) from public,anon;
grant execute on function sparknew_v1_private.review_report(uuid,text) to authenticated;
create function public.sparknew_review_report(report_id uuid,decision text) returns void language sql security invoker set search_path='' as $$select sparknew_v1_private.review_report(report_id,decision);$$;
revoke all on function public.sparknew_review_report(uuid,text) from public,anon;
grant execute on function public.sparknew_review_report(uuid,text) to authenticated;

create or replace function public.sparknew_ranked_reels(page_offset integer default 0,page_size integer default 20)
returns setof public.sparknew_posts language sql stable security invoker set search_path='' as $$
 select p.* from public.sparknew_posts p
 where p.kind='reel' and p.media_type='video' and p.community_id is null
 and (p.visibility='public' or p.author_id=auth.uid() or (p.visibility='friends' and sparknew_v1_private.friend(p.author_id)))
 order by (20.0/(1.0+greatest(extract(epoch from(now()-p.created_at))/86400.0,0)/7)
 +sparknew_v1_private.reel_quality(p.id)
 +3*ln(1+(select count(*) from public.sparknew_reactions r where r.post_id=p.id))
 +5*ln(1+(select count(distinct c.author_id) from public.sparknew_comments c where c.post_id=p.id))) desc,
 p.created_at desc,p.id desc
 limit least(greatest(page_size,1),40) offset greatest(page_offset,0);
$$;
notify pgrst,'reload schema';
