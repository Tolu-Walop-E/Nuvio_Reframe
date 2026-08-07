# Nuvio TV Supabase Backend

This directory contains the Nuvio TV Supabase backend used by the Android cloud fork: profiles/addons/plugins, collections and home-catalog settings, profile settings blobs, library / watch-progress / watched-items sync, profile PINs, sync-code device linking, and TV QR login (SQL + `tv-logins-exchange` Edge Function). Provider credential persistence and branded avatar asset packs remain optional follow-ups.

## Prerequisites

- Docker Desktop with the Linux container engine running.
- Supabase CLI available as `supabase`.
- PostgreSQL `psql` is optional for running the seed manually.

From the repository root, confirm the tools:

```powershell
supabase --version
docker info
```

## Start Locally

Start the local Supabase stack:

```powershell
supabase start
```

The local API URL and anon key printed by this command can be placed in the Android local properties described below.

## Apply Migrations and Seed

Resetting the local database applies every file under `supabase/migrations/` and then runs `supabase/seed.sql` because seeding is enabled in `config.toml`:

```powershell
supabase db reset
```

The seed is idempotent. To run it again without a reset, use `psql` against the local database:

```powershell
psql "postgresql://postgres:postgres@127.0.0.1:55322/postgres" -f supabase/seed.sql
```

## Run SQL Tests

The tests use pgTAP and run in a transaction that is rolled back:

```powershell
supabase test db
```

The suite covers anonymous/authenticated grants, cross-user isolation, linked-device owner reads, profile bounds, snapshot deletion scope, collection / home-catalog / profile-settings blob sync, library / watch-progress / watched-items push/pull/delta/delete, profile-specific plugin identity, device-name truncation, overview shape, and denial of direct writes.

## Inspect the Schema

Generate a local schema-only dump for review:

```powershell
supabase db dump --local --schema public --file supabase/.temp/phase1-schema.sql
```

Inspect migration drift without changing the database:

```powershell
supabase db diff --local --schema public
```

## Authentication

Phase 1 requires Supabase email/password authentication. Anonymous Auth sign-in is disabled. The `anon` database role can execute only the public avatar catalog RPC; authenticated user-data RPCs require a valid user JWT.

The custom TV-login flow is not implemented in Phase 1.

## Android Environment

Configure these keys in `local.dev.properties` for debug builds or `local.properties` for release builds:

```properties
NUVIO_SUPABASE_URL=http://127.0.0.1:55321
NUVIO_SUPABASE_ANON_KEY=<anon-key-from-supabase-start>
NUVIO_SUPABASE_FALLBACK_URL=
AVATAR_PUBLIC_BASE_URL=https://<PROJECT>.supabase.co/storage/v1/object/public/avatars
```

## Avatars (not shipped in the Android APK)

Upstream Nuvio stores avatar art in Supabase Storage, not in `app/src/main/res`. The Android client:

1. Calls `get_avatar_catalog`
2. Builds `imageUrl` as `AVATAR_PUBLIC_BASE_URL + "/" + storage_path` (or uses `storage_path` as-is when it is already an `http(s)` URL)

`AvatarPickerGrid` groups by category: `anime`, `animation`, `tv`, `movie`, `gaming`.

### Seed your fork

1. Create a **public** Storage bucket named `avatars`.
2. Upload images under paths that match `avatar_catalog.storage_path` (see `supabase/seed.sql`), e.g. `anime/sample-01.png`.
3. Set `AVATAR_PUBLIC_BASE_URL` to:
   `https://<PROJECT>.supabase.co/storage/v1/object/public/avatars`
4. Run / apply `supabase/seed.sql` (replace sample rows with your real pack ids/names/paths).
5. Rebuild the Android app so BuildConfig picks up the base URL.

An Android TV device or emulator cannot usually reach the host through `127.0.0.1`. Use the host address appropriate for that device while keeping the same local Supabase API port.

## Phase 1 Ownership Rules

- RPCs derive ownership from `auth.uid()` and `get_sync_owner()`; client JSON cannot select a user ID.
- A linked-device row is considered active while it exists because Phase 1 defines no separate active flag.
- An owner may link multiple devices, but each device user can belong to only one owner and a user cannot be linked to itself. New linking RPCs remain a later-phase concern.
- Addon and plugin payloads are full snapshots for one owner/profile. Their URLs must be nonblank HTTP or HTTPS URLs.
- Profile payloads are full snapshots for the profile indexes supported by the calling client and must include profile `1`. Omitted supported profiles are removed, while profiles above the client's maximum are preserved. Cascading FKs remove Phase 1 profile-scoped rows only for an intentionally removed profile.
- Direct authenticated reads are granted only for `addons`, `plugins`, and `linked_devices`. User-data writes go through SECURITY DEFINER RPCs.

## Not Implemented

- Provider credential persistence.
- Realtime subscriptions/publications.
- Supabase Storage for non-avatar media (avatar bucket is documented under Avatars above).
- Hosted approve-web UI for TV login (`supabase/web/tv-login.html`). Do not host this on Supabase Storage or Edge Functions — both rewrite `text/html` to `text/plain` with `nosniff`, so phones show raw HTML source. Bake URL+anon into the page, serve it from any normal HTTPS static host, and point `TV_LOGIN_WEB_BASE_URL` at it. Quick local test: `.\supabase\scripts\serve-tv-login.ps1`.

## Watch Progress + Watched Items

Migration `20260803140000_watch_progress_watched_items.sql` adds:

- Tables `watch_progress`, `watch_progress_events`, `watched_items`, `watched_items_events`
- RPCs used by `WatchProgressSyncService` / `WatchedItemsSyncService`
- Real counts in `get_sync_overview` for `watch_progress` and `watched_items`

All writes go through SECURITY DEFINER RPCs with `get_sync_owner()` so linked devices share the owner's rowset.

## Library Items

Migration `20260803150000_library_items.sql` adds:

- Tables `library_items`, `library_items_events`
- RPCs used by `SupabaseLibrarySyncRemoteDataSource`
- Real counts in `get_sync_overview` for `library_items`

## Profile Settings Blob

Migration `20260803160000_profile_settings_blob.sql` adds:

- Table `profile_settings_blobs` (per user/profile/platform)
- `sync_push_profile_settings_blob` / `sync_pull_profile_settings_blob` for `ProfileSettingsSyncService`

## PIN, Sync Codes, TV Login

Migration `20260803170000_pin_sync_codes_tv_login.sql` adds:

- `set_profile_pin` / `clear_profile_pin` / `verify_profile_pin`
- `generate_sync_code` / `get_sync_code` / `claim_sync_code` / `unlink_device`
- `start_tv_login_session` / `poll_tv_login_session` / `approve_tv_login_session` / `consume_tv_login_session`

Edge Function `supabase/functions/tv-logins-exchange` (JWT verification disabled so the TV can call with the anon key) consumes an approved session and returns Auth tokens via Admin `createSession`.

Local: set `[edge_runtime] enabled = true` (already in `config.toml`), then `supabase functions serve tv-logins-exchange` or restart the local stack.
