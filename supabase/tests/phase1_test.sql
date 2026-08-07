begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(81);

delete from auth.users
where id in (
    '10000000-0000-0000-0000-000000000001'::uuid,
    '20000000-0000-0000-0000-000000000002'::uuid,
    '30000000-0000-0000-0000-000000000003'::uuid,
    '40000000-0000-0000-0000-000000000004'::uuid,
    '50000000-0000-0000-0000-000000000005'::uuid,
    '60000000-0000-0000-0000-000000000006'::uuid
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
        '10000000-0000-0000-0000-000000000001'::uuid,
        'authenticated',
        'authenticated',
        'phase1-user-a@example.test',
        crypt('phase1-password-a', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    ),
    (
        '00000000-0000-0000-0000-000000000000'::uuid,
        '20000000-0000-0000-0000-000000000002'::uuid,
        'authenticated',
        'authenticated',
        'phase1-user-b@example.test',
        crypt('phase1-password-b', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    ),
    (
        '00000000-0000-0000-0000-000000000000'::uuid,
        '30000000-0000-0000-0000-000000000003'::uuid,
        'authenticated',
        'authenticated',
        'phase1-linked-device@example.test',
        crypt('phase1-password-device', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    ),
    (
        '00000000-0000-0000-0000-000000000000'::uuid,
        '40000000-0000-0000-0000-000000000004'::uuid,
        'authenticated',
        'authenticated',
        'phase1-second-linked-device@example.test',
        crypt('phase1-password-device-2', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    ),
    (
        '00000000-0000-0000-0000-000000000000'::uuid,
        '50000000-0000-0000-0000-000000000005'::uuid,
        'authenticated',
        'authenticated',
        'phase1-unrelated-device@example.test',
        crypt('phase1-password-unrelated-device', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    ),
    (
        '00000000-0000-0000-0000-000000000000'::uuid,
        '60000000-0000-0000-0000-000000000006'::uuid,
        'authenticated',
        'authenticated',
        'phase1-profile-compatibility@example.test',
        crypt('phase1-password-profile-compatibility', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    );

insert into public.profiles (
    user_id,
    profile_index,
    name,
    avatar_color_hex
)
values
    ('10000000-0000-0000-0000-000000000001', 1, 'User A', '#2563EB'),
    ('10000000-0000-0000-0000-000000000001', 2, 'User A Two', '#16A34A'),
    ('20000000-0000-0000-0000-000000000002', 1, 'User B', '#DC2626');

insert into public.profile_locks (user_id, profile_index)
select profile.user_id, profile.profile_index
from public.profiles as profile;

select has_table(
    'public',
    'collection_blobs',
    'collection blob persistence exists'
);

select has_table(
    'public',
    'home_catalog_settings',
    'home catalog settings persistence exists'
);

insert into public.addons (
    user_id,
    profile_id,
    url,
    name
)
values
    ('10000000-0000-0000-0000-000000000001', 1, 'https://a.example/original.json', 'A original'),
    ('20000000-0000-0000-0000-000000000002', 1, 'https://b.example/original.json', 'B original');

insert into public.plugins (
    user_id,
    profile_id,
    url,
    name,
    repo_type
)
values
    ('10000000-0000-0000-0000-000000000001', 1, 'https://a.example/repository.json', 'A repository', 'OFFICIAL'),
    ('20000000-0000-0000-0000-000000000002', 1, 'https://b.example/repository.json', 'B repository', 'OFFICIAL');

insert into public.linked_devices (
    owner_id,
    device_user_id,
    device_name
)
values (
    '10000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000003',
    'Phase 1 linked device'
);

select is(
    (
        select owner_id
        from public.linked_devices
        where device_user_id = '30000000-0000-0000-0000-000000000003'
    ),
    '10000000-0000-0000-0000-000000000001'::uuid,
    'a device can be linked to one owner'
);

select throws_ok(
    $$
        insert into public.linked_devices (owner_id, device_user_id, device_name)
        values (
            '20000000-0000-0000-0000-000000000002',
            '30000000-0000-0000-0000-000000000003',
            'Conflicting owner attempt'
        )
    $$,
    '23505',
    'duplicate key value violates unique constraint "linked_devices_device_user_id_key"',
    'the same device cannot be linked to another owner'
);

select lives_ok(
    $$
        insert into public.linked_devices (owner_id, device_user_id, device_name)
        values (
            '10000000-0000-0000-0000-000000000001',
            '40000000-0000-0000-0000-000000000004',
            'Phase 1 second linked device'
        )
    $$,
    'one owner can link another device'
);

select is(
    (
        select count(*)
        from public.linked_devices
        where owner_id = '10000000-0000-0000-0000-000000000001'
    ),
    2::bigint,
    'one owner can have multiple linked devices'
);

select throws_ok(
    $$
        insert into public.linked_devices (owner_id, device_user_id, device_name)
        values (
            '20000000-0000-0000-0000-000000000002',
            '20000000-0000-0000-0000-000000000002',
            'Self link attempt'
        )
    $$,
    '23514',
    'new row for relation "linked_devices" violates check constraint "linked_devices_owner_device_check"',
    'a user cannot be linked to itself'
);

select ok(
    has_function_privilege('anon', 'public.get_avatar_catalog()', 'EXECUTE'),
    'anonymous role can execute get_avatar_catalog'
);

set local role anon;
select set_config('request.jwt.claim.sub', '', true);
select set_config('request.jwt.claims', '{"role":"anon"}', true);

select lives_ok(
    $$select * from public.get_avatar_catalog()$$,
    'anonymous users can call get_avatar_catalog'
);

reset role;

select ok(
    not has_function_privilege('anon', 'public.sync_pull_profiles()', 'EXECUTE'),
    'anonymous role has no execute grant on authenticated RPCs'
);

set local role anon;

select throws_ok(
    $$select * from public.sync_pull_profiles()$$,
    '42501',
    'permission denied for function sync_pull_profiles',
    'anonymous users cannot call sync_pull_profiles'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000001', true);
select set_config(
    'request.jwt.claims',
    '{"sub":"10000000-0000-0000-0000-000000000001","role":"authenticated"}',
    true
);

select is(
    (select count(*) from public.sync_pull_profiles()),
    2::bigint,
    'User A pulls only their two profiles'
);

select is(
    (
        select count(*)
        from public.sync_pull_profiles()
        where user_id = '20000000-0000-0000-0000-000000000002'
    ),
    0::bigint,
    'User A cannot read User B profiles through the RPC'
);

select is(
    (
        select count(*)
        from public.addons
        where user_id = '20000000-0000-0000-0000-000000000002'
    ),
    0::bigint,
    'User A cannot read User B addons'
);

select is(
    (
        select count(*)
        from public.plugins
        where user_id = '20000000-0000-0000-0000-000000000002'
    ),
    0::bigint,
    'User A cannot read User B plugins'
);

select is(
    (select count(*) from public.addons),
    1::bigint,
    'User A can read their own addons'
);

select is(
    (select count(*) from public.plugins),
    1::bigint,
    'User A can read their own plugins'
);

select lives_ok(
    $$
        select public.sync_push_collections(
            1,
            '[{"id":"collection-a","title":"Action","folders":[]}]'::jsonb,
            'phase1-client-0001'
        )
    $$,
    'an owner can push a collection snapshot'
);

select is(
    (
        select collections_json
        from public.sync_pull_collections(1)
    ),
    '[{"id":"collection-a","title":"Action","folders":[]}]'::jsonb,
    'an owner pulls the same collection snapshot'
);

select lives_ok(
    $$
        select public.sync_push_home_catalog_settings(
            1,
            '{"hide_unreleased_content":false,"items":[{"addon_id":"","type":"special","catalog_id":"genres","enabled":true,"order":0}]}'::jsonb,
            'home_catalog_shared',
            'phase1-client-0001'
        )
    $$,
    'an owner can push home catalog settings'
);

select is(
    (
        select settings_json
        from public.sync_pull_home_catalog_settings(1, 'home_catalog_shared')
    ),
    '{"hide_unreleased_content":false,"items":[{"addon_id":"","type":"special","catalog_id":"genres","enabled":true,"order":0}]}'::jsonb,
    'an owner pulls the same home catalog settings'
);

select throws_ok(
    $$select public.sync_push_collections(1, '{}'::jsonb, 'phase1-client-0001')$$,
    '22023',
    'p_collections_json must be a JSON array',
    'collection pushes reject non-array payloads'
);

select throws_ok(
    $$
        select public.sync_push_home_catalog_settings(
            1,
            '[]'::jsonb,
            'home_catalog_shared',
            'phase1-client-0001'
        )
    $$,
    '22023',
    'p_settings_json must be a JSON object',
    'home catalog pushes reject non-object payloads'
);

select public.sync_push_addons(
    '[{"url":"https://spoof.example/manifest.json","name":"Spoof attempt","enabled":true,"sort_order":0,"user_id":"20000000-0000-0000-0000-000000000002"}]'::jsonb,
    1,
    'phase1-client-0001'
);

reset role;

select is(
    (
        select count(*)
        from public.addons
        where user_id = '20000000-0000-0000-0000-000000000002'
          and url = 'https://spoof.example/manifest.json'
    ),
    0::bigint,
    'Client-supplied ownership cannot modify User B data'
);

select is(
    (
        select count(*)
        from public.addons
        where user_id = '10000000-0000-0000-0000-000000000001'
          and url = 'https://spoof.example/manifest.json'
    ),
    1::bigint,
    'Ownership fields in addon JSON are ignored in favor of the caller owner'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '30000000-0000-0000-0000-000000000003', true);
select set_config(
    'request.jwt.claims',
    '{"sub":"30000000-0000-0000-0000-000000000003","role":"authenticated"}',
    true
);

select is(
    public.get_sync_owner(),
    '10000000-0000-0000-0000-000000000001'::uuid,
    'linked device resolves its effective owner'
);

select is(
    (
        select count(*)
        from public.addons
        where user_id = '10000000-0000-0000-0000-000000000001'
    ),
    1::bigint,
    'linked device can read owner addons'
);

select is(
    (
        select count(*)
        from public.plugins
        where user_id = '10000000-0000-0000-0000-000000000001'
    ),
    1::bigint,
    'linked device can read owner plugins'
);

select is(
    (
        select count(*)
        from public.addons
        where user_id = '20000000-0000-0000-0000-000000000002'
    ),
    0::bigint,
    'linked device cannot read unrelated owner addons'
);

select is(
    (
        select count(*)
        from public.plugins
        where user_id = '20000000-0000-0000-0000-000000000002'
    ),
    0::bigint,
    'linked device cannot read unrelated owner plugins'
);

select is(
    (
        select collections_json
        from public.sync_pull_collections(1)
    ),
    '[{"id":"collection-a","title":"Action","folders":[]}]'::jsonb,
    'linked device pulls its owner collection snapshot'
);

select is(
    (
        select settings_json
        from public.sync_pull_home_catalog_settings(1, 'home_catalog_shared')
    ),
    '{"hide_unreleased_content":false,"items":[{"addon_id":"","type":"special","catalog_id":"genres","enabled":true,"order":0}]}'::jsonb,
    'linked device pulls its owner home catalog settings'
);

select lives_ok(
    $$
        select public.sync_push_collections(
            1,
            '[{"id":"linked-update","title":"Linked update","folders":[]}]'::jsonb,
            'phase1-client-0002'
        )
    $$,
    'linked device can update its owner collection snapshot'
);

select is(
    (select count(*) from public.linked_devices),
    1::bigint,
    'device user can read only its own linking row'
);

reset role;

select is(
    (
        select collections_json
        from public.collection_blobs
        where user_id = '10000000-0000-0000-0000-000000000001'
          and profile_id = 1
    ),
    '[{"id":"linked-update","title":"Linked update","folders":[]}]'::jsonb,
    'linked device update is stored for the owner'
);

select is(
    (
        select count(*)
        from public.collection_blobs
        where user_id = '30000000-0000-0000-0000-000000000003'
    ),
    0::bigint,
    'linked device does not create a separate collection owner row'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '50000000-0000-0000-0000-000000000005', true);
select set_config(
    'request.jwt.claims',
    '{"sub":"50000000-0000-0000-0000-000000000005","role":"authenticated"}',
    true
);

select is(
    public.get_sync_owner(),
    '50000000-0000-0000-0000-000000000005'::uuid,
    'an unrelated device resolves only to itself'
);

select is(
    (
        select count(*)
        from public.addons
        where user_id = '10000000-0000-0000-0000-000000000001'
    ),
    0::bigint,
    'an unrelated device cannot read owner addons'
);

select is(
    (
        select count(*)
        from public.plugins
        where user_id = '10000000-0000-0000-0000-000000000001'
    ),
    0::bigint,
    'an unrelated device cannot read owner plugins'
);

select is(
    (select count(*) from public.sync_pull_collections(1)),
    0::bigint,
    'an unrelated device cannot pull owner collections'
);

select is(
    (select count(*) from public.sync_pull_home_catalog_settings(1, 'home_catalog_shared')),
    0::bigint,
    'an unrelated device cannot pull owner home catalog settings'
);

reset role;
set local role authenticated;
select set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000001', true);
select set_config(
    'request.jwt.claims',
    '{"sub":"10000000-0000-0000-0000-000000000001","role":"authenticated"}',
    true
);

select throws_ok(
    $$
        select public.sync_push_profiles(
            6,
            '[
                {"profile_index":1,"name":"Primary","avatar_color_hex":"#2563EB","uses_primary_addons":false,"uses_primary_plugins":false,"avatar_id":null,"avatar_url":null},
                {"profile_index":7,"name":"Invalid","avatar_color_hex":"#DC2626","uses_primary_addons":false,"uses_primary_plugins":false,"avatar_id":null,"avatar_url":null}
            ]'::jsonb,
            'phase1-client-0001'
        )
    $$,
    '22023',
    'profile_index 7 is outside the accepted range',
    'sync_push_profiles rejects profile indexes above six'
);

reset role;

insert into public.profiles (
    user_id,
    profile_index,
    name,
    avatar_color_hex
)
select
    '60000000-0000-0000-0000-000000000006',
    profile_index,
    'Compatibility ' || profile_index,
    '#0F766E'
from generate_series(1, 6) as series(profile_index);

insert into public.profile_locks (user_id, profile_index)
select profile.user_id, profile.profile_index
from public.profiles as profile
where profile.user_id = '60000000-0000-0000-0000-000000000006';

update public.profile_locks
set pin_enabled = true,
    pin_hash = 'compatibility-test-hash',
    failed_attempts = 2
where user_id = '60000000-0000-0000-0000-000000000006'
  and profile_index in (5, 6);

insert into public.addons (user_id, profile_id, url, name)
values
    ('60000000-0000-0000-0000-000000000006', 2, 'https://compat.example/addon-2.json', 'Compatibility addon 2'),
    ('60000000-0000-0000-0000-000000000006', 5, 'https://compat.example/addon-5.json', 'Compatibility addon 5'),
    ('60000000-0000-0000-0000-000000000006', 6, 'https://compat.example/addon-6.json', 'Compatibility addon 6');

insert into public.plugins (user_id, profile_id, url, name, repo_type)
values
    ('60000000-0000-0000-0000-000000000006', 2, 'https://compat.example/plugin-2.json', 'Compatibility plugin 2', 'OFFICIAL'),
    ('60000000-0000-0000-0000-000000000006', 5, 'https://compat.example/plugin-5.json', 'Compatibility plugin 5', 'OFFICIAL'),
    ('60000000-0000-0000-0000-000000000006', 6, 'https://compat.example/plugin-6.json', 'Compatibility plugin 6', 'OFFICIAL');

set local role authenticated;
select set_config('request.jwt.claim.sub', '60000000-0000-0000-0000-000000000006', true);
select set_config(
    'request.jwt.claims',
    '{"sub":"60000000-0000-0000-0000-000000000006","role":"authenticated"}',
    true
);

select throws_ok(
    $$
        select public.sync_push_profiles(
            4,
            '[
                {"profile_index":3,"name":"Compatibility 3","avatar_color_hex":"#0F766E","uses_primary_addons":false,"uses_primary_plugins":false,"avatar_id":null,"avatar_url":null}
            ]'::jsonb,
            'phase1-client-0001'
        )
    $$,
    '22023',
    'Primary profile 1 is required',
    'profile snapshots still require primary profile 1'
);

select public.sync_push_profiles(
    4,
    '[
        {"profile_index":1,"name":"Compatibility 1","avatar_color_hex":"#0F766E","uses_primary_addons":false,"uses_primary_plugins":false,"avatar_id":null,"avatar_url":null},
        {"profile_index":3,"name":"Compatibility 3","avatar_color_hex":"#0F766E","uses_primary_addons":false,"uses_primary_plugins":false,"avatar_id":null,"avatar_url":null},
        {"profile_index":4,"name":"Compatibility 4","avatar_color_hex":"#0F766E","uses_primary_addons":false,"uses_primary_plugins":false,"avatar_id":null,"avatar_url":null}
    ]'::jsonb,
    'phase1-client-0001'
);

reset role;

select is(
    array(
        select profile.name
        from public.profiles as profile
        where profile.user_id = '60000000-0000-0000-0000-000000000006'
          and profile.profile_index in (5, 6)
        order by profile.profile_index
    ),
    array['Compatibility 5', 'Compatibility 6']::text[],
    'a four-profile client preserves profiles five and six unchanged'
);

select is(
    (
        select count(*)
        from public.profiles
        where user_id = '60000000-0000-0000-0000-000000000006'
          and profile_index = 2
    ),
    0::bigint,
    'an omitted profile within the supported range is deleted'
);

select is(
    (
        select count(*)
        from public.profile_locks
        where user_id = '60000000-0000-0000-0000-000000000006'
          and profile_index = 2
    ),
    0::bigint,
    'the intentionally deleted supported profile cascades to its lock'
);

select is(
    (
        select count(*)
        from public.addons
        where user_id = '60000000-0000-0000-0000-000000000006'
          and profile_id = 2
    ),
    0::bigint,
    'the intentionally deleted supported profile cascades to its addon'
);

select is(
    (
        select count(*)
        from public.plugins
        where user_id = '60000000-0000-0000-0000-000000000006'
          and profile_id = 2
    ),
    0::bigint,
    'the intentionally deleted supported profile cascades to its plugin'
);

select is(
    (
        select count(*)
        from public.profile_locks
        where user_id = '60000000-0000-0000-0000-000000000006'
          and profile_index in (5, 6)
          and pin_enabled
          and pin_hash = 'compatibility-test-hash'
          and failed_attempts = 2
    ),
    2::bigint,
    'lock data for preserved higher profiles remains unchanged'
);

select is(
    array(
        select addon.url
        from public.addons as addon
        where addon.user_id = '60000000-0000-0000-0000-000000000006'
          and addon.profile_id in (5, 6)
        order by addon.profile_id
    ),
    array[
        'https://compat.example/addon-5.json',
        'https://compat.example/addon-6.json'
    ]::text[],
    'addon data for preserved higher profiles remains unchanged'
);

select is(
    array(
        select plugin.url
        from public.plugins as plugin
        where plugin.user_id = '60000000-0000-0000-0000-000000000006'
          and plugin.profile_id in (5, 6)
        order by plugin.profile_id
    ),
    array[
        'https://compat.example/plugin-5.json',
        'https://compat.example/plugin-6.json'
    ]::text[],
    'plugin data for preserved higher profiles remains unchanged'
);

select is(
    (
        select name
        from public.profiles
        where user_id = '20000000-0000-0000-0000-000000000002'
          and profile_index = 1
    ),
    'User B',
    'another user profile remains unchanged'
);

select is(
    (
        select count(*)
        from public.addons
        where user_id = '20000000-0000-0000-0000-000000000002'
          and profile_id = 1
          and url = 'https://b.example/original.json'
    ),
    1::bigint,
    'another user addon data remains unchanged'
);

select is(
    (
        select count(*)
        from public.plugins
        where user_id = '20000000-0000-0000-0000-000000000002'
          and profile_id = 1
          and url = 'https://b.example/repository.json'
          and repo_type = 'OFFICIAL'
    ),
    1::bigint,
    'another user plugin data remains unchanged'
);

insert into public.addons (user_id, profile_id, url, name)
values
    ('10000000-0000-0000-0000-000000000001', 1, 'https://stale.example/manifest.json', 'Stale A profile 1'),
    ('10000000-0000-0000-0000-000000000001', 2, 'https://guard.example/a-profile-2.json', 'A profile 2 guard'),
    ('20000000-0000-0000-0000-000000000002', 1, 'https://guard.example/b-profile-1.json', 'B profile 1 guard');

set local role authenticated;
select set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000001', true);

select public.sync_push_addons(
    '[{"url":"https://keep.example/manifest.json","name":"Keep","enabled":true,"sort_order":0}]'::jsonb,
    1,
    'phase1-client-0001'
);

reset role;

select is(
    (
        select count(*)
        from public.addons
        where user_id = '10000000-0000-0000-0000-000000000001'
          and profile_id = 1
    ),
    1::bigint,
    'addon snapshot leaves exactly the submitted owner/profile rows'
);

select is(
    (
        select count(*)
        from public.addons
        where user_id = '10000000-0000-0000-0000-000000000001'
          and profile_id = 1
          and url = 'https://keep.example/manifest.json'
    ),
    1::bigint,
    'addon snapshot keeps the submitted row'
);

select is(
    (
        select count(*)
        from public.addons
        where user_id = '10000000-0000-0000-0000-000000000001'
          and profile_id = 1
          and url = 'https://stale.example/manifest.json'
    ),
    0::bigint,
    'addon snapshot deletes stale rows in the requested owner/profile'
);

select is(
    (
        select count(*)
        from public.addons
        where user_id = '10000000-0000-0000-0000-000000000001'
          and profile_id = 2
          and url = 'https://guard.example/a-profile-2.json'
    ),
    1::bigint,
    'addon snapshot does not delete another profile rows'
);

select is(
    (
        select count(*)
        from public.addons
        where user_id = '20000000-0000-0000-0000-000000000002'
          and url = 'https://guard.example/b-profile-1.json'
    ),
    1::bigint,
    'addon snapshot does not delete another owner rows'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000001', true);

select public.sync_push_plugins(
    '[{"url":"https://shared.example/repository.json","name":"Shared one","enabled":true,"sort_order":0,"repo_type":"OFFICIAL"}]'::jsonb,
    1,
    'phase1-client-0001'
);

select public.sync_push_plugins(
    '[{"url":"https://shared.example/repository.json","name":"Shared two","enabled":true,"sort_order":0,"repo_type":null}]'::jsonb,
    2,
    'phase1-client-0001'
);

reset role;

select is(
    (
        select count(*)
        from public.plugins
        where user_id = '10000000-0000-0000-0000-000000000001'
          and url = 'https://shared.example/repository.json'
    ),
    2::bigint,
    'identical plugin URLs remain separate across profiles'
);

select is(
    (
        select count(distinct profile_id)
        from public.plugins
        where user_id = '10000000-0000-0000-0000-000000000001'
          and url = 'https://shared.example/repository.json'
    ),
    2::bigint,
    'shared plugin URL rows retain both profile identities'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000001', true);

select public.register_current_device(
    'phase1-installation',
    'Nuvio TV',
    '1.0.0',
    'Android TV 15',
    repeat('x', 200)
);

reset role;

select is(
    (
        select length(device_name)
        from public.registered_devices
        where installation_id = 'phase1-installation'
    ),
    160,
    'register_current_device truncates device names to 160 characters'
);

select is(
    (
        select user_id
        from public.registered_devices
        where installation_id = 'phase1-installation'
    ),
    '10000000-0000-0000-0000-000000000001'::uuid,
    'register_current_device stores the actual caller user ID'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000001', true);

select is(
    array(
        select key
        from jsonb_object_keys(public.get_sync_overview()) as keys(key)
        order by key
    ),
    array['addons', 'library_items', 'plugins', 'profiles', 'watch_progress', 'watched_items']::text[],
    'get_sync_overview returns the exact expected top-level keys'
);

select is(
    public.get_sync_overview() -> 'library_items',
    '{}'::jsonb,
    'get_sync_overview returns an empty library_items object in Phase 1'
);

select is(
    public.get_sync_overview() -> 'watch_progress',
    '{}'::jsonb,
    'get_sync_overview returns an empty watch_progress object in Phase 1'
);

select is(
    public.get_sync_overview() -> 'watched_items',
    '{}'::jsonb,
    'get_sync_overview returns an empty watched_items object in Phase 1'
);

select ok(
    public.get_sync_overview() -> 'profiles' ? '1',
    'get_sync_overview includes the primary profile'
);

reset role;

select ok(
    has_function_privilege('authenticated', 'public.get_avatar_catalog()', 'EXECUTE'),
    'authenticated role can execute get_avatar_catalog'
);

select ok(
    has_function_privilege(
        'authenticated',
        'public.sync_push_profiles(integer,jsonb,text)',
        'EXECUTE'
    ),
    'authenticated role can execute sync_push_profiles'
);

select ok(
    has_function_privilege(
        'authenticated',
        'public.sync_push_collections(integer,jsonb,text)',
        'EXECUTE'
    ),
    'authenticated role can execute sync_push_collections'
);

select ok(
    has_function_privilege(
        'authenticated',
        'public.sync_pull_collections(integer)',
        'EXECUTE'
    ),
    'authenticated role can execute sync_pull_collections'
);

select ok(
    has_function_privilege(
        'authenticated',
        'public.sync_push_home_catalog_settings(integer,jsonb,text,text)',
        'EXECUTE'
    ),
    'authenticated role can execute sync_push_home_catalog_settings'
);

select ok(
    has_function_privilege(
        'authenticated',
        'public.sync_pull_home_catalog_settings(integer,text)',
        'EXECUTE'
    ),
    'authenticated role can execute sync_pull_home_catalog_settings'
);

select ok(
    not exists (
        select 1
        from (
            values
                ('public.get_sync_owner()'),
                ('public.can_access_user_data(uuid)'),
                ('public.sync_push_profiles(integer,jsonb,text)'),
                ('public.sync_pull_profiles()'),
                ('public.sync_pull_profile_locks()'),
                ('public.sync_push_addons(jsonb,integer,text)'),
                ('public.sync_push_plugins(jsonb,integer,text)'),
                ('public.sync_push_collections(integer,jsonb,text)'),
                ('public.sync_pull_collections(integer)'),
                ('public.sync_push_home_catalog_settings(integer,jsonb,text,text)'),
                ('public.sync_pull_home_catalog_settings(integer,text)'),
                ('public.register_current_device(text,text,text,text,text)'),
                ('public.get_sync_overview()')
        ) as authenticated_function(signature)
        where has_function_privilege(
            'anon',
            authenticated_function.signature,
            'EXECUTE'
        )
    ),
    'anonymous role cannot execute any authenticated Phase 1 function'
);

select ok(
    not exists (
        select 1
        from pg_proc as proc
        join pg_namespace as namespace on namespace.oid = proc.pronamespace
        cross join lateral aclexplode(
            coalesce(proc.proacl, acldefault('f', proc.proowner))
        ) as privilege
        where namespace.nspname = 'public'
          and proc.proname in (
              'set_updated_at',
              'get_sync_owner',
              'can_access_user_data',
              'get_avatar_catalog',
              'sync_push_profiles',
              'sync_pull_profiles',
              'sync_pull_profile_locks',
              'sync_push_addons',
              'sync_push_plugins',
              'sync_push_collections',
              'sync_pull_collections',
              'sync_push_home_catalog_settings',
              'sync_pull_home_catalog_settings',
              'register_current_device',
              'get_sync_overview'
          )
          and privilege.grantee = 0
          and privilege.privilege_type = 'EXECUTE'
    ),
    'Phase 1 functions are not executable by PUBLIC'
);

select ok(
    not exists (
        select 1
        from (
            values
                ('public.profiles'),
                ('public.profile_locks'),
                ('public.addons'),
                ('public.plugins'),
                ('public.linked_devices'),
                ('public.avatar_catalog'),
                ('public.registered_devices'),
                ('public.collection_blobs'),
                ('public.home_catalog_settings'),
                ('public.watch_progress'),
                ('public.watch_progress_events'),
                ('public.watched_items'),
                ('public.watched_items_events'),
                ('public.library_items'),
                ('public.library_items_events'),
                ('public.profile_settings_blobs'),
                ('public.sync_codes'),
                ('public.tv_login_sessions')
        ) as phase1_table(relation_name)
        where has_table_privilege(
            'authenticated',
            phase1_table.relation_name,
            'INSERT'
        )
    ),
    'authenticated role has no direct Phase 1 table insert grants'
);

select ok(
    not exists (
        select 1
        from (
            values
                ('public.profiles'),
                ('public.profile_locks'),
                ('public.addons'),
                ('public.plugins'),
                ('public.linked_devices'),
                ('public.avatar_catalog'),
                ('public.registered_devices'),
                ('public.collection_blobs'),
                ('public.home_catalog_settings'),
                ('public.watch_progress'),
                ('public.watch_progress_events'),
                ('public.watched_items'),
                ('public.watched_items_events'),
                ('public.library_items'),
                ('public.library_items_events'),
                ('public.profile_settings_blobs'),
                ('public.sync_codes'),
                ('public.tv_login_sessions')
        ) as phase1_table(relation_name)
        where has_table_privilege(
            'authenticated',
            phase1_table.relation_name,
            'UPDATE'
        )
    ),
    'authenticated role has no direct Phase 1 table update grants'
);

select ok(
    not exists (
        select 1
        from (
            values
                ('public.profiles'),
                ('public.profile_locks'),
                ('public.addons'),
                ('public.plugins'),
                ('public.linked_devices'),
                ('public.avatar_catalog'),
                ('public.registered_devices'),
                ('public.collection_blobs'),
                ('public.home_catalog_settings'),
                ('public.watch_progress'),
                ('public.watch_progress_events'),
                ('public.watched_items'),
                ('public.watched_items_events'),
                ('public.library_items'),
                ('public.library_items_events'),
                ('public.profile_settings_blobs'),
                ('public.sync_codes'),
                ('public.tv_login_sessions')
        ) as phase1_table(relation_name)
        where has_table_privilege(
            'authenticated',
            phase1_table.relation_name,
            'DELETE'
        )
    ),
    'authenticated role has no direct Phase 1 table delete grants'
);

select ok(
    not has_table_privilege('anon', 'public.linked_devices', 'SELECT'),
    'anonymous role cannot select linked devices'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '10000000-0000-0000-0000-000000000001', true);

select throws_ok(
    $$
        insert into public.addons (user_id, profile_id, url)
        values (
            '10000000-0000-0000-0000-000000000001',
            1,
            'https://direct-write.example/manifest.json'
        )
    $$,
    '42501',
    'permission denied for table addons',
    'direct authenticated table writes are denied for RPC-only mutations'
);

reset role;

select * from finish();
rollback;
