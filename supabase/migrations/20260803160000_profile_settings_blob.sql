-- Profile layout/theme/settings blob sync (platform-scoped, typically `tv`).
-- Matches Android ProfileSettingsSyncService RPC contracts.

create table public.profile_settings_blobs (
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

create trigger profile_settings_blobs_set_updated_at
before update on public.profile_settings_blobs
for each row execute function public.set_updated_at();

alter table public.profile_settings_blobs enable row level security;
revoke all on table public.profile_settings_blobs from public, anon, authenticated;

create or replace function public.sync_push_profile_settings_blob(
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
    v_owner_id uuid := public.assert_sync_profile_access(
        p_profile_id,
        true,
        p_origin_client_id
    );
begin
    if p_platform is null or p_platform !~ '^[A-Za-z0-9_-]{1,64}$' then
        raise exception 'Invalid p_platform' using errcode = '22023';
    end if;

    if p_settings_json is null or jsonb_typeof(p_settings_json) <> 'object' then
        raise exception 'p_settings_json must be a JSON object' using errcode = '22023';
    end if;

    insert into public.profile_settings_blobs (
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

create or replace function public.sync_pull_profile_settings_blob(
    p_profile_id integer,
    p_platform text
)
returns table (
    profile_id integer,
    settings_json jsonb,
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
    if p_platform is null or p_platform !~ '^[A-Za-z0-9_-]{1,64}$' then
        raise exception 'Invalid p_platform' using errcode = '22023';
    end if;

    return query
    select
        blob.profile_id,
        blob.settings_json,
        blob.updated_at
    from public.profile_settings_blobs as blob
    where blob.user_id = v_owner_id
      and blob.profile_id = p_profile_id
      and blob.platform = p_platform;
end;
$$;

revoke all on function public.sync_push_profile_settings_blob(integer, jsonb, text, text)
    from public, anon, authenticated;
revoke all on function public.sync_pull_profile_settings_blob(integer, text)
    from public, anon, authenticated;

grant execute on function public.sync_push_profile_settings_blob(integer, jsonb, text, text)
    to authenticated;
grant execute on function public.sync_pull_profile_settings_blob(integer, text)
    to authenticated;
