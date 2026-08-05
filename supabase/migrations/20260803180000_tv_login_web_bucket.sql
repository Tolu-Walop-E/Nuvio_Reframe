-- Public static hosting for the TV QR approve page.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values (
    'web',
    'web',
    true,
    1048576,
    array['text/html', 'text/css', 'application/javascript', 'image/png', 'image/jpeg', 'image/webp']
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;

drop policy if exists web_public_read on storage.objects;
create policy web_public_read
on storage.objects
for select
to anon, authenticated
using (bucket_id = 'web');

drop policy if exists web_service_write on storage.objects;
create policy web_service_write
on storage.objects
for all
to service_role
using (bucket_id = 'web')
with check (bucket_id = 'web');
