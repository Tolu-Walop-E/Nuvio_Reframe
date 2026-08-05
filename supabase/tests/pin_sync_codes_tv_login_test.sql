begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(22);

delete from auth.users
where id in (
    '13130000-0000-0000-0000-000000000001'::uuid,
    '24240000-0000-0000-0000-000000000002'::uuid,
    '35350000-0000-0000-0000-000000000003'::uuid
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
        '13130000-0000-0000-0000-000000000001'::uuid,
        'authenticated',
        'authenticated',
        'pin-link-user-a@example.test',
        crypt('pin-password-a', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    ),
    (
        '00000000-0000-0000-0000-000000000000'::uuid,
        '24240000-0000-0000-0000-000000000002'::uuid,
        'authenticated',
        'authenticated',
        'pin-link-user-b@example.test',
        crypt('pin-password-b', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    ),
    (
        '00000000-0000-0000-0000-000000000000'::uuid,
        '35350000-0000-0000-0000-000000000003'::uuid,
        'authenticated',
        'authenticated',
        'pin-link-device@example.test',
        crypt('pin-password-device', gen_salt('bf')),
        now(),
        '{"provider":"email","providers":["email"]}'::jsonb,
        '{}'::jsonb,
        now(),
        now()
    );

insert into public.profiles (user_id, profile_index, name, avatar_color_hex)
values
    ('13130000-0000-0000-0000-000000000001', 1, 'Owner', '#777777'),
    ('24240000-0000-0000-0000-000000000002', 1, 'Claimer', '#888888');

insert into public.profile_locks (user_id, profile_index)
values
    ('13130000-0000-0000-0000-000000000001', 1),
    ('24240000-0000-0000-0000-000000000002', 1);

select set_config('request.jwt.claim.sub', '13130000-0000-0000-0000-000000000001', true);
select set_config('request.jwt.claim.role', 'authenticated', true);

select lives_ok(
    $$select public.set_profile_pin(1, '1234')$$,
    'owner can set an initial profile PIN'
);

select throws_ok(
    $$select public.set_profile_pin(1, '9999')$$,
    'P0001',
    'Current PIN is required',
    'replacing a PIN requires the current PIN'
);

select lives_ok(
    $$select public.set_profile_pin(1, '5678', '1234')$$,
    'owner can replace PIN with current PIN'
);

select is(
    (
        select unlocked
        from public.verify_profile_pin(1, '5678')
    ),
    true,
    'correct PIN unlocks'
);

select is(
    (
        select unlocked
        from public.verify_profile_pin(1, '0000')
    ),
    false,
    'incorrect PIN does not unlock'
);

select lives_ok(
    $$
        select set_config(
            'test.sync_code',
            (select code from public.generate_sync_code('4321')),
            true
        )
    $$,
    'owner can generate a sync code'
);

select is(
    (
        select code
        from public.get_sync_code('4321')
    ),
    current_setting('test.sync_code', true),
    'get_sync_code returns the active code after PIN check'
);

select set_config('request.jwt.claim.sub', '35350000-0000-0000-0000-000000000003', true);

select is(
    (
        select success
        from public.claim_sync_code(
            current_setting('test.sync_code', true),
            '4321',
            'Living Room'
        )
    ),
    true,
    'device can claim sync code with correct PIN'
);

select is(
    (
        select count(*)::integer
        from public.linked_devices
        where owner_id = '13130000-0000-0000-0000-000000000001'
          and device_user_id = '35350000-0000-0000-0000-000000000003'
    ),
    1,
    'claim inserts linked_devices row'
);

select set_config('request.jwt.claim.sub', '13130000-0000-0000-0000-000000000001', true);

select lives_ok(
    $$
        select public.unlink_device('35350000-0000-0000-0000-000000000003'::uuid)
    $$,
    'owner can unlink a device'
);

select is(
    (
        select count(*)::integer
        from public.linked_devices
        where device_user_id = '35350000-0000-0000-0000-000000000003'
    ),
    0,
    'unlink removes the linked device'
);

select set_config('request.jwt.claim.sub', '', true);
select set_config('request.jwt.claim.role', 'anon', true);

select lives_ok(
    $$
        select set_config(
            'test.tv_code',
            (
                select code
                from public.start_tv_login_session(
                    'device-nonce-abcdefgh',
                    'https://example.test/tv-login',
                    'Shield'
                )
            ),
            true
        )
    $$,
    'anon can start a TV login session'
);

select is(
    (
        select status
        from public.poll_tv_login_session(
            current_setting('test.tv_code', true),
            'device-nonce-abcdefgh'
        )
    ),
    'pending',
    'poll reports pending before approval'
);

select set_config('request.jwt.claim.sub', '13130000-0000-0000-0000-000000000001', true);
select set_config('request.jwt.claim.role', 'authenticated', true);

select lives_ok(
    $$
        select public.approve_tv_login_session(
            current_setting('test.tv_code', true),
            'device-nonce-abcdefgh'
        )
    $$,
    'authenticated user can approve a TV login session'
);

select is(
    (
        select status
        from public.poll_tv_login_session(
            current_setting('test.tv_code', true),
            'device-nonce-abcdefgh'
        )
    ),
    'approved',
    'poll reports approved after approval'
);

select set_config('request.jwt.claim.role', 'service_role', true);

select is(
    (
        select approved_user_id
        from public.consume_tv_login_session(
            current_setting('test.tv_code', true),
            'device-nonce-abcdefgh'
        )
    ),
    '13130000-0000-0000-0000-000000000001'::uuid,
    'service_role can consume an approved TV login session'
);

select is(
    (
        select status
        from public.poll_tv_login_session(
            current_setting('test.tv_code', true),
            'device-nonce-abcdefgh'
        )
    ),
    'used',
    'consume marks the TV login session used'
);

select set_config('request.jwt.claim.sub', '24240000-0000-0000-0000-000000000002', true);
select set_config('request.jwt.claim.role', 'authenticated', true);

select throws_ok(
    $$
        select public.consume_tv_login_session('NOPE', 'device-nonce-abcdefgh')
    $$,
    '42501',
    'service_role required',
    'authenticated callers cannot consume TV login sessions'
);

select ok(
    has_function_privilege('anon', 'public.start_tv_login_session(text,text,text)', 'EXECUTE')
    and has_function_privilege('anon', 'public.poll_tv_login_session(text,text)', 'EXECUTE')
    and not has_function_privilege('anon', 'public.approve_tv_login_session(text,text)', 'EXECUTE'),
    'anon can start/poll TV login but cannot approve'
);

select ok(
    not has_table_privilege('authenticated', 'public.sync_codes', 'SELECT')
    and not has_table_privilege('authenticated', 'public.tv_login_sessions', 'SELECT'),
    'authenticated has no direct sync_codes/tv_login table grants'
);

select set_config('request.jwt.claim.sub', '13130000-0000-0000-0000-000000000001', true);

select lives_ok(
    $$select public.clear_profile_pin(1, '5678')$$,
    'owner can clear profile PIN with current PIN'
);

select is(
    (
        select pin_enabled
        from public.sync_pull_profile_locks()
        where profile_index = 1
    ),
    false,
    'clear_profile_pin disables the lock'
);

select * from finish();
rollback;
