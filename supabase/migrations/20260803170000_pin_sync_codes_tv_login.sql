-- Profile PIN mutations, device sync-codes/linking, and TV QR login sessions.

-- ---------------------------------------------------------------------------
-- Sync codes
-- ---------------------------------------------------------------------------

create table public.sync_codes (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    code text not null,
    pin_hash text not null,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    expires_at timestamptz not null default 'infinity'::timestamptz
);

create unique index sync_codes_active_code_uidx
    on public.sync_codes (code)
    where is_active;

create index sync_codes_owner_created_idx
    on public.sync_codes (owner_id, created_at desc);

create trigger sync_codes_set_updated_at
before update on public.sync_codes
for each row execute function public.set_updated_at();

alter table public.sync_codes enable row level security;
revoke all on table public.sync_codes from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- TV login sessions
-- ---------------------------------------------------------------------------

create table public.tv_login_sessions (
    id uuid primary key default gen_random_uuid(),
    code text not null,
    device_nonce text not null,
    device_name text,
    redirect_base_url text not null,
    status text not null
        check (status in ('pending', 'approved', 'used', 'expired', 'cancelled')),
    approved_user_id uuid references auth.users(id) on delete set null,
    expires_at timestamptz not null,
    poll_interval_seconds integer not null default 3
        check (poll_interval_seconds between 1 and 60),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (code)
);

create unique index tv_login_sessions_code_nonce_uidx
    on public.tv_login_sessions (code, device_nonce);

create index tv_login_sessions_status_expires_idx
    on public.tv_login_sessions (status, expires_at);

create trigger tv_login_sessions_set_updated_at
before update on public.tv_login_sessions
for each row execute function public.set_updated_at();

alter table public.tv_login_sessions enable row level security;
revoke all on table public.tv_login_sessions from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- PIN helpers / RPCs
-- ---------------------------------------------------------------------------

create or replace function public.assert_profile_pin_format(p_pin text)
returns void
language plpgsql
immutable
security definer
set search_path = ''
as $$
begin
    if p_pin is null or p_pin !~ '^[0-9]{4,8}$' then
        raise exception 'PIN must be 4-8 digits' using errcode = '22023';
    end if;
end;
$$;

