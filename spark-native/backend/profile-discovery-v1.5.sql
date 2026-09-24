-- Rich profile fields have independent, server-enforced audiences.
create table public.sparknew_profile_details (
 id uuid primary key default gen_random_uuid(),
 owner_id uuid not null references public.sparknew_profiles(id) on delete cascade,
 kind text not null check(kind in ('category','ai_creator','city','hometown','birthday','relationship','family','gender','languages','link','community','offer','work','education','hobby','music','tv','film','game','sport','travel','social','phone','email','media_kit')),
 title text not null check(char_length(trim(title)) between 1 and 160),
 detail text not null default '' check(char_length(detail)<=500),
 visibility text not null default 'private' check(visibility in ('public','friends','private')),
 pinned boolean not null default false,
 created_at timestamptz not null default now()
);
create index sparknew_details_owner on public.sparknew_profile_details(owner_id,kind);
create index sparknew_details_public_match on public.sparknew_profile_details(kind,lower(trim(title)),owner_id) where visibility='public';
alter table public.sparknew_profile_details enable row level security;
revoke all on public.sparknew_profile_details from public,anon,authenticated;
grant select,insert,delete on public.sparknew_profile_details to authenticated;
grant update(title,detail,visibility,pinned) on public.sparknew_profile_details to authenticated;
create policy details_read on public.sparknew_profile_details for select to authenticated using (
 not sparknew_v1_private.blocked(owner_id) and
 (owner_id=(select auth.uid()) or visibility='public' or (visibility='friends' and sparknew_v1_private.friend(owner_id))));
create policy details_insert on public.sparknew_profile_details for insert to authenticated with check(owner_id=(select auth.uid()));
create policy details_update on public.sparknew_profile_details for update to authenticated using(owner_id=(select auth.uid())) with check(owner_id=(select auth.uid()));
create policy details_delete on public.sparknew_profile_details for delete to authenticated using(owner_id=(select auth.uid()));
create table public.sparknew_suggestion_dismissals (
 owner_id uuid references public.sparknew_profiles(id) on delete cascade,
 target_id uuid references public.sparknew_profiles(id) on delete cascade,
 primary key(owner_id,target_id),check(owner_id<>target_id)
);
create index sparknew_dismissal_target on public.sparknew_suggestion_dismissals(target_id);
alter table public.sparknew_suggestion_dismissals enable row level security;
revoke all on public.sparknew_suggestion_dismissals from public,anon,authenticated;
grant select,insert,delete on public.sparknew_suggestion_dismissals to authenticated;
create policy dismissals_own on public.sparknew_suggestion_dismissals for all to authenticated using(owner_id=(select auth.uid())) with check(owner_id=(select auth.uid()));
-- Only aggregate mutual counts leave this private helper. No arbitrary viewer ID,
-- private profile fields, or other people's friendship edges are exposed.
create function sparknew_v1_private.suggestions(page_size integer,page_offset integer)
returns table(id uuid,display_name text,avatar_path text,mutual_count bigint,reason text,rank_score bigint)
language sql stable security definer set search_path='' as $$
 with viewer as (select auth.uid() as uid),
 mine as (
  select case when f.sender_id=v.uid then f.receiver_id else f.sender_id end as id
  from public.sparknew_friendships f cross join viewer v
  where f.status='accepted' and v.uid in(f.sender_id,f.receiver_id)
 ), eligible as (
  select p.* from public.sparknew_profiles p cross join viewer v
  where v.uid is not null and p.id<>v.uid
  and not sparknew_v1_private.blocked(p.id)
  and not exists(select 1 from public.sparknew_friendships f where (f.sender_id=v.uid and f.receiver_id=p.id) or (f.receiver_id=v.uid and f.sender_id=p.id))
  and not exists(select 1 from public.sparknew_suggestion_dismissals d where d.owner_id=v.uid and d.target_id=p.id)
 ), mutuals as (
  select e.id,count(distinct m.id) as total from eligible e
  join public.sparknew_friendships f on f.status='accepted' and e.id in(f.sender_id,f.receiver_id)
  join mine m on m.id=case when f.sender_id=e.id then f.receiver_id else f.sender_id end
  where not sparknew_v1_private.blocked(m.id)
  and not exists(select 1 from public.sparknew_blocks b where (b.owner_id=e.id and b.target_id=m.id) or (b.owner_id=m.id and b.target_id=e.id))
  group by e.id
 ), matches as (
  select e.id,count(distinct d.kind) as total,
   bool_or(d.kind in ('city','hometown')) as place,
   bool_or(d.kind='education') as school,bool_or(d.kind='work') as workplace
  from eligible e join public.sparknew_profile_details d on d.owner_id=e.id and d.visibility='public'
  cross join viewer v
  where d.kind in('city','hometown','education','work','hobby','music','tv','film','game','sport','languages')
  and exists(select 1 from public.sparknew_profile_details own where own.owner_id=v.uid and own.visibility='public' and own.kind=d.kind and lower(trim(own.title))=lower(trim(d.title)))
  group by e.id
 )
 select e.id,e.display_name,e.avatar_path,coalesce(m.total,0),
 case when coalesce(m.total,0)>0 then m.total::text||case when m.total=1 then ' mutual friend' else ' mutual friends' end
 when a.school then 'Education in common' when a.workplace then 'Workplace in common'
 when a.place then 'Location in common' when coalesce(a.total,0)>0 then 'Shared interests' else 'Discover someone new' end,
 coalesce(m.total,0)*100+least(coalesce(a.total,0)*8,88)+case when e.avatar_path is not null then 2 else 0 end as score
 from eligible e left join mutuals m on m.id=e.id left join matches a on a.id=e.id
 order by score desc,e.id
 limit least(greatest(coalesce(page_size,20),1),50) offset least(greatest(coalesce(page_offset,0),0),10000);
$$;
revoke all on function sparknew_v1_private.suggestions(integer,integer) from public,anon;
grant execute on function sparknew_v1_private.suggestions(integer,integer) to authenticated;
create function public.sparknew_suggestions(page_size integer default 20,page_offset integer default 0)
returns table(id uuid,display_name text,avatar_path text,mutual_count bigint,reason text,rank_score bigint)
language sql stable security invoker set search_path='' as $$ select * from sparknew_v1_private.suggestions(page_size,page_offset); $$;
revoke all on function public.sparknew_suggestions(integer,integer) from public,anon;
grant execute on function public.sparknew_suggestions(integer,integer) to authenticated;
notify pgrst,'reload schema';
