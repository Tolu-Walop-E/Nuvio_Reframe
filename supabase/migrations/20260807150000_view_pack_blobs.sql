-- Studio → TV view pack sync (addon-style account delivery, no device IP).

create table public.view_pack_blobs (
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id integer not null check (profile_id between 1 and 6),
    pack_json jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (user_id, profile_id),
    foreign key (user_id, profile_id)
        references public.profiles(user_id, profile_index)
        on delete cascade
);

create trigger view_pack_blobs_set_updated_at
before update on public.view_pack_blobs
for each row execute function public.set_updated_at();

alter table public.view_pack_blobs enable row level security;

revoke all on table public.view_pack_blobs from public, anon, authenticated;

create or replace function public.sync_push_view_pack(
    p_profile_id integer,
    p_pack_json jsonb,
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

    if p_pack_json is null or jsonb_typeof(p_pack_json) <> 'object' then
        raise exception 'p_pack_json must be a JSON object' using errcode = '22023';
    end if;

    if not exists (
        select 1
        from public.profiles as profile
        where profile.user_id = v_owner_id
          and profile.profile_index = p_profile_id
    ) then
        raise exception 'Profile does not exist' using errcode = '22023';
    end if;

    insert into public.view_pack_blobs (
        user_id,
        profile_id,
        pack_json
    )
    values (
        v_owner_id,
        p_profile_id,
        p_pack_json
    )
    on conflict (user_id, profile_id) do update
    set pack_json = excluded.pack_json;
end;
$$;

create or replace function public.sync_pull_view_pack(
    p_profile_id integer
)
returns table (
    profile_id integer,
    pack_json jsonb,
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
        blob.pack_json,
        blob.updated_at
    from public.view_pack_blobs as blob
    where blob.user_id = v_owner_id
      and blob.profile_id = p_profile_id;
end;
$$;

create or replace function public.sync_clear_view_pack(
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

    delete from public.view_pack_blobs as blob
    where blob.user_id = v_owner_id
      and blob.profile_id = p_profile_id;
end;
$$;

revoke all on function public.sync_push_view_pack(integer, jsonb, text) from public, anon;
revoke all on function public.sync_pull_view_pack(integer) from public, anon;
revoke all on function public.sync_clear_view_pack(integer, text) from public, anon;

grant execute on function public.sync_push_view_pack(integer, jsonb, text) to authenticated;
grant execute on function public.sync_pull_view_pack(integer) to authenticated;
grant execute on function public.sync_clear_view_pack(integer, text) to authenticated;
