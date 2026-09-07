-- Run within a transaction and ROLLBACK. Fixtures never persist.
do $$
declare a uuid:=gen_random_uuid(); b uuid:=gen_random_uuid(); own_item uuid; other_item uuid; other_outfit uuid;
begin
  insert into auth.users(id,email) values(a,a::text||'@example.invalid'),(b,b::text||'@example.invalid');
  insert into public.wardrobe_items(user_id,storage_path,image_sha256) values(a,a::text||'/test.jpg',repeat('a',64)) returning id into own_item;
  insert into public.wardrobe_items(user_id,storage_path,image_sha256) values(b,b::text||'/test.jpg',repeat('b',64)) returning id into other_item;
  insert into public.outfits(user_id,title,item_ids) values(b,'Other account',array[other_item]) returning id into other_outfit;
  perform set_config('request.jwt.claim.sub',a::text,true);
  perform set_config('test.own_item',own_item::text,true);
  perform set_config('test.other_item',other_item::text,true);
  perform set_config('test.other_outfit',other_outfit::text,true);
end $$;
set local role authenticated;
do $$
declare event_id uuid:=gen_random_uuid(); own_item uuid:=current_setting('test.own_item')::uuid; foreign_item uuid:=current_setting('test.other_item')::uuid; foreign_outfit uuid:=current_setting('test.other_outfit')::uuid;
begin
  if not public.record_item_worn(own_item,event_id) then raise exception 'Own wear rejected'; end if;
  if not public.record_item_worn(own_item,event_id) then raise exception 'Idempotent retry rejected'; end if;
  if (select wear_count from public.wardrobe_items where id=own_item)<>1 then raise exception 'Wear count is not exactly once'; end if;
  if public.record_item_worn(foreign_item,gen_random_uuid()) then raise exception 'Foreign wear accepted'; end if;
  begin
    insert into public.wear_events(user_id,item_id) values(auth.uid(),foreign_item);
    raise exception 'Foreign wear reference accepted';
  exception when insufficient_privilege then null; end;
  begin
    insert into public.outfits(user_id,title,item_ids) values(auth.uid(),'Invalid',array[foreign_item]);
    raise exception 'Foreign outfit item accepted';
  exception when insufficient_privilege then null; end;
  begin
    insert into public.outfit_plans(user_id,outfit_id,planned_for) values(auth.uid(),foreign_outfit,now());
    raise exception 'Foreign planned outfit accepted';
  exception when insufficient_privilege then null; end;
  begin
    insert into public.outfit_feedback(user_id,outfit_id,verdict) values(auth.uid(),foreign_outfit,'worn');
    raise exception 'Foreign feedback accepted';
  exception when insufficient_privilege then null; end;
end $$;
reset role;
select 'idempotent wear counts and cross-account wardrobe references passed' as verification;
