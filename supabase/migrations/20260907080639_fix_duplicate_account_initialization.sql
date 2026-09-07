-- Both existing AFTER INSERT triggers initialize these records. Make the legacy
-- initializer idempotent so it can coexist with the terms-aware initializer.
create or replace function public.handle_new_closetai_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles(id,email,display_name,timezone,locale)
  values(new.id,new.email,
    coalesce(new.raw_user_meta_data->>'display_name', split_part(coalesce(new.email,''),'@',1)),
    coalesce(new.raw_user_meta_data->>'timezone','UTC'),
    coalesce(new.raw_user_meta_data->>'locale','en'))
  on conflict(id) do update set
    display_name=coalesce(public.profiles.display_name,excluded.display_name),
    timezone=excluded.timezone,
    locale=excluded.locale;
  insert into public.user_preferences(user_id) values(new.id) on conflict(user_id) do nothing;
  insert into public.subscription_entitlements(user_id) values(new.id) on conflict(user_id) do nothing;
  return new;
end;
$$;
revoke all on function public.handle_new_closetai_user() from public, anon, authenticated;
