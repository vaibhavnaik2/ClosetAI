-- Preserve the existing RPC signature; callers cannot choose a more generous quota.
create schema if not exists private;
grant usage on schema private to authenticated;

create or replace function private.consume_closetai_rate_limit(p_bucket text)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
  caller uuid := auth.uid();
  ceiling integer;
  window_value timestamptz;
  count_value integer;
begin
  if caller is null or not exists (select 1 from auth.users where id = caller) then
    return false;
  end if;
  ceiling := case p_bucket
    when 'ai-analysis' then 300
    when 'ai-stylist' then 180
    when 'closet-search' then 180
    when 'url-import' then 30
    when 'drive-import' then 60
    else null end;
  if ceiling is null then return false; end if;
  window_value := to_timestamp(floor(extract(epoch from now()) / 3600) * 3600);
  insert into public.rate_limit_counters(user_id,bucket,window_start,request_count)
  values(caller,p_bucket,window_value,1)
  on conflict(user_id,bucket,window_start)
  do update set request_count = least(public.rate_limit_counters.request_count + 1, ceiling + 1)
  returning request_count into count_value;
  return count_value <= ceiling;
end;
$$;
revoke all on function private.consume_closetai_rate_limit(text) from public, anon;
grant execute on function private.consume_closetai_rate_limit(text) to authenticated;

create or replace function public.consume_rate_limit(p_bucket text, p_limit integer, p_window_seconds integer)
returns boolean
language sql
security invoker
set search_path = ''
as $$
  select private.consume_closetai_rate_limit(p_bucket);
$$;
revoke all on function public.consume_rate_limit(text,integer,integer) from public, anon;
grant execute on function public.consume_rate_limit(text,integer,integer) to authenticated;
-- RLS does not make caller-owned counters safe: clients must never reset them.
revoke insert, update, delete, truncate, references, trigger on public.rate_limit_counters from public, anon, authenticated;
