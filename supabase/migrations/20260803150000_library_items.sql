-- Saved library snapshot + delta sync.
-- Matches Android SupabaseLibrarySyncRemoteDataSource RPC contracts.

create table public.library_items (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id integer not null default 1 check (profile_id between 1 and 6),
    content_id text not null,
    content_type text not null,
    name text not null default '',
    poster text,
    poster_shape text not null default 'POSTER',
    background text,
    description text,
    release_info text,
    imdb_rating real,
    genres text[] not null default '{}'::text[],
    addon_base_url text,
    added_at bigint not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, profile_id, content_id, content_type),
    foreign key (user_id, profile_id)
        references public.profiles(user_id, profile_index)
        on delete cascade
);

create index library_items_owner_profile_added_at_idx
    on public.library_items (user_id, profile_id, added_at desc, content_id, content_type);

create table public.library_items_events (
    event_id bigserial primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id integer not null check (profile_id between 1 and 6),
    operation text not null check (operation in ('upsert', 'delete')),
    content_id text not null,
    content_type text not null,
    name text not null default '',
    poster text,
    poster_shape text not null default 'POSTER',
    background text,
    description text,
    release_info text,
    imdb_rating real,
    genres text[] not null default '{}'::text[],
    addon_base_url text,
    added_at bigint not null default 0,
    created_at timestamptz not null default now(),
    foreign key (user_id, profile_id)
        references public.profiles(user_id, profile_index)
        on delete cascade
);

create index library_items_events_owner_profile_event_idx
    on public.library_items_events (user_id, profile_id, event_id);

create trigger library_items_set_updated_at
before update on public.library_items
for each row execute function public.set_updated_at();

create or replace function public.library_items_emit_event()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if tg_op = 'DELETE' then
        insert into public.library_items_events (
            user_id, profile_id, operation,
            content_id, content_type, name, poster, poster_shape,
            background, description, release_info, imdb_rating,
            genres, addon_base_url, added_at
        ) values (
            old.user_id, old.profile_id, 'delete',
            old.content_id, old.content_type, old.name, old.poster, old.poster_shape,
            old.background, old.description, old.release_info, old.imdb_rating,
            coalesce(old.genres, '{}'::text[]), old.addon_base_url, coalesce(old.added_at, 0)
        );
        return old;
    end if;

    insert into public.library_items_events (
        user_id, profile_id, operation,
        content_id, content_type, name, poster, poster_shape,
        background, description, release_info, imdb_rating,
        genres, addon_base_url, added_at
    ) values (
        new.user_id, new.profile_id, 'upsert',
        new.content_id, new.content_type, new.name, new.poster, new.poster_shape,
        new.background, new.description, new.release_info, new.imdb_rating,
        coalesce(new.genres, '{}'::text[]), new.addon_base_url, coalesce(new.added_at, 0)
    );
    return new;
end;
$$;

create trigger library_items_emit_event_aiud
after insert or update or delete on public.library_items
for each row execute function public.library_items_emit_event();

alter table public.library_items enable row level security;
alter table public.library_items_events enable row level security;

revoke all on table public.library_items from public, anon, authenticated;
revoke all on table public.library_items_events from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- RPCs
-- ---------------------------------------------------------------------------

create or replace function public.sync_get_library_delta_cursor(
    p_profile_id integer
)
returns bigint
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.assert_sync_profile_access(p_profile_id);
begin
    return coalesce((
        select max(event.event_id)
        from public.library_items_events as event
        where event.user_id = v_owner_id
          and event.profile_id = p_profile_id
    ), 0);
end;
$$;

