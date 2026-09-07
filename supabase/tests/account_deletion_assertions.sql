-- Run in a transaction and ROLLBACK.
do $$
declare fixture uuid:=gen_random_uuid();
begin
  insert into auth.users(id,email) values(fixture,fixture::text||'@example.invalid');
  perform set_config('request.jwt.claim.sub',fixture::text,true);
end $$;
set local role authenticated;
do $$
begin
  if private.closetai_account_active() is not true then raise exception 'New account unexpectedly blocked'; end if;
  if public.begin_closetai_deletion() is not true then raise exception 'Deletion request failed'; end if;
  if private.closetai_account_active() is not false then raise exception 'Deleting account retains storage access'; end if;
  if public.begin_closetai_deletion() is not true then raise exception 'Deletion request is not retryable'; end if;
  begin
    delete from private.closetai_account_deletions where user_id=auth.uid();
    raise exception 'User can remove deletion lock';
  exception when insufficient_privilege then null;
  end;
end $$;
reset role;
delete from auth.users where id=auth.uid();
set local role authenticated;
do $$ begin
  if private.closetai_account_active() is not false then raise exception 'Deleted user JWT retains storage access'; end if;
end $$;
reset role;
select 'active-account, deletion-lock, retry and deleted-token assertions passed' as verification;
