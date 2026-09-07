-- Run in a transaction, ending in ROLLBACK.
do $$
declare a uuid:=gen_random_uuid(); b uuid:=gen_random_uuid(); garment uuid; collection uuid; packing uuid;
begin
  insert into auth.users(id,email) values(a,a::text||'@example.invalid'),(b,b::text||'@example.invalid');
  insert into public.wardrobe_items(user_id,storage_path,image_sha256,name)
    select a,a::text||'/'||i||'.jpg',lpad(i::text,64,'0'),'Export fixture' from generate_series(1,1001) i;
  insert into public.wardrobe_items(user_id,storage_path,image_sha256,name)
    values(b,b::text||'/private.jpg',repeat('b',64),'Other account');
  select id into garment from public.wardrobe_items where user_id=a limit 1;
  insert into public.wardrobe_collections(user_id,name) values(a,'Test collection') returning id into collection;
  insert into public.wardrobe_collection_items(collection_id,item_id) values(collection,garment);
  insert into public.packing_lists(user_id,title) values(a,'Test trip') returning id into packing;
  insert into public.packing_list_items(packing_list_id,item_id) values(packing,garment);
  insert into public.connected_accounts(user_id,provider,refresh_ciphertext,access_ciphertext)
    values(a,'test','MUST-NOT-EXPORT','MUST-NOT-EXPORT');
  perform set_config('request.jwt.claim.sub',a::text,true);
end $$;
set local role authenticated;
do $$
declare exported jsonb;
begin
  exported:=public.export_closetai_account();
  if jsonb_array_length(exported->'data'->'wardrobe_items')<>1001 then raise exception 'Truncated or cross-account wardrobe export'; end if;
  if jsonb_array_length(exported->'data'->'wardrobe_collection_items')<>1 then raise exception 'Missing collection membership'; end if;
  if jsonb_array_length(exported->'data'->'packing_list_items')<>1 then raise exception 'Missing packing membership'; end if;
  if exported::text like '%MUST-NOT-EXPORT%' then raise exception 'OAuth token data leaked'; end if;
  if exported::text like '%Other account%' then raise exception 'Cross-account data leaked'; end if;
end $$;
reset role;
select 'export >1000 rows, memberships, account isolation and token exclusion passed' as verification;
