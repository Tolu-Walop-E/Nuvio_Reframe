# Nuvio TV Supabase Backend

This directory contains Phase 1 of the Nuvio TV Supabase backend plus the collection and home-catalog settings extension. It covers profiles, profile lock state reads, addons, plugins, collections, home-row order/settings, linked-device ownership reads, avatar catalog reads, device registration, and the sync overview. Media syncing, linking workflows, TV login, Storage, Realtime, and Edge Functions are intentionally outside this phase.

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

The suite covers anonymous/authenticated grants, cross-user isolation, linked-device owner reads, profile bounds, snapshot deletion scope, collection and home-catalog settings sync, profile-specific plugin identity, device-name truncation, overview shape, and denial of direct writes.

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
AVATAR_PUBLIC_BASE_URL=
```

The Phase 1 seed stores a complete HTTPS placeholder URL in `avatar_catalog.storage_path`, so `AVATAR_PUBLIC_BASE_URL` should remain empty for local development.

An Android TV device or emulator cannot usually reach the host through `127.0.0.1`. Use the host address appropriate for that device while keeping the same local Supabase API port.

## Phase 1 Ownership Rules

- RPCs derive ownership from `auth.uid()` and `get_sync_owner()`; client JSON cannot select a user ID.
- A linked-device row is considered active while it exists because Phase 1 defines no separate active flag.
- An owner may link multiple devices, but each device user can belong to only one owner and a user cannot be linked to itself. New linking RPCs remain a later-phase concern.
- Addon and plugin payloads are full snapshots for one owner/profile. Their URLs must be nonblank HTTP or HTTPS URLs.
- Profile payloads are full snapshots for the profile indexes supported by the calling client and must include profile `1`. Omitted supported profiles are removed, while profiles above the client's maximum are preserved. Cascading FKs remove Phase 1 profile-scoped rows only for an intentionally removed profile.
- Direct authenticated reads are granted only for `addons`, `plugins`, and `linked_devices`. User-data writes go through SECURITY DEFINER RPCs.

## Not Implemented

- Library snapshot or delta syncing.
- Watch-progress snapshot or delta syncing.
- Watched-items snapshot or delta syncing.
- Profile settings blobs.
- Provider credential persistence.
- Sync/device linking code creation, claiming, or unlinking RPCs.
- Profile PIN setting, clearing, or verification.
- TV login and its exchange Edge Function.
- Realtime subscriptions/publications.
- Supabase Storage buckets or uploads.
