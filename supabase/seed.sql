-- Official Nuvio profile avatars (hotlinked from api.nuvio.tv public storage).
-- storage_path is a full URL, so AVATAR_PUBLIC_BASE_URL is not required for these rows.
--
-- Apply in the Supabase SQL editor for your project, then reopen profile create/edit
-- in the app so get_avatar_catalog refreshes.

delete from public.avatar_catalog
where id like 'ultramax-%'
   or id like 'avatar_%'
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
    ('avatar_aang', 'Aang', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_aang_1772809370453.png', 'animation', 1, '#0060F8', true),
    ('avatar_arthur_morgan', 'Arthur Morgan', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_arthur_morgan_1772786328141.png', 'gaming', 2, '#B01028', true),
    ('avatar_ash', 'Ash', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_ash_1772809405294.png', 'anime', 3, '#E00808', true),
    ('avatar_chihiro', 'Chihiro', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_chihiro_1772809422792.png', 'anime', 4, '#88E070', true),
    ('avatar_daenerys', 'Daenerys', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_daenerys_1772786201651.png', 'tv', 5, '#00B8D8', true),
    ('avatar_dexter', 'Dexter', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_dexter_1772808898372.png', 'tv', 6, '#A80808', true),
    ('avatar_eleven', 'Eleven', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_eleven_1772785893766.png', 'tv', 7, '#8800F8', true),
    ('avatar_eren', 'Eren', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_eren_1772808836514.png', 'anime', 8, '#900018', true),
    ('avatar_furiosa', 'Furiosa', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_furiosa_1772827439561.png', 'movie', 9, '#D08848', true),
    ('avatar_geralt', 'Geralt', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_geralt_1772826884310.png', 'gaming', 10, '#380070', true),
    ('avatar_gojo', 'Gojo', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_gojo_1772826847969.png', 'anime', 11, '#00F8F8', true),
    ('avatar_goku', 'Goku', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_goku_1772786622108.png', 'anime', 12, '#F84000', true),
    ('avatar_harry_potter', 'Harry Potter', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_harry_potter_1772786358133.png', 'movie', 13, '#F8B000', true),
    ('avatar_jack_sparrow', 'Jack Sparrow', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_jack_sparrow_1772786396797.png', 'movie', 14, '#F8F8F8', true),
    ('avatar_jinwoo', 'Jinwoo', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_jinwoo_1772808878532.png', 'anime', 15, '#0058F8', true),
    ('avatar_joel', 'Joel', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_joel_1772827212455.png', 'tv', 16, '#102010', true),
    ('avatar_jon_snow', 'Jon Snow', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_jon_snow_1772786050374.png', 'tv', 17, '#004090', true),
    ('avatar_katara', 'Katara', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_katara_1772809386366.png', 'animation', 18, '#00A0A8', true),
    ('avatar_killua', 'Killua', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_killua_1772826924033.png', 'anime', 19, '#0030F8', true),
    ('avatar_kratos', 'Kratos', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_kratos_1772826869090.png', 'gaming', 20, '#880000', true),
    ('avatar_lalo', 'Lalo', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_lalo_1772808914536.png', 'tv', 21, '#E09018', true),
    ('avatar_lara', 'Lara', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_lara_1772826963671.png', 'gaming', 22, '#008878', true),
    ('avatar_levi', 'Levi', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_levi_1772826833149.png', 'anime', 23, '#484848', true),
    ('avatar_mikasa', 'Mikasa', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_mikasa_1772808997012.png', 'anime', 24, '#007870', true),
    ('avatar_naruto', 'Naruto', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_naruto_1772786640402.png', 'anime', 25, '#D8F800', true),
    ('avatar_negan', 'Negan', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_negan_1772808934794.png', 'tv', 26, '#780078', true),
    ('avatar_neo', 'Neo', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_neo_1772786377143.png', 'movie', 27, '#88F800', true),
    ('avatar_rick_grimes', 'Rick Grimes', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_rick_grimes_1772786275264.png', 'tv', 28, '#C85018', true),
    ('avatar_saitama', 'Saitama', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_saitama_1772826938248.png', 'anime', 29, '#F8D000', true),
    ('avatar_saul_goodman', 'Saul Goodman', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_saul_goodman_1772786019049.png', 'tv', 30, '#F84000', true),
    ('avatar_tommy_shelby', 'Tommy Shelby', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_tommy_shelby_1772786000275.png', 'tv', 31, '#F83040', true),
    ('avatar_v', 'V', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_v_1772827227584.png', 'gaming', 32, '#000830', true),
    ('avatar_walter_white', 'Walter White', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_walter_white_1772785927308.png', 'tv', 33, '#F8C000', true),
    ('avatar_wednesday', 'Wednesday', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_wednesday_1772786225606.png', 'tv', 34, '#500840', true),
    ('avatar_linear_woman_teal', 'Lin', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_linear_teal_v3.png', 'linear', 35, '#008080', true),
    ('avatar_linear_man_purple', 'Max', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_linear_purple_v3.png', 'linear', 36, '#6B21A8', true),
    ('avatar_linear_woman_red', 'Ava', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_linear_red_v3.png', 'linear', 37, '#E11D48', true),
    ('avatar_linear_man_navy', 'Theo', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_linear_navy_v3.png', 'linear', 38, '#1E3A5F', true),
    ('avatar_linear_woman_yellow', 'Zara', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_linear_yellow_v3.png', 'linear', 39, '#D97706', true),
    ('avatar_linear_man_green', 'Kai', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_linear_green_v3.png', 'linear', 40, '#065F46', true),
    ('avatar_linear_woman_pink', 'Nova', 'https://api.nuvio.tv/storage/v1/object/public/avatars/avatar_linear_pink_v3.png', 'linear', 41, '#BE185D', true)
on conflict (id) do update
set display_name = excluded.display_name,
    storage_path = excluded.storage_path,
    category = excluded.category,
    sort_order = excluded.sort_order,
    bg_color = excluded.bg_color,
    is_active = excluded.is_active;
