begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(28);

-- ---------------------------------------------------------------------------
-- Fixtures: owner A, foreign user B, linked device for A
-- ---------------------------------------------------------------------------

delete from auth.users
where id in (
    '11000000-0000-0000-0000-000000000001'::uuid,
    '22000000-0000-0000-0000-000000000002'::uuid,
    '33000000-0000-0000-0000-000000000003'::uuid
);

insert into auth.users (
    instance_id,
    id,
    aud,
    role,
    email,
    encrypted_password,
    email_confirmed_at,
    raw_app_meta_data,
    raw_user_meta_data,
    created_at,
    updated_at
)
values
    (
        '00000000-0000-0000-0000-000000000000'::uuid,
        '11000000-0000-0000-0000-000000000001'::uuid,
        'authenticated',
        'authenticated',
        'watch-sync-user-a@example.test',
        crypt('watch-password-a', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    ),
    (
        '00000000-0000-0000-0000-000000000000'::uuid,
        '22000000-0000-0000-0000-000000000002'::uuid,
        'authenticated',
        'authenticated',
        'watch-sync-user-b@example.test',
        crypt('watch-password-b', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    ),
    (
        '00000000-0000-0000-0000-000000000000'::uuid,
        '33000000-0000-0000-0000-000000000003'::uuid,
        'authenticated',
        'authenticated',
        'watch-sync-linked@example.test',
        crypt('watch-password-device', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    );

insert into public.profiles (user_id, profile_index, name, avatar_color_hex)
values
    ('11000000-0000-0000-0000-000000000001', 1, 'Owner A', '#111111'),
    ('22000000-0000-0000-0000-000000000002', 1, 'Owner B', '#222222');

insert into public.linked_devices (owner_id, device_user_id, device_name)
values (
    '11000000-0000-0000-0000-000000000001',
    '33000000-0000-0000-0000-000000000003',
    'Watch sync linked device'
);

select set_config('request.jwt.claim.sub', '11000000-0000-0000-0000-000000000001', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
select set_config('role', 'authenticated', true);

-- ---------------------------------------------------------------------------
-- Watch progress push / pull / cursor / delta / delete
-- ---------------------------------------------------------------------------

select lives_ok(
    $$
        select public.sync_push_watch_progress(
            '[
                {
                    "content_id":"tt100",
                    "content_type":"movie",
                    "video_id":"tt100",
                    "position":1200,
                    "duration":5400,
                    "last_watched":1000,
                    "progress_key":"movie:tt100"
                },
                {
                    "content_id":"tt200",
                    "content_type":"series",
                    "video_id":"tt200:1:2",
                    "season":1,
                    "episode":2,
                    "position":800,
                    "duration":2400,
                    "last_watched":2000,
                    "progress_key":"series:tt200:1:2"
                }
            ]'::jsonb,
            1,
            'watch-client-origin-01'
        )
    $$,
    'owner can push watch progress'
);

select is(
    (
        select count(*)::integer
        from public.sync_pull_watch_progress(1)
    ),
    2,
    'owner pulls both watch progress rows'
);

select is(
    (
        select last_watched
        from public.sync_pull_watch_progress(1, 1500, null)
        where progress_key = 'series:tt200:1:2'
    ),
    2000::bigint,
    'snapshot since filter keeps newer progress'
);

select is(
    public.sync_get_watch_progress_delta_cursor(1) > 0,
    true,
    'watch progress delta cursor advances after push'
);

select is(
    (
        select count(*)::integer
        from public.sync_pull_watch_progress_delta(1, 0, 50)
        where operation = 'upsert'
    ),
    2,
    'watch progress delta exposes upsert events'
);

select lives_ok(
    $$
        select public.sync_push_watch_progress(
            '[
                {
                    "content_id":"tt100",
                    "content_type":"movie",
                    "video_id":"tt100",
                    "position":100,
                    "duration":5400,
                    "last_watched":500,
                    "progress_key":"movie:tt100"
                }
            ]'::jsonb,
            1,
            'watch-client-origin-01'
        )
    $$,
    'stale push is accepted without error'
);

select is(
    (
        select position
        from public.sync_pull_watch_progress(1)
        where progress_key = 'movie:tt100'
    ),
    1200::bigint,
    'stale watch progress push loses to newer last_watched'
);

select lives_ok(
    $$
        select public.sync_delete_watch_progress(
            array['movie:tt100'],
            1,
            'watch-client-origin-01'
        )
    $$,
    'owner can delete watch progress keys'
);

select is(
    (
        select count(*)::integer
        from public.sync_pull_watch_progress(1)
        where progress_key = 'movie:tt100'
    ),
    0,
    'deleted watch progress is gone from snapshot'
);

select ok(
    exists (
        select 1
        from public.sync_pull_watch_progress_delta(1, 0, 100)
        where operation = 'delete'
          and progress_key = 'movie:tt100'
    ),
    'delete emits a watch progress delta event'
);

-- ---------------------------------------------------------------------------
-- Watched items push / pull / cursor / delta / delete
-- ---------------------------------------------------------------------------

select lives_ok(
    $$
        select public.sync_push_watched_items(
            '[
                {
                    "content_id":"tt100",
                    "content_type":"movie",
                    "title":"Movie One",
                    "season":null,
                    "episode":null,
                    "watched_at":3000
                },
                {
                    "content_id":"tt200",
                    "content_type":"series",
                    "title":"Show Two",
                    "season":1,
                    "episode":2,
                    "watched_at":4000
                }
            ]'::jsonb,
            1,
            'watch-client-origin-01'
        )
    $$,
    'owner can push watched items'
);

select is(
    (
        select count(*)::integer
        from public.sync_pull_watched_items(1, 1, 50)
    ),
    2,
    'owner pulls both watched items'
);

select is(
    public.sync_get_watched_items_delta_cursor(1) > 0,
    true,
    'watched items delta cursor advances after push'
);

select is(
    (
        select count(*)::integer
        from public.sync_pull_watched_items_delta(1, 0, 50)
        where operation = 'upsert'
    ),
    2,
    'watched items delta exposes upsert events'
);

select lives_ok(
    $$
        select public.sync_delete_watched_items(
            1,
            '[{"content_id":"tt100"},{"content_id":"tt200","season":1,"episode":2}]'::jsonb,
            'watch-client-origin-01'
        )
    $$,
    'owner can delete watched items by key'
);

select is(
    (
        select count(*)::integer
        from public.sync_pull_watched_items(1, 1, 50)
    ),
    0,
    'deleted watched items are gone from snapshot'
);

-- ---------------------------------------------------------------------------
-- Overview counts + linked device read path
-- ---------------------------------------------------------------------------

select lives_ok(
    $$
        select public.sync_push_watch_progress(
            '[{
                "content_id":"tt999",
                "content_type":"movie",
                "video_id":"tt999",
                "position":10,
                "duration":100,
                "last_watched":9000,
                "progress_key":"movie:tt999"
            }]'::jsonb,
            1,
            'watch-client-origin-01'
        )
    $$,
    'reseed one progress row for overview'
);

