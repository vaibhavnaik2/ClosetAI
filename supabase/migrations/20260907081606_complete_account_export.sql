-- A STABLE invoker function uses the calling statement's snapshot and RLS.
-- Returning one JSON object avoids PostgREST's per-table result row limit.
create or replace function public.export_closetai_account()
returns jsonb
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  caller uuid := auth.uid();
  table_name text;
  rows_value jsonb;
  result_value jsonb := '{}'::jsonb;
begin
  if caller is null then raise exception 'Authentication required' using errcode='28000'; end if;
  foreach table_name in array array[
    'profiles','user_preferences','subscription_entitlements','wardrobe_items',
    'outfits','outfit_feedback','wear_events','import_jobs','import_assets',
    'wardrobe_filter_presets','wardrobe_collections','packing_lists','outfit_plans',
    'app_devices','audit_events'
  ] loop
    execute format('select coalesce(jsonb_agg(to_jsonb(t)), ''[]''::jsonb) from public.%I t where %I=$1',
      table_name,case when table_name='profiles' then 'id' else 'user_id' end)
    into rows_value using caller;
    result_value := result_value || jsonb_build_object(table_name,rows_value);
  end loop;
  select coalesce(jsonb_agg(to_jsonb(i)), '[]'::jsonb) into rows_value
    from public.wardrobe_collection_items i
    join public.wardrobe_collections c on c.id=i.collection_id where c.user_id=caller;
  result_value := result_value || jsonb_build_object('wardrobe_collection_items',rows_value);
  select coalesce(jsonb_agg(to_jsonb(i)), '[]'::jsonb) into rows_value
    from public.packing_list_items i
    join public.packing_lists p on p.id=i.packing_list_id where p.user_id=caller;
  result_value := result_value || jsonb_build_object('packing_list_items',rows_value);
  select coalesce(jsonb_agg(jsonb_build_object('provider',provider,'account_email',account_email,
    'scopes',scopes,'created_at',created_at,'updated_at',updated_at)), '[]'::jsonb)
    into rows_value from public.connected_accounts where user_id=caller;
  result_value := result_value || jsonb_build_object('connected_accounts',rows_value);
  return jsonb_build_object('format','ClosetAI Export v2','generated_at',now(),
    'user',jsonb_build_object('id',caller),'data',result_value);
end;
$$;
revoke all on function public.export_closetai_account() from public, anon;
grant execute on function public.export_closetai_account() to authenticated;
