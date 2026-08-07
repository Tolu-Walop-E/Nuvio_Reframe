begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(18);

delete from auth.users
where id in (
    '11110000-0000-0000-0000-000000000001'::uuid,
    '22220000-0000-0000-0000-000000000002'::uuid,
    '33330000-0000-0000-0000-000000000003'::uuid
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
        '11110000-0000-0000-0000-000000000001'::uuid,
        'authenticated',
        'authenticated',
        'library-sync-user-a@example.test',
        crypt('library-password-a', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    ),
    (
        '00000000-0000-0000-0000-000000000000'::uuid,
        '22220000-0000-0000-0000-000000000002'::uuid,
        'authenticated',
        'authenticated',
        'library-sync-user-b@example.test',
        crypt('library-password-b', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    ),
    (
        '00000000-0000-0000-0000-000000000000'::uuid,
        '33330000-0000-0000-0000-000000000003'::uuid,
        'authenticated',
        'authenticated',
        'library-sync-linked@example.test',
        crypt('library-password-device', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    );

insert into public.profiles (user_id, profile_index, name, avatar_color_hex)
values
    ('11110000-0000-0000-0000-000000000001', 1, 'Library Owner', '#333333'),
    ('22220000-0000-0000-0000-000000000002', 1, 'Library Other', '#444444');

insert into public.linked_devices (owner_id, device_user_id, device_name)
values (
    '11110000-0000-0000-0000-000000000001',
    '33330000-0000-0000-0000-000000000003',
    'Library linked device'
);

select set_config('request.jwt.claim.sub', '11110000-0000-0000-0000-000000000001', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
select set_config('role', 'authenticated', true);

select lives_ok(
    $$
        select public.sync_push_library_items(
            '[
                {
                    "content_id":"tt100",
                    "content_type":"movie",
                    "name":"Movie One",
                    "poster":"https://img.example/tt100.jpg",
                    "poster_shape":"POSTER",
                    "background":null,
                    "description":"A movie",
                    "release_info":"2024",
                    "imdb_rating":7.5,
                    "genres":["Action","Sci-Fi"],
                    "addon_base_url":"https://addon.example",
                    "added_at":1000
                },
                {
                    "content_id":"tt200",
                    "content_type":"series",
                    "name":"Show Two",
                    "poster":null,
                    "poster_shape":"LANDSCAPE",
                    "background":null,
                    "description":null,
                    "release_info":null,
                    "imdb_rating":null,
                    "genres":[],
                    "addon_base_url":null,
                    "added_at":2000
                }
            ]'::jsonb,
            1,
            'library-client-origin01'
        )
    $$,
    'owner can push library items'
);

select is(
    (
        select count(*)::integer
        from public.sync_pull_library(1, 50, 0)
    ),
    2,
    'owner pulls both library rows'
);

select is(
    (
        select genres
        from public.sync_pull_library(1, 50, 0)
        where content_id = 'tt100'
    ),
    array['Action', 'Sci-Fi']::text[],
    'genres round-trip as text[]'
);

select is(
    public.sync_get_library_delta_cursor(1) > 0,
    true,
    'library delta cursor advances after push'
);

select is(
    (
        select count(*)::integer
        from public.sync_pull_library_delta(1, 0, 50)
        where operation = 'upsert'
    ),
    2,
    'library delta exposes upsert events'
);

select lives_ok(
    $$
        select public.sync_push_library_items(
            '[{
                "content_id":"tt100",
                "content_type":"movie",
                "name":"Stale Name",
                "poster":null,
                "poster_shape":"POSTER",
                "background":null,
                "description":null,
                "release_info":null,
                "imdb_rating":null,
                "genres":[],
                "addon_base_url":null,
                "added_at":500
            }]'::jsonb,
            1,
            'library-client-origin01'
        )
    $$,
    'stale library push is accepted'
);

select is(
    (
        select name
        from public.sync_pull_library(1, 50, 0)
        where content_id = 'tt100'
    ),
    'Movie One',
    'stale library push loses to newer added_at'
);

select lives_ok(
    $$
        select public.sync_delete_library_items(
            '[{"content_id":"tt100","content_type":"movie"}]'::jsonb,
            1,
            'library-client-origin01'
        )
    $$,
    'owner can delete library items'
);

select is(
    (
        select count(*)::integer
        from public.sync_pull_library(1, 50, 0)
        where content_id = 'tt100'
    ),
    0,
    'deleted library item is gone from snapshot'
);

select ok(
    exists (
        select 1
        from public.sync_pull_library_delta(1, 0, 100)
        where operation = 'delete'
          and content_id = 'tt100'
          and content_type = 'movie'
    ),
    'delete emits a library delta event'
);

select is(
    (
        select (public.get_sync_overview()->'library_items'->>'1')::integer
    ),
    1,
    'overview counts remaining library items'
);

select set_config('request.jwt.claim.sub', '33330000-0000-0000-0000-000000000003', true);

select is(
    (
        select count(*)::integer
        from public.sync_pull_library(1, 50, 0)
        where content_id = 'tt200'
    ),
    1,
    'linked device can pull owner library'
);

select lives_ok(
    $$
        select public.sync_push_library_items(
            '[{
                "content_id":"tt300",
                "content_type":"movie",
                "name":"From Device",
                "poster":null,
                "poster_shape":"POSTER",
                "background":null,
                "description":null,
                "release_info":null,
                "imdb_rating":null,
                "genres":["Drama"],
                "addon_base_url":null,
                "added_at":3000
            }]'::jsonb,
            1,
            'library-linked-origin01'
        )
    $$,
    'linked device can push into owner library'
);

select set_config('request.jwt.claim.sub', '22220000-0000-0000-0000-000000000002', true);

select is(
    (
        select count(*)::integer
        from public.sync_pull_library(1, 50, 0)
    ),
    0,
    'foreign user cannot see owner library'
);

select ok(
    not has_table_privilege('authenticated', 'public.library_items', 'INSERT')
    and not has_table_privilege('authenticated', 'public.library_items_events', 'SELECT'),
    'authenticated has no direct library table grants'
);

select ok(
    has_function_privilege('authenticated', 'public.sync_push_library_items(jsonb,integer,text)', 'EXECUTE')
    and not has_function_privilege('anon', 'public.sync_pull_library(integer,integer,integer)', 'EXECUTE'),
    'authenticated can execute library RPCs; anon cannot'
);

select throws_ok(
    $$
        select public.sync_push_library_items('[]'::jsonb, 1, 'short')
    $$,
    '22023',
    'Invalid p_origin_client_id',
    'library origin client id is validated'
);

select is(
    (
        select count(*)::integer
        from public.sync_pull_library(1, 1, 0)
    ),
    0,
    'paging against empty foreign profile returns zero'
);

select * from finish();
rollback;
