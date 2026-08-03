create extension if not exists pgcrypto with schema extensions;

create table public.profiles (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_index integer not null check (profile_index between 1 and 6),
    name text not null,
    avatar_color_hex text not null,
    uses_primary_addons boolean not null default false,
    uses_primary_plugins boolean not null default false,
    avatar_id text,
    avatar_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, profile_index)
);

create table public.profile_locks (
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_index integer not null check (profile_index between 1 and 6),
    pin_enabled boolean not null default false,
    pin_hash text,
    pin_locked_until timestamptz,
    failed_attempts integer not null default 0 check (failed_attempts >= 0),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, profile_index),
    foreign key (user_id, profile_index)
        references public.profiles(user_id, profile_index)
        on delete cascade
);

create table public.addons (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id integer not null default 1 check (profile_id between 1 and 6),
    url text not null,
    name text,
    enabled boolean not null default true,
    sort_order integer not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    foreign key (user_id, profile_id)
        references public.profiles(user_id, profile_index)
        on delete cascade
);

create unique index addons_user_profile_url_uidx
    on public.addons (user_id, profile_id, url);

create index addons_user_profile_sort_idx
    on public.addons (user_id, profile_id, sort_order);

create table public.plugins (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id integer not null default 1 check (profile_id between 1 and 6),
    url text not null,
    name text,
    enabled boolean not null default true,
    sort_order integer not null default 0,
    repo_type text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    foreign key (user_id, profile_id)
        references public.profiles(user_id, profile_index)
        on delete cascade
);

create unique index plugins_user_profile_url_uidx
    on public.plugins (user_id, profile_id, url);

create index plugins_user_profile_sort_idx
    on public.plugins (user_id, profile_id, sort_order);

create table public.linked_devices (
    id uuid primary key default gen_random_uuid(),
    owner_id uuid not null references auth.users(id) on delete cascade,
    device_user_id uuid not null references auth.users(id) on delete cascade,
    device_name text,
    linked_at timestamptz not null default now(),
    constraint linked_devices_device_user_id_key unique (device_user_id),
    constraint linked_devices_owner_device_check check (owner_id <> device_user_id)
);

create table public.avatar_catalog (
    id text primary key,
    display_name text not null,
    storage_path text not null,
    category text not null,
    sort_order integer not null default 0,
    bg_color text,
    is_active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index avatar_catalog_active_sort_idx
    on public.avatar_catalog (is_active, sort_order);

create table public.registered_devices (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    installation_id text not null,
    client_name text not null,
    client_version text not null,
    platform text not null,
    device_name text not null,
    last_seen_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, installation_id)
);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger profiles_set_updated_at
before update on public.profiles
for each row execute function public.set_updated_at();

create trigger profile_locks_set_updated_at
before update on public.profile_locks
for each row execute function public.set_updated_at();

create trigger addons_set_updated_at
before update on public.addons
for each row execute function public.set_updated_at();

create trigger plugins_set_updated_at
before update on public.plugins
for each row execute function public.set_updated_at();

create trigger avatar_catalog_set_updated_at
before update on public.avatar_catalog
for each row execute function public.set_updated_at();

create trigger registered_devices_set_updated_at
before update on public.registered_devices
for each row execute function public.set_updated_at();

create or replace function public.get_sync_owner()
returns uuid
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_caller_id uuid := auth.uid();
    v_owner_id uuid;
begin
    if v_caller_id is null then
        return null;
    end if;

    select linked.owner_id
    into v_owner_id
    from public.linked_devices as linked
    where linked.device_user_id = v_caller_id;

    return coalesce(v_owner_id, v_caller_id);
end;
$$;

create or replace function public.can_access_user_data(target_user_id uuid)
returns boolean
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_caller_id uuid := auth.uid();
    v_owner_id uuid;
begin
    if v_caller_id is null or target_user_id is null then
        return false;
    end if;

    if target_user_id = v_caller_id then
        return true;
    end if;

    v_owner_id := public.get_sync_owner();

    return v_owner_id <> v_caller_id
        and target_user_id = v_owner_id
        and exists (
            select 1
            from public.linked_devices as linked
            where linked.owner_id = target_user_id
              and linked.device_user_id = v_caller_id
        );
