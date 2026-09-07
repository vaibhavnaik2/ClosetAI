create table private.closetai_account_deletions (
  user_id uuid primary key references auth.users(id) on delete cascade,
  requested_at timestamptz not null default now()
);
alter table private.closetai_account_deletions enable row level security;
revoke all on private.closetai_account_deletions from public, anon, authenticated;

create function private.closetai_account_active()
returns boolean language sql stable security definer set search_path=''
as $$
  select exists(select 1 from auth.users where id=auth.uid())
    and not exists(select 1 from private.closetai_account_deletions where user_id=auth.uid());
$$;
revoke all on function private.closetai_account_active() from public, anon;
grant execute on function private.closetai_account_active() to authenticated;

create function private.begin_closetai_deletion()
returns boolean language plpgsql security definer set search_path=''
as $$
declare caller uuid:=auth.uid();
begin
  if caller is null or not exists(select 1 from auth.users where id=caller) then
    raise exception 'Authentication required' using errcode='28000';
  end if;
  insert into private.closetai_account_deletions(user_id) values(caller) on conflict(user_id) do nothing;
  return true;
end;
$$;
revoke all on function private.begin_closetai_deletion() from public, anon;
grant execute on function private.begin_closetai_deletion() to authenticated;

create function public.begin_closetai_deletion()
returns boolean language sql security invoker set search_path=''
as $$ select private.begin_closetai_deletion(); $$;
revoke all on function public.begin_closetai_deletion() from public, anon;
grant execute on function public.begin_closetai_deletion() to authenticated;

-- Existing ownership policies remain in place. This is an additional AND gate.
create policy closetai_storage_account_active on storage.objects
as restrictive for all to authenticated
using (bucket_id <> 'wardrobe-private' or (select private.closetai_account_active()))
with check (bucket_id <> 'wardrobe-private' or (select private.closetai_account_active()));
