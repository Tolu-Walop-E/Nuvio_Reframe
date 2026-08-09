-- Debrid / MDBList / AnimeSkip credential sync (per user/profile/provider).
-- Matches Android ProviderCredentialSyncService RPC contracts.

create table public.provider_credentials (
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id integer not null check (profile_id between 1 and 6),
    provider text not null check (provider ~ '^[a-z0-9_:-]{1,64}$'),
    credential_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, profile_id, provider),
    foreign key (user_id, profile_id)
        references public.profiles(user_id, profile_index)
        on delete cascade
);

create trigger provider_credentials_set_updated_at
before update on public.provider_credentials
for each row execute function public.set_updated_at();

alter table public.provider_credentials enable row level security;
revoke all on table public.provider_credentials from public, anon, authenticated;

-- The client always sends every known provider, using an empty string for the
-- ones the user has not filled in. Treating those as blank is what lets the
-- seed RPC fill gaps without ever clobbering a real key.
create or replace function public.provider_credential_is_blank(p_credential_json jsonb)
returns boolean
language sql
immutable
set search_path = ''
as $$
    select p_credential_json is null
        or jsonb_typeof(p_credential_json) <> 'object'
        or not exists (
            select 1
            from jsonb_each_text(p_credential_json) as field(key, value)
            where coalesce(btrim(field.value), '') <> ''
        );
$$;

create or replace function public.assert_provider_credential_payload(p_credentials jsonb)
returns void
language plpgsql
immutable
set search_path = ''
as $$
begin
    if p_credentials is null or jsonb_typeof(p_credentials) <> 'array' then
        raise exception 'p_credentials must be a JSON array' using errcode = '22023';
    end if;

    if exists (
        select 1
        from jsonb_array_elements(p_credentials) as entry(credential)
        where jsonb_typeof(entry.credential) <> 'object'
           or coalesce(lower(btrim(entry.credential ->> 'provider')), '') !~ '^[a-z0-9_:-]{1,64}$'
           or jsonb_typeof(entry.credential -> 'credential_json') is distinct from 'object'
    ) then
        raise exception 'Invalid provider credential entry' using errcode = '22023';
    end if;

    if (
        select count(distinct lower(btrim(entry.credential ->> 'provider')))
        from jsonb_array_elements(p_credentials) as entry(credential)
    ) <> jsonb_array_length(p_credentials) then
        raise exception 'Duplicate provider in p_credentials' using errcode = '22023';
    end if;
end;
$$;

-- Full snapshot replace: providers missing from the payload are removed.
create or replace function public.sync_push_provider_credentials(
    p_profile_id integer,
    p_credentials jsonb,
    p_origin_client_id text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.assert_sync_profile_access(
        p_profile_id,
        true,
        p_origin_client_id
    );
begin
    perform public.assert_provider_credential_payload(p_credentials);

    delete from public.provider_credentials as existing
    where existing.user_id = v_owner_id
      and existing.profile_id = p_profile_id
      and not exists (
          select 1
          from jsonb_array_elements(p_credentials) as entry(credential)
          where lower(btrim(entry.credential ->> 'provider')) = existing.provider
      );

    insert into public.provider_credentials as target (
        user_id,
        profile_id,
        provider,
        credential_json
    )
    select
        v_owner_id,
        p_profile_id,
        lower(btrim(entry.credential ->> 'provider')),
        entry.credential -> 'credential_json'
    from jsonb_array_elements(p_credentials) as entry(credential)
    on conflict (user_id, profile_id, provider) do update
    set credential_json = excluded.credential_json
    where target.credential_json is distinct from excluded.credential_json;
end;
$$;

-- Gap fill only: inserts providers the account has never stored and replaces
-- blank values, so a device that has not signed in yet cannot wipe real keys.
create or replace function public.sync_seed_provider_credentials(
    p_profile_id integer,
    p_credentials jsonb,
    p_origin_client_id text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.assert_sync_profile_access(
        p_profile_id,
        true,
        p_origin_client_id
    );
begin
    perform public.assert_provider_credential_payload(p_credentials);

    insert into public.provider_credentials as target (
        user_id,
        profile_id,
        provider,
        credential_json
    )
    select
        v_owner_id,
        p_profile_id,
        lower(btrim(entry.credential ->> 'provider')),
        entry.credential -> 'credential_json'
    from jsonb_array_elements(p_credentials) as entry(credential)
    on conflict (user_id, profile_id, provider) do update
    set credential_json = excluded.credential_json
    where public.provider_credential_is_blank(target.credential_json)
      and not public.provider_credential_is_blank(excluded.credential_json);
end;
$$;

create or replace function public.sync_pull_provider_credentials(
    p_profile_id integer
)
returns table (
    provider text,
    credential_json jsonb,
    updated_at timestamptz
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.assert_sync_profile_access(p_profile_id);
begin
    return query
    select
        credential.provider,
        credential.credential_json,
        credential.updated_at
    from public.provider_credentials as credential
    where credential.user_id = v_owner_id
      and credential.profile_id = p_profile_id;
end;
$$;

revoke all on function public.provider_credential_is_blank(jsonb)
    from public, anon, authenticated;
revoke all on function public.assert_provider_credential_payload(jsonb)
    from public, anon, authenticated;
revoke all on function public.sync_push_provider_credentials(integer, jsonb, text)
    from public, anon, authenticated;
revoke all on function public.sync_seed_provider_credentials(integer, jsonb, text)
    from public, anon, authenticated;
revoke all on function public.sync_pull_provider_credentials(integer)
    from public, anon, authenticated;

grant execute on function public.sync_push_provider_credentials(integer, jsonb, text)
    to authenticated;
grant execute on function public.sync_seed_provider_credentials(integer, jsonb, text)
    to authenticated;
grant execute on function public.sync_pull_provider_credentials(integer)
    to authenticated;