end;
$$;

create or replace function public.get_avatar_catalog()
returns table (
    id text,
    display_name text,
    storage_path text,
    category text,
    sort_order integer,
    bg_color text
)
language sql
stable
security definer
set search_path = ''
as $$
    select
        avatar.id,
        avatar.display_name,
        avatar.storage_path,
        avatar.category,
        avatar.sort_order,
        avatar.bg_color
    from public.avatar_catalog as avatar
    where avatar.is_active
    order by avatar.sort_order, avatar.id;
$$;

create or replace function public.sync_push_profiles(
    p_client_max_profiles integer,
    p_profiles jsonb,
    p_origin_client_id text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.get_sync_owner();
    v_profile jsonb;
    v_profile_index integer;
begin
    if auth.uid() is null or v_owner_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;

    if p_client_max_profiles is null or p_client_max_profiles not between 1 and 6 then
        raise exception 'p_client_max_profiles must be between 1 and 6'
            using errcode = '22023';
    end if;

    if p_origin_client_id is null
        or p_origin_client_id !~ '^[A-Za-z0-9_-]{16,96}$' then
        raise exception 'Invalid p_origin_client_id'
            using errcode = '22023';
    end if;

    if p_profiles is null or jsonb_typeof(p_profiles) <> 'array' then
        raise exception 'p_profiles must be a JSON array'
            using errcode = '22023';
    end if;

    if jsonb_array_length(p_profiles) > p_client_max_profiles then
        raise exception 'Profile count exceeds p_client_max_profiles'
            using errcode = '22023';
    end if;

    for v_profile in
        select item.value
        from jsonb_array_elements(p_profiles) as item(value)
    loop
        if jsonb_typeof(v_profile) <> 'object'
            or jsonb_typeof(v_profile -> 'profile_index') <> 'number' then
            raise exception 'Each profile must contain a numeric profile_index'
                using errcode = '22023';
        end if;

        begin
            v_profile_index := (v_profile ->> 'profile_index')::integer;
        exception when others then
            raise exception 'profile_index must be an integer'
                using errcode = '22023';
        end;

        if v_profile_index not between 1 and p_client_max_profiles then
            raise exception 'profile_index % is outside the accepted range', v_profile_index
                using errcode = '22023';
        end if;

        if jsonb_typeof(v_profile -> 'name') is distinct from 'string'
            or jsonb_typeof(v_profile -> 'avatar_color_hex') is distinct from 'string'
            or jsonb_typeof(v_profile -> 'uses_primary_addons') is distinct from 'boolean'
            or jsonb_typeof(v_profile -> 'uses_primary_plugins') is distinct from 'boolean' then
            raise exception 'Profile payload has invalid required fields'
                using errcode = '22023';
        end if;

        if nullif(btrim(v_profile ->> 'name'), '') is null
            or nullif(btrim(v_profile ->> 'avatar_color_hex'), '') is null then
            raise exception 'Profile name and avatar_color_hex cannot be blank'
                using errcode = '22023';
        end if;

        if v_profile ? 'avatar_id'
            and jsonb_typeof(v_profile -> 'avatar_id') not in ('string', 'null') then
            raise exception 'avatar_id must be a string or null'
                using errcode = '22023';
        end if;

        if v_profile ? 'avatar_url'
            and jsonb_typeof(v_profile -> 'avatar_url') not in ('string', 'null') then
            raise exception 'avatar_url must be a string or null'
                using errcode = '22023';
        end if;
    end loop;

    if (
        select count(*)
        from jsonb_array_elements(p_profiles)
    ) <> (
        select count(distinct (item.value ->> 'profile_index')::integer)
        from jsonb_array_elements(p_profiles) as item(value)
    ) then
        raise exception 'Duplicate profile_index values are not allowed'
            using errcode = '22023';
    end if;

    if not exists (
        select 1
        from jsonb_array_elements(p_profiles) as item(value)
        where (item.value ->> 'profile_index')::integer = 1
    ) then
        raise exception 'Primary profile 1 is required'
            using errcode = '22023';
    end if;

    insert into public.profiles (
        user_id,
        profile_index,
        name,
        avatar_color_hex,
        uses_primary_addons,
        uses_primary_plugins,
        avatar_id,
        avatar_url
    )
    select
        v_owner_id,
        (item.value ->> 'profile_index')::integer,
        item.value ->> 'name',
        item.value ->> 'avatar_color_hex',
        (item.value ->> 'uses_primary_addons')::boolean,
        (item.value ->> 'uses_primary_plugins')::boolean,
        item.value ->> 'avatar_id',
        item.value ->> 'avatar_url'
    from jsonb_array_elements(p_profiles) as item(value)
    on conflict (user_id, profile_index) do update
    set name = excluded.name,
        avatar_color_hex = excluded.avatar_color_hex,
        uses_primary_addons = excluded.uses_primary_addons,
        uses_primary_plugins = excluded.uses_primary_plugins,
        avatar_id = excluded.avatar_id,
        avatar_url = excluded.avatar_url;

    delete from public.profiles as profile
    where profile.user_id = v_owner_id
      and profile.profile_index <= p_client_max_profiles
      and not exists (
          select 1
          from jsonb_array_elements(p_profiles) as item(value)
          where (item.value ->> 'profile_index')::integer = profile.profile_index
      );

    insert into public.profile_locks (user_id, profile_index)
    select v_owner_id, profile.profile_index
    from public.profiles as profile
    where profile.user_id = v_owner_id
    on conflict (user_id, profile_index) do nothing;
end;
$$;

create or replace function public.sync_pull_profiles()
returns table (
    id text,
    user_id text,
    profile_index integer,
    name text,
    avatar_color_hex text,
    uses_primary_addons boolean,
    uses_primary_plugins boolean,
    avatar_id text,
    avatar_url text,
    created_at text,
    updated_at text
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

    return query
    select
        profile.id::text,
        profile.user_id::text,
        profile.profile_index,
        profile.name,
        profile.avatar_color_hex,
        profile.uses_primary_addons,
        profile.uses_primary_plugins,
        profile.avatar_id,
        profile.avatar_url,
        profile.created_at::text,
        profile.updated_at::text
    from public.profiles as profile
    where profile.user_id = v_owner_id
    order by profile.profile_index;
end;
$$;

create or replace function public.sync_pull_profile_locks()
returns table (
    profile_index integer,
    pin_enabled boolean,
    pin_locked_until text
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

    return query
    select
        profile_lock.profile_index,
        profile_lock.pin_enabled,
        profile_lock.pin_locked_until::text
    from public.profile_locks as profile_lock
    where profile_lock.user_id = v_owner_id
    order by profile_lock.profile_index;
end;
$$;

create or replace function public.sync_push_addons(
    p_addons jsonb,
    p_profile_id integer,
    p_origin_client_id text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.get_sync_owner();
    v_addon jsonb;
    v_url text;
begin
    if auth.uid() is null or v_owner_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;

    if p_profile_id is null or p_profile_id not between 1 and 6 then
        raise exception 'p_profile_id must be between 1 and 6'
            using errcode = '22023';
    end if;

    if p_origin_client_id is null
        or p_origin_client_id !~ '^[A-Za-z0-9_-]{16,96}$' then
        raise exception 'Invalid p_origin_client_id'
            using errcode = '22023';
    end if;

    if p_addons is null or jsonb_typeof(p_addons) <> 'array' then
        raise exception 'p_addons must be a JSON array'
            using errcode = '22023';
    end if;

    if not exists (
        select 1
        from public.profiles as profile
        where profile.user_id = v_owner_id
          and profile.profile_index = p_profile_id
    ) then
        raise exception 'Profile % does not exist', p_profile_id
            using errcode = '22023';
    end if;

    for v_addon in
        select item.value
        from jsonb_array_elements(p_addons) as item(value)
    loop
        if jsonb_typeof(v_addon) <> 'object'
            or jsonb_typeof(v_addon -> 'url') <> 'string' then
            raise exception 'Each addon must contain a URL string'
                using errcode = '22023';
        end if;

        v_url := btrim(v_addon ->> 'url');
        if v_url is null or v_url !~* '^https?://[^[:space:]]+$' then
            raise exception 'Addon URL is empty or malformed'
                using errcode = '22023';
        end if;

        if v_addon ? 'sort_order'
            and jsonb_typeof(v_addon -> 'sort_order') <> 'number' then
            raise exception 'Addon sort_order must be an integer'
                using errcode = '22023';
        end if;

        if v_addon ? 'enabled'
            and jsonb_typeof(v_addon -> 'enabled') <> 'boolean' then
            raise exception 'Addon enabled must be a boolean'
                using errcode = '22023';
        end if;

        if v_addon ? 'name'
            and jsonb_typeof(v_addon -> 'name') not in ('string', 'null') then
            raise exception 'Addon name must be a string or null'
                using errcode = '22023';
        end if;
    end loop;

    if (
        select count(*)
        from jsonb_array_elements(p_addons)
    ) <> (
        select count(distinct btrim(item.value ->> 'url'))
        from jsonb_array_elements(p_addons) as item(value)
    ) then
        raise exception 'Duplicate addon URLs are not allowed'
            using errcode = '22023';
    end if;

    insert into public.addons (
        user_id,
        profile_id,
        url,
        name,
        enabled,
        sort_order
    )
    select
        v_owner_id,
        p_profile_id,
        btrim(item.value ->> 'url'),
        item.value ->> 'name',
        coalesce((item.value ->> 'enabled')::boolean, true),
        coalesce((item.value ->> 'sort_order')::integer, 0)
    from jsonb_array_elements(p_addons) as item(value)
    on conflict (user_id, profile_id, url) do update
    set name = excluded.name,
        enabled = excluded.enabled,
        sort_order = excluded.sort_order;

    delete from public.addons as addon
    where addon.user_id = v_owner_id
      and addon.profile_id = p_profile_id
      and not exists (
          select 1
          from jsonb_array_elements(p_addons) as item(value)
          where btrim(item.value ->> 'url') = addon.url
      );
end;
$$;

create or replace function public.sync_push_plugins(
    p_plugins jsonb,
    p_profile_id integer,
    p_origin_client_id text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.get_sync_owner();
    v_plugin jsonb;
    v_url text;
begin
    if auth.uid() is null or v_owner_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;

    if p_profile_id is null or p_profile_id not between 1 and 6 then
        raise exception 'p_profile_id must be between 1 and 6'
            using errcode = '22023';
    end if;

    if p_origin_client_id is null
        or p_origin_client_id !~ '^[A-Za-z0-9_-]{16,96}$' then
        raise exception 'Invalid p_origin_client_id'
            using errcode = '22023';
    end if;

    if p_plugins is null or jsonb_typeof(p_plugins) <> 'array' then
        raise exception 'p_plugins must be a JSON array'
            using errcode = '22023';
    end if;

    if not exists (
        select 1
        from public.profiles as profile
        where profile.user_id = v_owner_id
          and profile.profile_index = p_profile_id
    ) then
        raise exception 'Profile % does not exist', p_profile_id
            using errcode = '22023';
    end if;

    for v_plugin in
        select item.value
        from jsonb_array_elements(p_plugins) as item(value)
    loop
        if jsonb_typeof(v_plugin) <> 'object'
            or jsonb_typeof(v_plugin -> 'url') <> 'string' then
            raise exception 'Each plugin must contain a URL string'
                using errcode = '22023';
        end if;

        v_url := btrim(v_plugin ->> 'url');
        if v_url is null or v_url !~* '^https?://[^[:space:]]+$' then
            raise exception 'Plugin URL is empty or malformed'
                using errcode = '22023';
        end if;

        if v_plugin ? 'sort_order'
            and jsonb_typeof(v_plugin -> 'sort_order') <> 'number' then
            raise exception 'Plugin sort_order must be an integer'
                using errcode = '22023';
        end if;

        if v_plugin ? 'enabled'
            and jsonb_typeof(v_plugin -> 'enabled') <> 'boolean' then
            raise exception 'Plugin enabled must be a boolean'
                using errcode = '22023';
        end if;

        if v_plugin ? 'name'
            and jsonb_typeof(v_plugin -> 'name') not in ('string', 'null') then
            raise exception 'Plugin name must be a string or null'
                using errcode = '22023';
        end if;

        if v_plugin ? 'repo_type'
            and jsonb_typeof(v_plugin -> 'repo_type') not in ('string', 'null') then
            raise exception 'Plugin repo_type must be a string or null'
                using errcode = '22023';
        end if;
    end loop;

    if (
        select count(*)
        from jsonb_array_elements(p_plugins)
    ) <> (
        select count(distinct btrim(item.value ->> 'url'))
        from jsonb_array_elements(p_plugins) as item(value)
    ) then
        raise exception 'Duplicate plugin URLs are not allowed'
            using errcode = '22023';
    end if;

    insert into public.plugins (
        user_id,
        profile_id,
        url,
        name,
        enabled,
        sort_order,
        repo_type
    )
    select
        v_owner_id,
        p_profile_id,
        btrim(item.value ->> 'url'),
        item.value ->> 'name',
        coalesce((item.value ->> 'enabled')::boolean, true),
        coalesce((item.value ->> 'sort_order')::integer, 0),
        item.value ->> 'repo_type'
    from jsonb_array_elements(p_plugins) as item(value)
    on conflict (user_id, profile_id, url) do update
    set name = excluded.name,
        enabled = excluded.enabled,
        sort_order = excluded.sort_order,
        repo_type = excluded.repo_type;

    delete from public.plugins as plugin
    where plugin.user_id = v_owner_id
      and plugin.profile_id = p_profile_id
      and not exists (
          select 1
          from jsonb_array_elements(p_plugins) as item(value)
          where btrim(item.value ->> 'url') = plugin.url
      );
end;
$$;

create or replace function public.register_current_device(
    p_installation_id text,
    p_client_name text,
    p_client_version text,
    p_platform text,
    p_device_name text
)
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

    if nullif(btrim(p_installation_id), '') is null then
        raise exception 'p_installation_id cannot be blank'
            using errcode = '22023';
    end if;

    if nullif(btrim(p_client_name), '') is null
        or nullif(btrim(p_client_version), '') is null
        or nullif(btrim(p_platform), '') is null then
        raise exception 'Client name, version, and platform cannot be blank'
            using errcode = '22023';
    end if;

    insert into public.registered_devices (
        user_id,
        installation_id,
        client_name,
        client_version,
        platform,
        device_name,
        last_seen_at
    )
    values (
        v_caller_id,
        btrim(p_installation_id),
        btrim(p_client_name),
        btrim(p_client_version),
        btrim(p_platform),
        left(coalesce(p_device_name, ''), 160),
        now()
    )
    on conflict (user_id, installation_id) do update
    set client_name = excluded.client_name,
        client_version = excluded.client_version,
        platform = excluded.platform,
        device_name = excluded.device_name,
        last_seen_at = now();
end;
$$;

create or replace function public.get_sync_overview()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.get_sync_owner();
    v_addons jsonb;
    v_plugins jsonb;
    v_profiles jsonb;
begin
    if auth.uid() is null or v_owner_id is null then
        raise exception 'Authentication required' using errcode = '42501';
    end if;

    select coalesce(
        jsonb_object_agg(counts.profile_id::text, counts.item_count),
        '{}'::jsonb
    )
    into v_addons
    from (
        select addon.profile_id, count(*)::integer as item_count
        from public.addons as addon
        where addon.user_id = v_owner_id
        group by addon.profile_id
    ) as counts;

    select coalesce(
        jsonb_object_agg(counts.profile_id::text, counts.item_count),
        '{}'::jsonb
    )
    into v_plugins
    from (
        select plugin.profile_id, count(*)::integer as item_count
        from public.plugins as plugin
        where plugin.user_id = v_owner_id
        group by plugin.profile_id
    ) as counts;

    select coalesce(
        jsonb_object_agg(
            profile.profile_index::text,
            jsonb_build_object(
                'name', profile.name,
                'color', profile.avatar_color_hex
            )
        ),
        '{}'::jsonb
    )
    into v_profiles
    from public.profiles as profile
    where profile.user_id = v_owner_id;

    return jsonb_build_object(
        'addons', v_addons,
        'plugins', v_plugins,
        'library_items', '{}'::jsonb,
        'watch_progress', '{}'::jsonb,
        'watched_items', '{}'::jsonb,
        'profiles', v_profiles
    );
end;
$$;

alter table public.profiles enable row level security;
alter table public.profile_locks enable row level security;
alter table public.addons enable row level security;
alter table public.plugins enable row level security;
alter table public.linked_devices enable row level security;
alter table public.avatar_catalog enable row level security;
alter table public.registered_devices enable row level security;

create policy profiles_select_accessible
on public.profiles
for select
to authenticated
using (public.can_access_user_data(user_id));

create policy profile_locks_select_accessible
on public.profile_locks
for select
to authenticated
using (public.can_access_user_data(user_id));

create policy addons_select_accessible
on public.addons
for select
to authenticated
using (public.can_access_user_data(user_id));

create policy plugins_select_accessible
on public.plugins
for select
to authenticated
using (public.can_access_user_data(user_id));

create policy linked_devices_owner_select
on public.linked_devices
for select
to authenticated
using (auth.uid() = owner_id);

create policy linked_devices_device_select
on public.linked_devices
for select
to authenticated
using (auth.uid() = device_user_id);

create policy avatar_catalog_active_select
on public.avatar_catalog
for select
to anon, authenticated
using (is_active);

create policy registered_devices_own_select
on public.registered_devices
for select
to authenticated
using (auth.uid() = user_id);

revoke all on table public.profiles from public, anon, authenticated;
revoke all on table public.profile_locks from public, anon, authenticated;
revoke all on table public.addons from public, anon, authenticated;
revoke all on table public.plugins from public, anon, authenticated;
revoke all on table public.linked_devices from public, anon, authenticated;
revoke all on table public.avatar_catalog from public, anon, authenticated;
revoke all on table public.registered_devices from public, anon, authenticated;

grant usage on schema public to anon, authenticated;
grant select on table public.addons to authenticated;
grant select on table public.plugins to authenticated;
grant select on table public.linked_devices to authenticated;

revoke all on function public.set_updated_at() from public, anon, authenticated;
revoke all on function public.get_sync_owner() from public, anon, authenticated;
revoke all on function public.can_access_user_data(uuid) from public, anon, authenticated;
revoke all on function public.get_avatar_catalog() from public, anon, authenticated;
revoke all on function public.sync_push_profiles(integer, jsonb, text) from public, anon, authenticated;
revoke all on function public.sync_pull_profiles() from public, anon, authenticated;
revoke all on function public.sync_pull_profile_locks() from public, anon, authenticated;
revoke all on function public.sync_push_addons(jsonb, integer, text) from public, anon, authenticated;
revoke all on function public.sync_push_plugins(jsonb, integer, text) from public, anon, authenticated;
revoke all on function public.register_current_device(text, text, text, text, text) from public, anon, authenticated;
revoke all on function public.get_sync_overview() from public, anon, authenticated;

grant execute on function public.get_avatar_catalog() to anon, authenticated;
grant execute on function public.get_sync_owner() to authenticated;
grant execute on function public.can_access_user_data(uuid) to authenticated;
grant execute on function public.sync_push_profiles(integer, jsonb, text) to authenticated;
grant execute on function public.sync_pull_profiles() to authenticated;
grant execute on function public.sync_pull_profile_locks() to authenticated;
grant execute on function public.sync_push_addons(jsonb, integer, text) to authenticated;
grant execute on function public.sync_push_plugins(jsonb, integer, text) to authenticated;
grant execute on function public.register_current_device(text, text, text, text, text) to authenticated;
grant execute on function public.get_sync_overview() to authenticated;
