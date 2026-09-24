alter table public.sparknew_profile_details add column metadata jsonb not null default '{}'::jsonb;
alter table public.sparknew_profile_details add constraint sparknew_details_metadata_object check(jsonb_typeof(metadata)='object' and octet_length(metadata::text)<=4096);
alter table public.sparknew_profile_details add constraint sparknew_details_coordinates check(
 (not(metadata ? 'latitude') and not(metadata ? 'longitude')) or
 (jsonb_typeof(metadata->'latitude')='number' and jsonb_typeof(metadata->'longitude')='number'
 and (metadata->>'latitude')::numeric between -85.05112878 and 85.05112878 and (metadata->>'longitude')::numeric between -180 and 180
 and kind in('city','hometown','travel')));
grant update(metadata) on public.sparknew_profile_details to authenticated;
notify pgrst,'reload schema';
-- Explicitly reject a missing member of a coordinate pair.
alter table public.sparknew_profile_details drop constraint sparknew_details_coordinates;
alter table public.sparknew_profile_details add constraint sparknew_details_coordinates check((
 (not(metadata ? 'latitude') and not(metadata ? 'longitude')) or
 (jsonb_typeof(metadata->'latitude')='number' and jsonb_typeof(metadata->'longitude')='number'
 and (metadata->>'latitude')::numeric between -85.05112878 and 85.05112878 and (metadata->>'longitude')::numeric between -180 and 180
 and kind in('city','hometown','travel'))) is true);