select lives_ok(
    $$
        select public.sync_push_watched_items(
            '[{
                "content_id":"tt999",
                "content_type":"movie",
                "title":"Movie Nine",
                "season":null,
                "episode":null,
                "watched_at":9001
            }]'::jsonb,
            1,
            'watch-client-origin-01'
        )
    $$,
    'reseed one watched item for overview'
);

select is(
    (
        select (public.get_sync_overview()->'watch_progress'->>'1')::integer
    ),
    2,
    'overview counts remaining watch progress for profile 1'
);

select is(
    (
        select (public.get_sync_overview()->'watched_items'->>'1')::integer
    ),
    1,
    'overview counts remaining watched items for profile 1'
);

select set_config('request.jwt.claim.sub', '33000000-0000-0000-0000-000000000003', true);

select is(
    (
        select count(*)::integer
        from public.sync_pull_watch_progress(1)
        where progress_key = 'movie:tt999'
    ),
    1,
    'linked device can pull owner watch progress'
);

select is(
    (
        select count(*)::integer
        from public.sync_pull_watched_items(1, 1, 50)
        where content_id = 'tt999'
    ),
    1,
    'linked device can pull owner watched items'
);

select lives_ok(
    $$
        select public.sync_push_watch_progress(
            '[{
                "content_id":"tt888",
                "content_type":"movie",
                "video_id":"tt888",
                "position":5,
                "duration":50,
                "last_watched":9100,
                "progress_key":"movie:tt888"
            }]'::jsonb,
            1,
            'watch-linked-origin-01'
        )
    $$,
    'linked device can push into owner watch progress'
);

-- ---------------------------------------------------------------------------
-- Isolation + grants
-- ---------------------------------------------------------------------------

select set_config('request.jwt.claim.sub', '22000000-0000-0000-0000-000000000002', true);

select is(
    (
        select count(*)::integer
        from public.sync_pull_watch_progress(1)
    ),
    0,
    'foreign user cannot see owner watch progress'
);

select is(
    (
        select count(*)::integer
        from public.sync_pull_watched_items(1, 1, 50)
    ),
    0,
    'foreign user cannot see owner watched items'
);

select ok(
    not has_table_privilege('authenticated', 'public.watch_progress', 'INSERT')
    and not has_table_privilege('authenticated', 'public.watched_items', 'INSERT')
    and not has_table_privilege('authenticated', 'public.watch_progress_events', 'SELECT')
    and not has_table_privilege('authenticated', 'public.watched_items_events', 'SELECT'),
    'authenticated has no direct watch sync table write/event grants'
);

select ok(
    has_function_privilege('authenticated', 'public.sync_push_watch_progress(jsonb,integer,text)', 'EXECUTE')
    and has_function_privilege('authenticated', 'public.sync_pull_watched_items(integer,integer,integer)', 'EXECUTE')
    and not has_function_privilege('anon', 'public.sync_pull_watch_progress(integer,bigint,integer)', 'EXECUTE'),
    'authenticated can execute watch sync RPCs; anon cannot'
);

select throws_ok(
    $$
        select public.sync_push_watch_progress('[]'::jsonb, 1, 'bad')
    $$,
    '22023',
    'Invalid p_origin_client_id',
    'origin client id is validated'
);

select * from finish();
rollback;
