begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(12);

delete from auth.users
where id in (
    '12120000-0000-0000-0000-000000000001'::uuid,
    '23230000-0000-0000-0000-000000000002'::uuid,
    '34340000-0000-0000-0000-000000000003'::uuid
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
        '12120000-0000-0000-0000-000000000001'::uuid,
        'authenticated',
        'authenticated',
        'settings-blob-user-a@example.test',
        crypt('settings-password-a', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    ),
    (
        '00000000-0000-0000-0000-000000000000'::uuid,
        '23230000-0000-0000-0000-000000000002'::uuid,
        'authenticated',
        'authenticated',
        'settings-blob-user-b@example.test',
        crypt('settings-password-b', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    ),
    (
        '00000000-0000-0000-0000-000000000000'::uuid,
        '34340000-0000-0000-0000-000000000003'::uuid,
        'authenticated',
        'authenticated',
        'settings-blob-linked@example.test',
        crypt('settings-password-device', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    );

insert into public.profiles (user_id, profile_index, name, avatar_color_hex)
values
    ('12120000-0000-0000-0000-000000000001', 1, 'Settings Owner', '#555555'),
    ('23230000-0000-0000-0000-000000000002', 1, 'Settings Other', '#666666');

insert into public.linked_devices (owner_id, device_user_id, device_name)
values (
    '12120000-0000-0000-0000-000000000001',
    '34340000-0000-0000-0000-000000000003',
    'Settings linked device'
);

select set_config('request.jwt.claim.sub', '12120000-0000-0000-0000-000000000001', true);
select set_config('request.jwt.claim.role', 'authenticated', true);
select set_config('role', 'authenticated', true);

select lives_ok(
    $$
        select public.sync_push_profile_settings_blob(
            1,
            '{"features":{"theme_settings":{"theme":"slate"}}}'::jsonb,
            'tv',
            'settings-client-orig01'
        )
    $$,
    'owner can push profile settings blob'
);

select is(
    (
        select settings_json
        from public.sync_pull_profile_settings_blob(1, 'tv')
    ),
    '{"features":{"theme_settings":{"theme":"slate"}}}'::jsonb,
    'owner can pull profile settings blob'
);

select is(
    (
        select count(*)::integer
        from public.sync_pull_profile_settings_blob(1, 'mobile')
    ),
    0,
    'platform mismatch returns empty'
);

select lives_ok(
    $$
        select public.sync_push_profile_settings_blob(
            1,
            '{"features":{"layout_settings":{"hero":true}}}'::jsonb,
            'tv',
            'settings-client-orig01'
        )
    $$,
    'owner can overwrite profile settings blob'
);

select is(
    (
        select settings_json->'features' ? 'layout_settings'
        from public.sync_pull_profile_settings_blob(1, 'tv')
    ),
    true,
    'overwrite replaces prior settings_json'
);

select set_config('request.jwt.claim.sub', '34340000-0000-0000-0000-000000000003', true);

select is(
    (
        select settings_json->'features' ? 'layout_settings'
        from public.sync_pull_profile_settings_blob(1, 'tv')
    ),
    true,
    'linked device can pull owner settings blob'
);

select lives_ok(
    $$
        select public.sync_push_profile_settings_blob(
            1,
            '{"features":{"theme_settings":{"theme":"gold"}}}'::jsonb,
            'tv',
            'settings-linked-orig01'
        )
    $$,
    'linked device can push owner settings blob'
);

select set_config('request.jwt.claim.sub', '23230000-0000-0000-0000-000000000002', true);

select is(
    (
        select count(*)::integer
        from public.sync_pull_profile_settings_blob(1, 'tv')
    ),
    0,
    'foreign user cannot see owner settings blob'
);

select throws_ok(
    $$
        select public.sync_push_profile_settings_blob(
            1,
            '[]'::jsonb,
            'tv',
            'settings-client-orig01'
        )
    $$,
    '22023',
    'p_settings_json must be a JSON object',
    'settings json must be an object'
);

select throws_ok(
    $$
        select public.sync_push_profile_settings_blob(
            1,
            '{}'::jsonb,
            'tv',
            'bad'
        )
    $$,
    '22023',
    'Invalid p_origin_client_id',
    'settings origin client id is validated'
);

select ok(
    not has_table_privilege('authenticated', 'public.profile_settings_blobs', 'INSERT')
    and not has_table_privilege('authenticated', 'public.profile_settings_blobs', 'SELECT'),
    'authenticated has no direct settings blob table grants'
);

select ok(
    has_function_privilege(
        'authenticated',
        'public.sync_push_profile_settings_blob(integer,jsonb,text,text)',
        'EXECUTE'
    )
    and not has_function_privilege(
        'anon',
        'public.sync_pull_profile_settings_blob(integer,text)',
        'EXECUTE'
    ),
    'authenticated can execute settings blob RPCs; anon cannot'
);

select * from finish();
rollback;
