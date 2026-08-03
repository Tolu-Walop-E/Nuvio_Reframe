insert into public.avatar_catalog (
    id,
    display_name,
    storage_path,
    category,
    sort_order,
    bg_color,
    is_active
)
values (
    'nuvio-placeholder',
    'Nuvio',
    'https://placehold.co/512x512/2563EB/FFFFFF.png?text=N',
    'default',
    0,
    '#2563EB',
    true
)
on conflict (id) do update
set display_name = excluded.display_name,
    storage_path = excluded.storage_path,
    category = excluded.category,
    sort_order = excluded.sort_order,
    bg_color = excluded.bg_color,
    is_active = excluded.is_active;
