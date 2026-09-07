-- Run within a transaction after the migration, then ROLLBACK. No persistent fixtures.
do $$
declare fixture uuid := gen_random_uuid();
begin
  insert into auth.users(id,email,raw_user_meta_data) values(fixture, fixture::text || '@example.invalid', '{"terms_version":"test","privacy_version":"test","display_name":"Release fixture"}'::jsonb);
  if (select count(*) from public.profiles where id=fixture and accepted_terms_version='test' and display_name='Release fixture') <> 1 then
    raise exception 'Account initialization failed';
  end if;
  if (select count(*) from public.user_preferences where user_id=fixture) <> 1 or
     (select count(*) from public.subscription_entitlements where user_id=fixture) <> 1 then
    raise exception 'Preferences or entitlements initialization failed';
  end if;
  perform set_config('request.jwt.claim.sub', fixture::text, true);
end $$;
set local role authenticated;
do $$
declare i integer;
begin
  for i in 1..30 loop
    if public.consume_rate_limit('url-import',2147483647,1) is not true then
      raise exception 'Valid request % was denied',i;
    end if;
  end loop;
  if public.consume_rate_limit('url-import',2147483647,1) is not false then
    raise exception 'Caller-supplied limits bypassed the quota';
  end if;
  if public.consume_rate_limit('unknown-bucket',2147483647,1) is not false then
    raise exception 'Unknown bucket accepted';
  end if;
  begin
    delete from public.rate_limit_counters where user_id=auth.uid();
    raise exception 'Direct counter deletion was allowed';
  exception when insufficient_privilege then null;
  end;
  begin
    update public.rate_limit_counters set request_count=0 where user_id=auth.uid();
    raise exception 'Direct counter reset was allowed';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;
select 'quota, fixed-window, unknown-bucket, counter-delete and counter-update assertions passed' as verification;
