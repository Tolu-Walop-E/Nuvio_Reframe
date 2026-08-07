-- Continue Watching (watch_progress) + watched-item badge sync.
-- Matches Android WatchProgressSyncService / WatchedItemsSyncService RPC contracts.

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

create table public.watch_progress (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id integer not null default 1 check (profile_id between 1 and 6),
    content_id text not null,
    content_type text not null,
    video_id text not null default '',
    season integer,
    episode integer,
    position bigint not null default 0,
    duration bigint not null default 0,
    last_watched bigint not null default 0,
    progress_key text not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, profile_id, progress_key),
    foreign key (user_id, profile_id)
        references public.profiles(user_id, profile_index)
        on delete cascade
);

create index watch_progress_owner_profile_last_watched_idx
    on public.watch_progress (user_id, profile_id, last_watched desc);

create table public.watch_progress_events (
    event_id bigserial primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id integer not null check (profile_id between 1 and 6),
    operation text not null check (operation in ('upsert', 'delete')),
    progress_key text not null,
    content_id text not null default '',
    content_type text not null default '',
    video_id text not null default '',
    season integer,
    episode integer,
    position bigint not null default 0,
    duration bigint not null default 0,
    last_watched bigint not null default 0,
    created_at timestamptz not null default now(),
    foreign key (user_id, profile_id)
        references public.profiles(user_id, profile_index)
        on delete cascade
);

create index watch_progress_events_owner_profile_event_idx
    on public.watch_progress_events (user_id, profile_id, event_id);

create table public.watched_items (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id integer not null default 1 check (profile_id between 1 and 6),
    content_id text not null,
    content_type text not null,
    title text not null default '',
    season integer,
    episode integer,
    watched_at bigint not null,
    created_at timestamptz not null default now(),
    foreign key (user_id, profile_id)
        references public.profiles(user_id, profile_index)
        on delete cascade
);

create unique index watched_items_identity_uidx
    on public.watched_items (
        user_id,
        profile_id,
        content_id,
        (coalesce(season, -1)),
        (coalesce(episode, -1))
    );

create index watched_items_owner_profile_watched_at_idx
    on public.watched_items (user_id, profile_id, watched_at desc, content_id);

create table public.watched_items_events (
    event_id bigserial primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_id integer not null check (profile_id between 1 and 6),
    operation text not null check (operation in ('upsert', 'delete')),
    content_id text not null,
    content_type text not null default '',
    title text not null default '',
    season integer,
    episode integer,
    watched_at bigint not null default 0,
    created_at timestamptz not null default now(),
    foreign key (user_id, profile_id)
        references public.profiles(user_id, profile_index)
        on delete cascade
);

create index watched_items_events_owner_profile_event_idx
    on public.watched_items_events (user_id, profile_id, event_id);

create trigger watch_progress_set_updated_at
before update on public.watch_progress
for each row execute function public.set_updated_at();

-- ---------------------------------------------------------------------------
-- Delta event triggers
-- ---------------------------------------------------------------------------

create or replace function public.watch_progress_emit_event()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if tg_op = 'DELETE' then
        insert into public.watch_progress_events (
            user_id, profile_id, operation, progress_key,
            content_id, content_type, video_id, season, episode,
            position, duration, last_watched
        ) values (
            old.user_id, old.profile_id, 'delete', old.progress_key,
            old.content_id, old.content_type, old.video_id, old.season, old.episode,
            coalesce(old.position, 0), coalesce(old.duration, 0), coalesce(old.last_watched, 0)
        );
        return old;
    end if;

    insert into public.watch_progress_events (
        user_id, profile_id, operation, progress_key,
        content_id, content_type, video_id, season, episode,
        position, duration, last_watched
    ) values (
        new.user_id, new.profile_id, 'upsert', new.progress_key,
        new.content_id, new.content_type, new.video_id, new.season, new.episode,
        coalesce(new.position, 0), coalesce(new.duration, 0), coalesce(new.last_watched, 0)
    );
    return new;
end;
$$;

create trigger watch_progress_emit_event_aiud
after insert or update or delete on public.watch_progress
for each row execute function public.watch_progress_emit_event();