create or replace function public.set_profile_pin(
    p_profile_id integer,
    p_pin text,
    p_current_pin text default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.get_sync_owner();
    v_enabled boolean;
    v_hash text;
begin
    if auth.uid() is null or v_owner_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;
    -- Only the account owner may mutate PIN state (not linked devices).
    if auth.uid() <> v_owner_id then
        raise exception 'Only the account owner can manage profile PINs'
            using errcode = '42501';
    end if;
    if p_profile_id is null or p_profile_id not between 1 and 6 then
        raise exception 'Invalid p_profile_id' using errcode = '22023';
    end if;
    perform public.assert_profile_pin_format(p_pin);

    select lock.pin_enabled, lock.pin_hash
    into v_enabled, v_hash
    from public.profile_locks as lock
    where lock.user_id = v_owner_id
      and lock.profile_index = p_profile_id;

    if not found then
        raise exception 'Profile does not exist' using errcode = '22023';
    end if;

    if coalesce(v_enabled, false) then
        if p_current_pin is null or length(trim(p_current_pin)) = 0 then
            raise exception 'Current PIN is required' using errcode = 'P0001';
        end if;
        if v_hash is null
            or v_hash <> extensions.crypt(p_current_pin, v_hash) then
            raise exception 'Current PIN is incorrect' using errcode = 'P0001';
        end if;
    end if;

    update public.profile_locks as lock
    set pin_enabled = true,
        pin_hash = extensions.crypt(p_pin, extensions.gen_salt('bf')),
        failed_attempts = 0,
        pin_locked_until = null
    where lock.user_id = v_owner_id
      and lock.profile_index = p_profile_id;
end;
$$;

create or replace function public.clear_profile_pin(
    p_profile_id integer,
    p_current_pin text default null
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.get_sync_owner();
    v_enabled boolean;
    v_hash text;
begin
    if auth.uid() is null or v_owner_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;
    if auth.uid() <> v_owner_id then
        raise exception 'Only the account owner can manage profile PINs'
            using errcode = '42501';
    end if;
    if p_profile_id is null or p_profile_id not between 1 and 6 then
        raise exception 'Invalid p_profile_id' using errcode = '22023';
    end if;

    select lock.pin_enabled, lock.pin_hash
    into v_enabled, v_hash
    from public.profile_locks as lock
    where lock.user_id = v_owner_id
      and lock.profile_index = p_profile_id;

    if not found then
        raise exception 'Profile does not exist' using errcode = '22023';
    end if;

    if coalesce(v_enabled, false) then
        if p_current_pin is null or length(trim(p_current_pin)) = 0 then
            raise exception 'Current PIN is required' using errcode = 'P0001';
        end if;
        if v_hash is null
            or v_hash <> extensions.crypt(p_current_pin, v_hash) then
            raise exception 'Current PIN is incorrect' using errcode = 'P0001';
        end if;
    end if;

    update public.profile_locks as lock
    set pin_enabled = false,
        pin_hash = null,
        failed_attempts = 0,
        pin_locked_until = null
    where lock.user_id = v_owner_id
      and lock.profile_index = p_profile_id;
end;
$$;

create or replace function public.verify_profile_pin(
    p_profile_id integer,
    p_pin text
)
returns table (
    unlocked boolean,
    retry_after_seconds integer
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.get_sync_owner();
    v_enabled boolean;
    v_hash text;
    v_locked_until timestamptz;
    v_failed integer;
    v_retry integer := 0;
begin
    if auth.uid() is null or v_owner_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;
    if p_profile_id is null or p_profile_id not between 1 and 6 then
        raise exception 'Invalid p_profile_id' using errcode = '22023';
    end if;
    perform public.assert_profile_pin_format(p_pin);

    select lock.pin_enabled, lock.pin_hash, lock.pin_locked_until, lock.failed_attempts
    into v_enabled, v_hash, v_locked_until, v_failed
    from public.profile_locks as lock
    where lock.user_id = v_owner_id
      and lock.profile_index = p_profile_id;

    if not found or not coalesce(v_enabled, false) then
        return query select true, 0;
        return;
    end if;

    if v_locked_until is not null and v_locked_until > now() then
        v_retry := greatest(ceil(extract(epoch from (v_locked_until - now())))::integer, 1);
        return query select false, v_retry;
        return;
    end if;

    if v_hash is not null and v_hash = extensions.crypt(p_pin, v_hash) then
        update public.profile_locks as lock
        set failed_attempts = 0,
            pin_locked_until = null
        where lock.user_id = v_owner_id
          and lock.profile_index = p_profile_id;
        return query select true, 0;
        return;
    end if;

    v_failed := coalesce(v_failed, 0) + 1;
    if v_failed >= 5 then
        v_locked_until := now() + interval '5 minutes';
        v_retry := 300;
        v_failed := 0;
    end if;

    update public.profile_locks as lock
    set failed_attempts = v_failed,
        pin_locked_until = v_locked_until
    where lock.user_id = v_owner_id
      and lock.profile_index = p_profile_id;

    return query select false, coalesce(v_retry, 0);
end;
$$;

-- ---------------------------------------------------------------------------
-- Sync code / linking RPCs
-- ---------------------------------------------------------------------------

create or replace function public.generate_sync_code(p_pin text)
returns table (code text)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_existing_code text;
    v_new_code text;
    v_pin_hash text;
begin
    if v_user_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;
    perform public.assert_profile_pin_format(p_pin);

    select sc.code
    into v_existing_code
    from public.sync_codes as sc
    where sc.owner_id = v_user_id
      and sc.is_active
      and sc.expires_at > now()
    order by sc.created_at desc
    limit 1;

    v_pin_hash := extensions.crypt(p_pin, extensions.gen_salt('bf'));

    if v_existing_code is not null then
        update public.sync_codes as sc
        set pin_hash = v_pin_hash
        where sc.owner_id = v_user_id
          and sc.code = v_existing_code;
        return query select v_existing_code;
        return;
    end if;

    v_new_code := upper(
        substr(md5(random()::text || clock_timestamp()::text), 1, 4) || '-' ||
        substr(md5(random()::text || clock_timestamp()::text), 5, 4) || '-' ||
        substr(md5(random()::text || clock_timestamp()::text), 9, 4) || '-' ||
        substr(md5(random()::text || clock_timestamp()::text), 13, 4) || '-' ||
        substr(md5(random()::text || clock_timestamp()::text), 17, 4)
    );

    insert into public.sync_codes (owner_id, code, pin_hash)
    values (v_user_id, v_new_code, v_pin_hash);

    return query select v_new_code;
end;
$$;

create or replace function public.get_sync_code(p_pin text)
returns table (code text)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_existing_code text;
    v_existing_pin_hash text;
begin
    if v_user_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;
    perform public.assert_profile_pin_format(p_pin);

    select sc.code, sc.pin_hash
    into v_existing_code, v_existing_pin_hash
    from public.sync_codes as sc
    where sc.owner_id = v_user_id
      and sc.is_active
      and sc.expires_at > now()
    order by sc.created_at desc
    limit 1;

    if v_existing_code is null then
        raise exception 'No sync code found. Generate one first.' using errcode = 'P0001';
    end if;

    if v_existing_pin_hash is null
        or v_existing_pin_hash <> extensions.crypt(p_pin, v_existing_pin_hash) then
        raise exception 'Incorrect PIN' using errcode = 'P0001';
    end if;

    return query select v_existing_code;
end;
$$;

create or replace function public.claim_sync_code(
    p_code text,
    p_pin text,
    p_device_name text default null
)
returns table (
    result_owner_id uuid,
    success boolean,
    message text
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_caller_id uuid := auth.uid();
    v_owner_id uuid;
    v_pin_hash text;
    v_device_name text;
begin
    if v_caller_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;
    perform public.assert_profile_pin_format(p_pin);

    select sc.owner_id, sc.pin_hash
    into v_owner_id, v_pin_hash
    from public.sync_codes as sc
    where sc.code = upper(trim(coalesce(p_code, '')))
      and sc.is_active
      and sc.expires_at > now()
    limit 1;

    if v_owner_id is null then
        return query select null::uuid, false, 'Sync code not found'::text;
        return;
    end if;

    if v_owner_id = v_caller_id then
        return query select null::uuid, false, 'Cannot link a device to itself'::text;
        return;
    end if;

    if v_pin_hash is null
        or v_pin_hash <> extensions.crypt(p_pin, v_pin_hash) then
        return query select null::uuid, false, 'Incorrect PIN'::text;
        return;
    end if;

    if exists (
        select 1
        from public.linked_devices as linked
        where linked.device_user_id = v_caller_id
          and linked.owner_id <> v_owner_id
    ) then
        return query select null::uuid, false, 'Device is already linked to another account'::text;
        return;
    end if;

    v_device_name := nullif(left(trim(coalesce(p_device_name, '')), 120), '');

    insert into public.linked_devices (owner_id, device_user_id, device_name)
    values (v_owner_id, v_caller_id, v_device_name)
    on conflict (device_user_id) do update
    set device_name = coalesce(excluded.device_name, public.linked_devices.device_name),
        linked_at = now()
    where public.linked_devices.owner_id = excluded.owner_id;

    return query select v_owner_id, true, 'Device linked successfully'::text;
end;
$$;

create or replace function public.unlink_device(p_device_user_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_caller_id uuid := auth.uid();
begin
    if v_caller_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;
    if p_device_user_id is null then
        raise exception 'p_device_user_id is required' using errcode = '22023';
    end if;

    delete from public.linked_devices as linked
    where (linked.owner_id = v_caller_id and linked.device_user_id = p_device_user_id)
       or (linked.device_user_id = v_caller_id and linked.device_user_id = p_device_user_id);
end;
$$;

-- ---------------------------------------------------------------------------
-- TV login RPCs
-- ---------------------------------------------------------------------------

create or replace function public.start_tv_login_session(
    p_device_nonce text,
    p_redirect_base_url text,
    p_device_name text default null
)
returns table (
    code text,
    web_url text,
    expires_at timestamptz,
    poll_interval_seconds integer
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_code text;
    v_nonce text := nullif(trim(coalesce(p_device_nonce, '')), '');
    v_redirect text := nullif(trim(coalesce(p_redirect_base_url, '')), '');
    v_expires timestamptz := now() + interval '10 minutes';
    v_web_url text;
    v_sep text;
begin
    if v_nonce is null or length(v_nonce) < 8 or length(v_nonce) > 128 then
        raise exception 'Invalid p_device_nonce' using errcode = '22023';
    end if;
    if v_redirect is null
        or v_redirect !~* '^https?://' then
        raise exception 'Invalid p_redirect_base_url' using errcode = '22023';
    end if;

    v_code := upper(
        substr(md5(random()::text || clock_timestamp()::text || v_nonce), 1, 4)
        || '-' ||
        substr(md5(random()::text || clock_timestamp()::text || v_nonce), 5, 4)
    );

    v_sep := case when position('?' in v_redirect) > 0 then '&' else '?' end;
    v_web_url := v_redirect
        || v_sep
        || 'code=' || v_code
        || '&nonce=' || v_nonce;

    insert into public.tv_login_sessions (
        code,
        device_nonce,
        device_name,
        redirect_base_url,
        status,
        expires_at,
        poll_interval_seconds
    )
    values (
        v_code,
        v_nonce,
        nullif(left(trim(coalesce(p_device_name, '')), 120), ''),
        v_redirect,
        'pending',
        v_expires,
        3
    );

    return query select v_code, v_web_url, v_expires, 3;
end;
$$;

create or replace function public.poll_tv_login_session(
    p_code text,
    p_device_nonce text
)
returns table (
    status text,
    expires_at timestamptz,
    poll_interval_seconds integer
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_status text;
    v_expires timestamptz;
    v_poll integer;
begin
    select session.status, session.expires_at, session.poll_interval_seconds
    into v_status, v_expires, v_poll
    from public.tv_login_sessions as session
    where session.code = upper(trim(coalesce(p_code, '')))
      and session.device_nonce = trim(coalesce(p_device_nonce, ''))
    limit 1;

    if not found then
        return query select 'expired'::text, now(), 3;
        return;
    end if;

    if v_status = 'pending' and v_expires <= now() then
        update public.tv_login_sessions as session
        set status = 'expired'
        where session.code = upper(trim(coalesce(p_code, '')))
          and session.device_nonce = trim(coalesce(p_device_nonce, ''))
          and session.status = 'pending';
        v_status := 'expired';
    end if;

    return query select v_status, v_expires, coalesce(v_poll, 3);
end;
$$;

create or replace function public.approve_tv_login_session(
    p_code text,
    p_device_nonce text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_status text;
    v_expires timestamptz;
begin
    if v_user_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;

    select session.status, session.expires_at
    into v_status, v_expires
    from public.tv_login_sessions as session
    where session.code = upper(trim(coalesce(p_code, '')))
      and session.device_nonce = trim(coalesce(p_device_nonce, ''))
    for update;

    if not found then
        raise exception 'TV login session not found' using errcode = 'P0001';
    end if;
    if v_expires <= now() then
        update public.tv_login_sessions
        set status = 'expired'
        where code = upper(trim(coalesce(p_code, '')))
          and device_nonce = trim(coalesce(p_device_nonce, ''));
        raise exception 'TV login session expired' using errcode = 'P0001';
    end if;
    if v_status <> 'pending' then
        raise exception 'TV login session is not pending' using errcode = 'P0001';
    end if;

    update public.tv_login_sessions
    set status = 'approved',
        approved_user_id = v_user_id
    where code = upper(trim(coalesce(p_code, '')))
      and device_nonce = trim(coalesce(p_device_nonce, ''));
end;
$$;

-- Called by the tv-logins-exchange Edge Function with the service role.
create or replace function public.consume_tv_login_session(
    p_code text,
    p_device_nonce text
)
returns table (
    approved_user_id uuid
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_status text;
    v_expires timestamptz;
    v_user_id uuid;
begin
    -- Restrict to service_role (Edge Function). auth.role() is set by PostgREST/GoTrue.
    if auth.role() is distinct from 'service_role' then
        raise exception 'service_role required' using errcode = '42501';
    end if;

    select session.status, session.expires_at, session.approved_user_id
    into v_status, v_expires, v_user_id
    from public.tv_login_sessions as session
    where session.code = upper(trim(coalesce(p_code, '')))
      and session.device_nonce = trim(coalesce(p_device_nonce, ''))
    for update;

    if not found then
        raise exception 'TV login session not found' using errcode = 'P0001';
    end if;
    if v_expires <= now() then
        update public.tv_login_sessions
        set status = 'expired'
        where code = upper(trim(coalesce(p_code, '')))
          and device_nonce = trim(coalesce(p_device_nonce, ''));
        raise exception 'TV login session expired' using errcode = 'P0001';
    end if;
    if v_status <> 'approved' or v_user_id is null then
        raise exception 'TV login session is not approved' using errcode = 'P0001';
    end if;

    update public.tv_login_sessions
    set status = 'used'
    where code = upper(trim(coalesce(p_code, '')))
      and device_nonce = trim(coalesce(p_device_nonce, ''));

    return query select v_user_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------

revoke all on function public.assert_profile_pin_format(text) from public, anon, authenticated;
revoke all on function public.set_profile_pin(integer, text, text) from public, anon, authenticated;
revoke all on function public.clear_profile_pin(integer, text) from public, anon, authenticated;
revoke all on function public.verify_profile_pin(integer, text) from public, anon, authenticated;
revoke all on function public.generate_sync_code(text) from public, anon, authenticated;
revoke all on function public.get_sync_code(text) from public, anon, authenticated;
revoke all on function public.claim_sync_code(text, text, text) from public, anon, authenticated;
revoke all on function public.unlink_device(uuid) from public, anon, authenticated;
revoke all on function public.start_tv_login_session(text, text, text) from public, anon, authenticated;
revoke all on function public.poll_tv_login_session(text, text) from public, anon, authenticated;
revoke all on function public.approve_tv_login_session(text, text) from public, anon, authenticated;
revoke all on function public.consume_tv_login_session(text, text) from public, anon, authenticated;

grant execute on function public.set_profile_pin(integer, text, text) to authenticated;
grant execute on function public.clear_profile_pin(integer, text) to authenticated;
grant execute on function public.verify_profile_pin(integer, text) to authenticated;
grant execute on function public.generate_sync_code(text) to authenticated;
grant execute on function public.get_sync_code(text) to authenticated;
grant execute on function public.claim_sync_code(text, text, text) to authenticated;
grant execute on function public.unlink_device(uuid) to authenticated;
grant execute on function public.start_tv_login_session(text, text, text) to anon, authenticated;
grant execute on function public.poll_tv_login_session(text, text) to anon, authenticated;
grant execute on function public.approve_tv_login_session(text, text) to authenticated;
grant execute on function public.consume_tv_login_session(text, text) to service_role;
