create table public.collection_blobs (
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id integer not null check (profile_id between 1 and 6),
    collections_json jsonb not null default '[]'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, profile_id),
    foreign key (user_id, profile_id)
        references public.profiles(user_id, profile_index)
        on delete cascade
);

create table public.home_catalog_settings (
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id integer not null check (profile_id between 1 and 6),
    platform text not null check (platform ~ '^[A-Za-z0-9_-]{1,64}$'),
    settings_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, profile_id, platform),
    foreign key (user_id, profile_id)
        references public.profiles(user_id, profile_index)
        on delete cascade
);

create trigger collection_blobs_set_updated_at
before update on public.collection_blobs
for each row execute function public.set_updated_at();

create trigger home_catalog_settings_set_updated_at
before update on public.home_catalog_settings
for each row execute function public.set_updated_at();

alter table public.collection_blobs enable row level security;
alter table public.home_catalog_settings enable row level security;

revoke all on table public.collection_blobs from public, anon, authenticated;
revoke all on table public.home_catalog_settings from public, anon, authenticated;

create or replace function public.sync_push_collections(
    p_profile_id integer,
    p_collections_json jsonb,
    p_origin_client_id text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.get_sync_owner();
begin
    if auth.uid() is null or v_owner_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;

    if p_profile_id is null or p_profile_id not between 1 and 6 then
        raise exception 'Invalid p_profile_id' using errcode = '22023';
    end if;

    if p_origin_client_id is null
        or p_origin_client_id !~ '^[A-Za-z0-9_-]{16,96}$' then
        raise exception 'Invalid p_origin_client_id' using errcode = '22023';
    end if;

    if p_collections_json is null or jsonb_typeof(p_collections_json) <> 'array' then
        raise exception 'p_collections_json must be a JSON array' using errcode = '22023';
    end if;

    if not exists (
        select 1
        from public.profiles as profile
        where profile.user_id = v_owner_id
          and profile.profile_index = p_profile_id
    ) then
        raise exception 'Profile does not exist' using errcode = '22023';
    end if;

    insert into public.collection_blobs (
        user_id,
        profile_id,
        collections_json
    )
    values (
        v_owner_id,
        p_profile_id,
        p_collections_json
    )
    on conflict (user_id, profile_id) do update
    set collections_json = excluded.collections_json;
end;
$$;

create or replace function public.sync_pull_collections(
    p_profile_id integer
)
returns table (
    profile_id integer,
    collections_json jsonb,
    updated_at timestamptz
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.get_sync_owner();
begin
    if auth.uid() is null or v_owner_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;

    if p_profile_id is null or p_profile_id not between 1 and 6 then
        raise exception 'Invalid p_profile_id' using errcode = '22023';
    end if;

    return query
    select
        blob.profile_id,
        blob.collections_json,
        blob.updated_at
    from public.collection_blobs as blob
    where blob.user_id = v_owner_id
      and blob.profile_id = p_profile_id;
end;
$$;

create or replace function public.sync_push_home_catalog_settings(
    p_profile_id integer,
    p_settings_json jsonb,
    p_platform text,
    p_origin_client_id text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.get_sync_owner();
begin
    if auth.uid() is null or v_owner_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;

    if p_profile_id is null or p_profile_id not between 1 and 6 then
        raise exception 'Invalid p_profile_id' using errcode = '22023';
    end if;

    if p_platform is null or p_platform !~ '^[A-Za-z0-9_-]{1,64}$' then
        raise exception 'Invalid p_platform' using errcode = '22023';
    end if;

    if p_origin_client_id is null
        or p_origin_client_id !~ '^[A-Za-z0-9_-]{16,96}$' then
        raise exception 'Invalid p_origin_client_id' using errcode = '22023';
    end if;

    if p_settings_json is null or jsonb_typeof(p_settings_json) <> 'object' then
        raise exception 'p_settings_json must be a JSON object' using errcode = '22023';
    end if;

    if not exists (
        select 1
        from public.profiles as profile
        where profile.user_id = v_owner_id
          and profile.profile_index = p_profile_id
    ) then
        raise exception 'Profile does not exist' using errcode = '22023';
    end if;

    insert into public.home_catalog_settings (
        user_id,
        profile_id,
        platform,
        settings_json
    )
    values (
        v_owner_id,
        p_profile_id,
        p_platform,
        p_settings_json
    )
    on conflict (user_id, profile_id, platform) do update
    set settings_json = excluded.settings_json;
end;
$$;

create or replace function public.sync_pull_home_catalog_settings(
    p_profile_id integer,
    p_platform text
)
returns table (
    profile_id integer,
    platform text,
    settings_json jsonb,
    updated_at timestamptz
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.get_sync_owner();
begin
    if auth.uid() is null or v_owner_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;

    if p_profile_id is null or p_profile_id not between 1 and 6 then
        raise exception 'Invalid p_profile_id' using errcode = '22023';
    end if;

    if p_platform is null or p_platform !~ '^[A-Za-z0-9_-]{1,64}$' then
        raise exception 'Invalid p_platform' using errcode = '22023';
    end if;

    return query
    select
        settings.profile_id,
        settings.platform,
        settings.settings_json,
        settings.updated_at
    from public.home_catalog_settings as settings
    where settings.user_id = v_owner_id
      and settings.profile_id = p_profile_id
      and settings.platform = p_platform;
end;
$$;

revoke all on function public.sync_push_collections(integer, jsonb, text) from public, anon;
revoke all on function public.sync_pull_collections(integer) from public, anon;
revoke all on function public.sync_push_home_catalog_settings(integer, jsonb, text, text) from public, anon;
revoke all on function public.sync_pull_home_catalog_settings(integer, text) from public, anon;

grant execute on function public.sync_push_collections(integer, jsonb, text) to authenticated;
grant execute on function public.sync_pull_collections(integer) to authenticated;
grant execute on function public.sync_push_home_catalog_settings(integer, jsonb, text, text) to authenticated;
grant execute on function public.sync_pull_home_catalog_settings(integer, text) to authenticated;