create or replace function public.watched_items_emit_event()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if tg_op = 'DELETE' then
        insert into public.watched_items_events (
            user_id, profile_id, operation,
            content_id, content_type, title, season, episode, watched_at
        ) values (
            old.user_id, old.profile_id, 'delete',
            old.content_id, old.content_type, old.title, old.season, old.episode,
            coalesce(old.watched_at, 0)
        );
        return old;
    end if;

    insert into public.watched_items_events (
        user_id, profile_id, operation,
        content_id, content_type, title, season, episode, watched_at
    ) values (
        new.user_id, new.profile_id, 'upsert',
        new.content_id, new.content_type, new.title, new.season, new.episode,
        coalesce(new.watched_at, 0)
    );
    return new;
end;
$$;

create trigger watched_items_emit_event_aiud
after insert or update or delete on public.watched_items
for each row execute function public.watched_items_emit_event();

alter table public.watch_progress enable row level security;
alter table public.watch_progress_events enable row level security;
alter table public.watched_items enable row level security;
alter table public.watched_items_events enable row level security;

revoke all on table public.watch_progress from public, anon, authenticated;
revoke all on table public.watch_progress_events from public, anon, authenticated;
revoke all on table public.watched_items from public, anon, authenticated;
revoke all on table public.watched_items_events from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Shared helpers
-- ---------------------------------------------------------------------------

create or replace function public.assert_sync_profile_access(
    p_profile_id integer,
    p_require_origin boolean default false,
    p_origin_client_id text default null
)
returns uuid
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

    if p_require_origin then
        if p_origin_client_id is null
            or p_origin_client_id !~ '^[A-Za-z0-9_-]{16,96}$' then
            raise exception 'Invalid p_origin_client_id' using errcode = '22023';
        end if;
    end if;

    if not exists (
        select 1
        from public.profiles as profile
        where profile.user_id = v_owner_id
          and profile.profile_index = p_profile_id
    ) then
        raise exception 'Profile does not exist' using errcode = '22023';
    end if;

    return v_owner_id;
end;
$$;

-- ---------------------------------------------------------------------------
-- Watch progress RPCs
-- ---------------------------------------------------------------------------

create or replace function public.sync_get_watch_progress_delta_cursor(
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
        from public.watch_progress_events as event
        where event.user_id = v_owner_id
          and event.profile_id = p_profile_id
    ), 0);
end;
$$;