create or replace function public.sync_pull_library_delta(
    p_profile_id integer,
    p_since_event_id bigint,
    p_limit integer
)
returns table (
    event_id bigint,
    operation text,
    content_id text,
    content_type text,
    name text,
    poster text,
    poster_shape text,
    background text,
    description text,
    release_info text,
    imdb_rating real,
    genres text[],
    addon_base_url text,
    added_at bigint
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.assert_sync_profile_access(p_profile_id);
    v_limit integer := greatest(coalesce(p_limit, 900), 1);
    v_since bigint := coalesce(p_since_event_id, 0);
begin
    return query
    select
        event.event_id,
        event.operation,
        event.content_id,
        event.content_type,
        event.name,
        event.poster,
        event.poster_shape,
        event.background,
        event.description,
        event.release_info,
        event.imdb_rating,
        event.genres,
        event.addon_base_url,
        event.added_at
    from public.library_items_events as event
    where event.user_id = v_owner_id
      and event.profile_id = p_profile_id
      and event.event_id > v_since
    order by event.event_id asc
    limit v_limit;
end;
$$;

create or replace function public.sync_push_library_items(
    p_items jsonb,
    p_profile_id integer,
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
    v_item jsonb;
    v_content_id text;
    v_content_type text;
    v_genres text[];
    v_added_at bigint;
begin
    if p_items is null or jsonb_typeof(p_items) <> 'array' then
        raise exception 'p_items must be a JSON array' using errcode = '22023';
    end if;

    for v_item in
        select value
        from jsonb_array_elements(p_items)
    loop
        v_content_id := nullif(trim(coalesce(v_item->>'content_id', '')), '');
        v_content_type := nullif(trim(coalesce(v_item->>'content_type', '')), '');
        if v_content_id is null or v_content_type is null then
            raise exception 'content_id and content_type are required' using errcode = '22023';
        end if;

        select coalesce(array_agg(elem), '{}'::text[])
        into v_genres
        from jsonb_array_elements_text(
            case
                when jsonb_typeof(v_item->'genres') = 'array' then v_item->'genres'
                else '[]'::jsonb
            end
        ) as elem;

        v_added_at := coalesce((v_item->>'added_at')::bigint, 0);

        insert into public.library_items (
            user_id,
            profile_id,
            content_id,
            content_type,
            name,
            poster,
            poster_shape,
            background,
            description,
            release_info,
            imdb_rating,
            genres,
            addon_base_url,
            added_at
        )
        values (
            v_owner_id,
            p_profile_id,
            v_content_id,
            v_content_type,
            coalesce(v_item->>'name', ''),
            nullif(v_item->>'poster', ''),
            coalesce(nullif(trim(v_item->>'poster_shape'), ''), 'POSTER'),
            nullif(v_item->>'background', ''),
            nullif(v_item->>'description', ''),
            nullif(v_item->>'release_info', ''),
            case
                when v_item ? 'imdb_rating'
                    and jsonb_typeof(v_item->'imdb_rating') <> 'null'
                then (v_item->>'imdb_rating')::real
                else null
            end,
            coalesce(v_genres, '{}'::text[]),
            nullif(v_item->>'addon_base_url', ''),
            v_added_at
        )
        on conflict (user_id, profile_id, content_id, content_type) do update
        set name = excluded.name,
            poster = excluded.poster,
            poster_shape = excluded.poster_shape,
            background = excluded.background,
            description = excluded.description,
            release_info = excluded.release_info,
            imdb_rating = excluded.imdb_rating,
            genres = excluded.genres,
            addon_base_url = excluded.addon_base_url,
            added_at = excluded.added_at
        where public.library_items.added_at <= excluded.added_at;
    end loop;
end;
$$;

create or replace function public.sync_pull_library(
    p_profile_id integer,
    p_limit integer,
    p_offset integer
)
returns table (
    id uuid,
    user_id uuid,
    content_id text,
    content_type text,
    name text,
    poster text,
    poster_shape text,
    background text,
    description text,
    release_info text,
    imdb_rating real,
    genres text[],
    addon_base_url text,
    added_at bigint,
    profile_id integer
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.assert_sync_profile_access(p_profile_id);
    v_limit integer := greatest(coalesce(p_limit, 900), 1);
    v_offset integer := greatest(coalesce(p_offset, 0), 0);
begin
    return query
    select
        row.id,
        row.user_id,
        row.content_id,
        row.content_type,
        row.name,
        row.poster,
        row.poster_shape,
        row.background,
        row.description,
        row.release_info,
        row.imdb_rating,
        row.genres,
        row.addon_base_url,
        row.added_at,
        row.profile_id
    from public.library_items as row
    where row.user_id = v_owner_id
      and row.profile_id = p_profile_id
    order by row.added_at desc, row.content_id asc, row.content_type asc
    offset v_offset
    limit v_limit;
end;
$$;

create or replace function public.sync_delete_library_items(
    p_keys jsonb,
    p_profile_id integer,
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
    v_key jsonb;
    v_content_id text;
    v_content_type text;
begin
    if p_keys is null or jsonb_typeof(p_keys) <> 'array' then
        raise exception 'p_keys must be a JSON array' using errcode = '22023';
    end if;

    for v_key in
        select value
        from jsonb_array_elements(p_keys)
    loop
        v_content_id := nullif(trim(coalesce(v_key->>'content_id', '')), '');
        v_content_type := nullif(trim(coalesce(v_key->>'content_type', '')), '');
        if v_content_id is null or v_content_type is null then
            continue;
        end if;

        delete from public.library_items as row
        where row.user_id = v_owner_id
          and row.profile_id = p_profile_id
          and row.content_id = v_content_id
          and row.content_type = v_content_type;
    end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- Overview counts (keep watch/watched counts from prior migration)
-- ---------------------------------------------------------------------------

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
    v_library_items jsonb;
    v_watch_progress jsonb;
    v_watched_items jsonb;
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
        jsonb_object_agg(counts.profile_id::text, counts.item_count),
        '{}'::jsonb
    )
    into v_library_items
    from (
        select row.profile_id, count(*)::integer as item_count
        from public.library_items as row
        where row.user_id = v_owner_id
        group by row.profile_id
    ) as counts;

    select coalesce(
        jsonb_object_agg(counts.profile_id::text, counts.item_count),
        '{}'::jsonb
    )
    into v_watch_progress
    from (
        select row.profile_id, count(*)::integer as item_count
        from public.watch_progress as row
        where row.user_id = v_owner_id
        group by row.profile_id
    ) as counts;

    select coalesce(
        jsonb_object_agg(counts.profile_id::text, counts.item_count),
        '{}'::jsonb
    )
    into v_watched_items
    from (
        select row.profile_id, count(*)::integer as item_count
        from public.watched_items as row
        where row.user_id = v_owner_id
        group by row.profile_id
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
        'library_items', v_library_items,
        'watch_progress', v_watch_progress,
        'watched_items', v_watched_items,
        'profiles', v_profiles
    );
end;
$$;

revoke all on function public.library_items_emit_event() from public, anon, authenticated;
revoke all on function public.sync_get_library_delta_cursor(integer) from public, anon, authenticated;
revoke all on function public.sync_pull_library_delta(integer, bigint, integer) from public, anon, authenticated;
revoke all on function public.sync_push_library_items(jsonb, integer, text) from public, anon, authenticated;
revoke all on function public.sync_pull_library(integer, integer, integer) from public, anon, authenticated;
revoke all on function public.sync_delete_library_items(jsonb, integer, text) from public, anon, authenticated;

grant execute on function public.sync_get_library_delta_cursor(integer) to authenticated;
grant execute on function public.sync_pull_library_delta(integer, bigint, integer) to authenticated;
grant execute on function public.sync_push_library_items(jsonb, integer, text) to authenticated;
grant execute on function public.sync_pull_library(integer, integer, integer) to authenticated;
grant execute on function public.sync_delete_library_items(jsonb, integer, text) to authenticated;
