-- Ownership of a row must also constrain the objects it references.
create policy wear_references_owned on public.wear_events as restrictive for all to authenticated
using (true) with check (
  (item_id is null or exists(select 1 from public.wardrobe_items w where w.id=item_id and w.user_id=(select auth.uid())))
  and (outfit_id is null or exists(select 1 from public.outfits o where o.id=outfit_id and o.user_id=(select auth.uid())))
);
create policy feedback_outfit_owned on public.outfit_feedback as restrictive for all to authenticated
using (true) with check (outfit_id is null or exists(select 1 from public.outfits o where o.id=outfit_id and o.user_id=(select auth.uid())));
create policy planned_outfit_owned on public.outfit_plans as restrictive for all to authenticated
using (true) with check (outfit_id is null or exists(select 1 from public.outfits o where o.id=outfit_id and o.user_id=(select auth.uid())));
create policy outfit_items_owned on public.outfits as restrictive for all to authenticated
using (true) with check (not exists(
  select 1 from unnest(item_ids) as garment_id
  where not exists(select 1 from public.wardrobe_items w where w.id=garment_id and w.user_id=(select auth.uid()))
));
create policy import_asset_job_owned on public.import_assets as restrictive for all to authenticated
using (true) with check (exists(select 1 from public.import_jobs j where j.id=job_id and j.user_id=(select auth.uid())));

create function public.record_item_worn(p_item_id uuid,p_event_id uuid)
returns boolean language plpgsql security invoker set search_path=''
as $$
declare caller uuid:=auth.uid(); inserted uuid;
begin
  if caller is null or p_event_id is null then return false; end if;
  perform 1 from public.wardrobe_items where id=p_item_id and user_id=caller for update;
  if not found then return false; end if;
  insert into public.wear_events(id,user_id,item_id,worn_at,context)
  values(p_event_id,caller,p_item_id,now(),jsonb_build_object('source','closetai_android'))
  on conflict(id) do nothing returning id into inserted;
  if inserted is null then
    return exists(select 1 from public.wear_events where id=p_event_id and user_id=caller and item_id=p_item_id);
  end if;
  update public.wardrobe_items set wear_count=wear_count+1,last_worn_at=now(),updated_at=now()
    where id=p_item_id and user_id=caller;
  return true;
end;
$$;
revoke all on function public.record_item_worn(uuid,uuid) from public,anon;
grant execute on function public.record_item_worn(uuid,uuid) to authenticated;