create or replace function public.sync_pull_watch_progress_delta(
    p_profile_id integer,
    p_since_event_id bigint,
    p_limit integer
)
returns table (
    event_id bigint,
    operation text,
    progress_key text,
    content_id text,
    content_type text,
    video_id text,
    season integer,
    episode integer,
    "position" bigint,
    duration bigint,
    last_watched bigint
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
        event.progress_key,
        event.content_id,
        event.content_type,
        event.video_id,
        event.season,
        event.episode,
        event."position",
        event.duration,
        event.last_watched
    from public.watch_progress_events as event
    where event.user_id = v_owner_id
      and event.profile_id = p_profile_id
      and event.event_id > v_since
    order by event.event_id asc
    limit v_limit;
end;
$$;

create or replace function public.sync_push_watch_progress(
    p_entries jsonb,
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
    v_entry jsonb;
    v_progress_key text;
    v_last_watched bigint;
begin
    if p_entries is null or jsonb_typeof(p_entries) <> 'array' then
        raise exception 'p_entries must be a JSON array' using errcode = '22023';
    end if;

    for v_entry in
        select value
        from jsonb_array_elements(p_entries)
    loop
        v_progress_key := nullif(trim(coalesce(v_entry->>'progress_key', '')), '');
        if v_progress_key is null then
            raise exception 'progress_key is required' using errcode = '22023';
        end if;

        v_last_watched := coalesce((v_entry->>'last_watched')::bigint, 0);

        insert into public.watch_progress (
            user_id,
            profile_id,
            content_id,
            content_type,
            video_id,
            season,
            episode,
            position,
            duration,
            last_watched,
            progress_key
        )
        values (
            v_owner_id,
            p_profile_id,
            coalesce(nullif(trim(v_entry->>'content_id'), ''), ''),
            coalesce(nullif(trim(v_entry->>'content_type'), ''), 'movie'),
            coalesce(nullif(trim(v_entry->>'video_id'), ''), ''),
            nullif(v_entry->>'season', '')::integer,
            nullif(v_entry->>'episode', '')::integer,
            coalesce((v_entry->>'position')::bigint, 0),
            coalesce((v_entry->>'duration')::bigint, 0),
            v_last_watched,
            v_progress_key
        )
        on conflict (user_id, profile_id, progress_key) do update
        set content_id = excluded.content_id,
            content_type = excluded.content_type,
            video_id = excluded.video_id,
            season = excluded.season,
            episode = excluded.episode,
            position = excluded.position,
            duration = excluded.duration,
            last_watched = excluded.last_watched
        where public.watch_progress.last_watched <= excluded.last_watched;
    end loop;
end;
$$;

create or replace function public.sync_pull_watch_progress(
    p_profile_id integer,
    p_since_last_watched bigint default null,
    p_limit integer default null
)
returns table (
    id uuid,
    user_id uuid,
    content_id text,
    content_type text,
    video_id text,
    season integer,
    episode integer,
    "position" bigint,
    duration bigint,
    last_watched bigint,
    progress_key text,
    profile_id integer
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
        row.id,
        row.user_id,
        row.content_id,
        row.content_type,
        row.video_id,
        row.season,
        row.episode,
        row."position",
        row.duration,
        row.last_watched,
        row.progress_key,
        row.profile_id
    from public.watch_progress as row
    where row.user_id = v_owner_id
      and row.profile_id = p_profile_id
      and (
          p_since_last_watched is null
          or row.last_watched > p_since_last_watched
      )
    order by row.last_watched desc, row.progress_key asc
    limit case when p_limit is null then null else greatest(p_limit, 1) end;
end;
$$;

create or replace function public.sync_delete_watch_progress(
    p_keys text[],
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
begin
    if p_keys is null then
        raise exception 'p_keys is required' using errcode = '22023';
    end if;

    delete from public.watch_progress as row
    where row.user_id = v_owner_id
      and row.profile_id = p_profile_id
      and row.progress_key = any (p_keys);
end;
$$;

-- ---------------------------------------------------------------------------
-- Watched items RPCs
-- ---------------------------------------------------------------------------

create or replace function public.sync_get_watched_items_delta_cursor(
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
        from public.watched_items_events as event
        where event.user_id = v_owner_id
          and event.profile_id = p_profile_id
    ), 0);
end;
$$;

create or replace function public.sync_pull_watched_items_delta(
    p_profile_id integer,
    p_since_event_id bigint,
    p_limit integer
)
returns table (
    event_id bigint,
    operation text,
    content_id text,
    content_type text,
    title text,
    season integer,
    episode integer,
    watched_at bigint
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
        event.title,
        event.season,
        event.episode,
        event.watched_at
    from public.watched_items_events as event
    where event.user_id = v_owner_id
      and event.profile_id = p_profile_id
      and event.event_id > v_since
    order by event.event_id asc
    limit v_limit;
end;
$$;

create or replace function public.sync_push_watched_items(
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
    v_season integer;
    v_episode integer;
    v_watched_at bigint;
begin
    if p_items is null or jsonb_typeof(p_items) <> 'array' then
        raise exception 'p_items must be a JSON array' using errcode = '22023';
    end if;

    for v_item in
        select value
        from jsonb_array_elements(p_items)
    loop
        v_content_id := nullif(trim(coalesce(v_item->>'content_id', '')), '');
        if v_content_id is null then
            raise exception 'content_id is required' using errcode = '22023';
        end if;

        v_season := case
            when v_item ? 'season' and jsonb_typeof(v_item->'season') <> 'null'
                then (v_item->>'season')::integer
            else null
        end;
        v_episode := case
            when v_item ? 'episode' and jsonb_typeof(v_item->'episode') <> 'null'
                then (v_item->>'episode')::integer
            else null
        end;
        v_watched_at := coalesce((v_item->>'watched_at')::bigint, 0);

        insert into public.watched_items (
            user_id,
            profile_id,
            content_id,
            content_type,
            title,
            season,
            episode,
            watched_at
        )
        values (
            v_owner_id,
            p_profile_id,
            v_content_id,
            coalesce(nullif(trim(v_item->>'content_type'), ''), 'movie'),
            coalesce(v_item->>'title', ''),
            v_season,
            v_episode,
            v_watched_at
        )
        on conflict (user_id, profile_id, content_id, (coalesce(season, -1)), (coalesce(episode, -1)))
        do update
        set content_type = excluded.content_type,
            title = excluded.title,
            watched_at = excluded.watched_at
        where public.watched_items.watched_at <= excluded.watched_at;
    end loop;
end;
$$;

create or replace function public.sync_pull_watched_items(
    p_profile_id integer,
    p_page integer,
    p_page_size integer
)
returns table (
    id uuid,
    user_id uuid,
    content_id text,
    content_type text,
    title text,
    season integer,
    episode integer,
    watched_at bigint,
    profile_id integer
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_owner_id uuid := public.assert_sync_profile_access(p_profile_id);
    v_page integer := greatest(coalesce(p_page, 1), 1);
    v_page_size integer := greatest(coalesce(p_page_size, 900), 1);
begin
    return query
    select
        row.id,
        row.user_id,
        row.content_id,
        row.content_type,
        row.title,
        row.season,
        row.episode,
        row.watched_at,
        row.profile_id
    from public.watched_items as row
    where row.user_id = v_owner_id
      and row.profile_id = p_profile_id
    order by row.watched_at desc, row.content_id asc, row.season nulls first, row.episode nulls first
    offset (v_page - 1) * v_page_size
    limit v_page_size;
end;
$$;

create or replace function public.sync_delete_watched_items(
    p_profile_id integer,
    p_keys jsonb,
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
    v_season integer;
    v_episode integer;
begin
    if p_keys is null or jsonb_typeof(p_keys) <> 'array' then
        raise exception 'p_keys must be a JSON array' using errcode = '22023';
    end if;

    for v_key in
        select value
        from jsonb_array_elements(p_keys)
    loop
        v_content_id := nullif(trim(coalesce(v_key->>'content_id', '')), '');
        if v_content_id is null then
            continue;
        end if;

        v_season := case
            when v_key ? 'season' and jsonb_typeof(v_key->'season') <> 'null'
                then (v_key->>'season')::integer
            else null
        end;
        v_episode := case
            when v_key ? 'episode' and jsonb_typeof(v_key->'episode') <> 'null'
                then (v_key->>'episode')::integer
            else null
        end;

        delete from public.watched_items as row
        where row.user_id = v_owner_id
          and row.profile_id = p_profile_id
          and row.content_id = v_content_id
          and row.season is not distinct from v_season
          and row.episode is not distinct from v_episode;
    end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- Overview counts
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
        'library_items', '{}'::jsonb,
        'watch_progress', v_watch_progress,
        'watched_items', v_watched_items,
        'profiles', v_profiles
    );
end;
$$;

-- ---------------------------------------------------------------------------
-- Grants
-- ---------------------------------------------------------------------------

revoke all on function public.assert_sync_profile_access(integer, boolean, text) from public, anon, authenticated;
revoke all on function public.watch_progress_emit_event() from public, anon, authenticated;
revoke all on function public.watched_items_emit_event() from public, anon, authenticated;

revoke all on function public.sync_get_watch_progress_delta_cursor(integer) from public, anon, authenticated;
revoke all on function public.sync_pull_watch_progress_delta(integer, bigint, integer) from public, anon, authenticated;
revoke all on function public.sync_push_watch_progress(jsonb, integer, text) from public, anon, authenticated;
revoke all on function public.sync_pull_watch_progress(integer, bigint, integer) from public, anon, authenticated;
revoke all on function public.sync_delete_watch_progress(text[], integer, text) from public, anon, authenticated;

revoke all on function public.sync_get_watched_items_delta_cursor(integer) from public, anon, authenticated;
revoke all on function public.sync_pull_watched_items_delta(integer, bigint, integer) from public, anon, authenticated;
revoke all on function public.sync_push_watched_items(jsonb, integer, text) from public, anon, authenticated;
revoke all on function public.sync_pull_watched_items(integer, integer, integer) from public, anon, authenticated;
revoke all on function public.sync_delete_watched_items(integer, jsonb, text) from public, anon, authenticated;

grant execute on function public.sync_get_watch_progress_delta_cursor(integer) to authenticated;
grant execute on function public.sync_pull_watch_progress_delta(integer, bigint, integer) to authenticated;
grant execute on function public.sync_push_watch_progress(jsonb, integer, text) to authenticated;
grant execute on function public.sync_pull_watch_progress(integer, bigint, integer) to authenticated;
grant execute on function public.sync_delete_watch_progress(text[], integer, text) to authenticated;

grant execute on function public.sync_get_watched_items_delta_cursor(integer) to authenticated;
grant execute on function public.sync_pull_watched_items_delta(integer, bigint, integer) to authenticated;
grant execute on function public.sync_push_watched_items(jsonb, integer, text) to authenticated;
grant execute on function public.sync_pull_watched_items(integer, integer, integer) to authenticated;
grant execute on function public.sync_delete_watched_items(integer, jsonb, text) to authenticated;
