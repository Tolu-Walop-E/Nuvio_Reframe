-- Ultramax character avatars (hotlinked HTTPS URLs).
-- Source: https://ultramax.vip/avatars.html
-- storage_path is a full URL, so AVATAR_PUBLIC_BASE_URL is not required for these rows.
--
-- Apply in the Supabase SQL editor for your project, then reopen profile create/edit
-- in the app so get_avatar_catalog refreshes.

delete from public.avatar_catalog
where id like 'ultramax-%'
   or id = 'nuvio-placeholder';

insert into public.avatar_catalog (
    id,
    display_name,
    storage_path,
    category,
    sort_order,
    bg_color,
    is_active
)
values
    ('ultramax-baymax', 'Baymax', 'https://ultramax.vip/images/avatars/baymax.webp', 'animation', 10, null, true),
    ('ultramax-belle', 'Belle', 'https://ultramax.vip/images/avatars/belle.webp', 'movie', 20, null, true),
    ('ultramax-aladdin', 'Aladdin', 'https://ultramax.vip/images/avatars/aladdin.webp', 'movie', 30, null, true),
    ('ultramax-ariel', 'Ariel', 'https://ultramax.vip/images/avatars/ariel.webp', 'movie', 40, null, true),
    ('ultramax-anna', 'Anna', 'https://ultramax.vip/images/avatars/anna.webp', 'animation', 50, null, true),
    ('ultramax-baloo', 'Baloo', 'https://ultramax.vip/images/avatars/baloo.webp', 'animation', 60, null, true),
    ('ultramax-bambi', 'Bambi', 'https://ultramax.vip/images/avatars/bambi.webp', 'animation', 70, null, true),
    ('ultramax-alice', 'Alice', 'https://ultramax.vip/images/avatars/alice.webp', 'movie', 80, null, true)
on conflict (id) do update
set display_name = excluded.display_name,
    storage_path = excluded.storage_path,
    category = excluded.category,
    sort_order = excluded.sort_order,
    bg_color = excluded.bg_color,
    is_active = excluded.is_active;
