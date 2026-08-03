# Supabase Requirements

This document is an evidence-only contract audit of the current repository. It distinguishes the live Kotlin/PostgREST wire contract from SQL found only in Git history. PostgreSQL types, keys, relation names, policies, and grants are stated as exact only where SQL defines them. Where the app exposes only JSON, the type is labeled as a wire type and the physical database implementation remains unknown.

Repository-relative references use `path:line`. Deleted SQL is referenced as `path@<deleting-commit>^:line`, meaning the version immediately before the deletion. Negative findings were checked with repository-wide `rg`/`rg --files` searches because an absent call has no source line.

## Configuration Contract

- Debug builds read `NUVIO_SUPABASE_URL`, `NUVIO_SUPABASE_ANON_KEY`, and `NUVIO_SUPABASE_FALLBACK_URL` from `local.dev.properties` with `local.properties` fallback. Release builds read the same keys from `local.properties`. They become `BuildConfig.SUPABASE_URL`, `BuildConfig.SUPABASE_ANON_KEY`, and `BuildConfig.SUPABASE_FALLBACK_URL`. Sources: `app/build.gradle.kts:213-216`, `app/build.gradle.kts:247-250`.
- The ignored local configuration currently defines the names `NUVIO_SUPABASE_URL` and `NUVIO_SUPABASE_ANON_KEY`; their secret values are intentionally not reproduced here. `local.properties`, `local.dev.properties`, `.supabase_db.env`, and `/supabase` are ignored. Sources: `local.properties:9-10`, `.gitignore:3-4`, `.gitignore:43`, `.gitignore:53`.
- The Supabase client is created with the configured URL and anon key and installs only Auth and PostgREST. Sources: `app/src/main/java/com/nuvio/tv/core/di/SupabaseModule.kt:30-34`, `app/src/main/java/com/nuvio/tv/core/di/SupabaseModule.kt:57-64`.
- The dependency list likewise includes only the Supabase Auth and PostgREST modules; no Supabase Storage or Realtime module is present. Sources: `app/build.gradle.kts:536-540`, `gradle/libs.versions.toml:127-130`.
- `AVATAR_PUBLIC_BASE_URL` is a separate required object-base setting used to turn RPC-provided `storage_path` values into image URLs. Sources: `app/build.gradle.kts:225`, `app/build.gradle.kts:259`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/AvatarRepository.kt:50-54`.
- `local.example.properties` currently documents `SUPABASE_URL` and `SUPABASE_ANON_KEY`, but Gradle reads the `NUVIO_`-prefixed names. The example therefore does not configure the live build as written. Sources: `local.example.properties:1-4`, `app/build.gradle.kts:214-216`, `app/build.gradle.kts:248-250`.

## 1. Required Tables

### Current, directly exposed relations

| Relation | Why it is required | Evidence |
|---|---|---|
| `public.addons` | Direct PostgREST `SELECT`, filtered by effective `user_id` and `profile_id`. | `app/src/main/java/com/nuvio/tv/core/sync/AddonSyncService.kt:89-106` |
| `public.plugins` | Direct PostgREST `SELECT`, filtered by effective `user_id` and `profile_id`. | `app/src/main/java/com/nuvio/tv/core/sync/PluginSyncService.kt:85-102` |
| `public.linked_devices` | Direct PostgREST `SELECT`, filtered by `owner_id`. | `app/src/main/java/com/nuvio/tv/data/repository/SyncRepositoryImpl.kt:88-95` |
| `auth.users` | Supabase-managed user relation. Historical FKs target it, and live auth requires stable user IDs and email. | `docs/supabase_setup.sql@b3a1cae^:22`, `docs/supabase_setup.sql@b3a1cae^:41-42`, `app/src/main/java/com/nuvio/tv/core/auth/AuthSessionValidator.kt:56-60` |

### Physical names found only in deleted historical SQL

The current RPC names still require the represented capabilities, but the current repository does **not** prove that these physical table names remain the implementation. They must not be recreated from history without reconciling the current profile-aware RPC contract.

| Historical relation | Capability still used by the live app | Evidence |
|---|---|---|
| `public.sync_codes` | Generate, retrieve, and claim device sync codes. | `docs/supabase_setup.sql@b3a1cae^:20-29`, `app/src/main/java/com/nuvio/tv/data/repository/SyncRepositoryImpl.kt:23-69` |
| `public.watch_progress` | Full and delta watch-progress sync. | `docs/supabase_setup.sql@b3a1cae^:99-111`, `app/src/main/java/com/nuvio/tv/core/sync/WatchProgressSyncService.kt:94-145`, `app/src/main/java/com/nuvio/tv/core/sync/WatchProgressSyncService.kt:260-283` |
| `public.library_items` | Full and delta saved-library sync. | `docs/supabase_setup.sql@b3a1cae^:122-140`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseLibrarySyncRemoteDataSource.kt:36-117` |
| `public.watched_items` | Full and delta watched-history sync. | `docs/supabase_setup.sql@b3a1cae^:151-164`, `app/src/main/java/com/nuvio/tv/core/sync/WatchedItemsSyncService.kt:90-110`, `app/src/main/java/com/nuvio/tv/core/sync/WatchedItemsSyncService.kt:131-191` |

### Required data whose physical relation names are not present

The live RPCs require persistence or a data source for profiles, profile PIN state, avatar catalog entries, profile settings blobs, collections blobs, home-catalog settings, provider credentials, TV-login sessions, registered devices, and library/watch delta events. No current SQL names a backing table or view for any of these, so no table name is asserted here. Sources: `app/src/main/java/com/nuvio/tv/core/sync/ProfileSyncService.kt:46-199`, `app/src/main/java/com/nuvio/tv/core/sync/ProfileSettingsSyncService.kt:197-243`, `app/src/main/java/com/nuvio/tv/core/sync/CollectionSyncService.kt:53-104`, `app/src/main/java/com/nuvio/tv/core/sync/HomeCatalogSettingsSyncService.kt:172-262`, `app/src/main/java/com/nuvio/tv/core/sync/TraktCredentialCleanupService.kt:37-53`, `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:564-650`, `app/src/main/java/com/nuvio/tv/core/auth/DeviceSessionRegistration.kt:99-121`.

## 2. Columns and Expected Types

### `public.addons`

| Column | Expected contract | Evidence |
|---|---|---|
| `id` | JSON string or null in the live DTO; historical SQL: `UUID NOT NULL DEFAULT gen_random_uuid()`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:25-27`, `docs/supabase_setup.sql@b3a1cae^:79-81` |
| `user_id` | JSON string, required; historical SQL: `UUID NOT NULL`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:28`, `docs/supabase_setup.sql@b3a1cae^:81` |
| `url` | JSON string, required; historical SQL: `TEXT NOT NULL`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:29`, `docs/supabase_setup.sql@b3a1cae^:82` |
| `name` | JSON string or null; historical SQL: nullable `TEXT`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:30`, `docs/supabase_setup.sql@b3a1cae^:83` |
| `enabled` | JSON boolean, default expected `true`; historical SQL: `BOOLEAN NOT NULL DEFAULT true`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:31`, `docs/supabase_setup.sql@b3a1cae^:84` |
| `sort_order` | JSON integer, default expected `0`; historical SQL: `INTEGER NOT NULL DEFAULT 0`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:32`, `docs/supabase_setup.sql@b3a1cae^:85` |
| `profile_id` | JSON integer, default expected `1`; required by the live filter. No SQL in the repository defines this column on `addons`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:33`, `app/src/main/java/com/nuvio/tv/core/sync/AddonSyncService.kt:101-105` |
| `created_at` | JSON timestamp string or null; historical SQL: `TIMESTAMPTZ NOT NULL DEFAULT now()`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:34`, `docs/supabase_setup.sql@b3a1cae^:86` |
| `updated_at` | JSON timestamp string or null; historical SQL: `TIMESTAMPTZ NOT NULL DEFAULT now()`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:35`, `docs/supabase_setup.sql@b3a1cae^:87` |

### `public.plugins`

| Column | Expected contract | Evidence |
|---|---|---|
| `id` | JSON string or null; historical SQL: `UUID NOT NULL DEFAULT gen_random_uuid()`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:11-13`, `docs/supabase_setup.sql@b3a1cae^:59-61` |
| `user_id` | JSON string, required; historical SQL: `UUID NOT NULL`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:14`, `docs/supabase_setup.sql@b3a1cae^:61` |
| `url` | JSON string, required; historical SQL: `TEXT NOT NULL`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:15`, `docs/supabase_setup.sql@b3a1cae^:62` |
| `name` | JSON string or null; historical SQL: nullable `TEXT`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:16`, `docs/supabase_setup.sql@b3a1cae^:63` |
| `enabled` | JSON boolean, default expected `true`; historical SQL: `BOOLEAN NOT NULL DEFAULT true`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:17`, `docs/supabase_setup.sql@b3a1cae^:64` |
| `sort_order` | JSON integer, default expected `0`; historical SQL: `INTEGER NOT NULL DEFAULT 0`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:18`, `docs/supabase_setup.sql@b3a1cae^:65` |
| `profile_id` | JSON integer, default expected `1`; required by the live filter. Deleted plugin SQL assumes an integer parameter/column but does not create the column. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:19`, `app/src/main/java/com/nuvio/tv/core/sync/PluginSyncService.kt:97-101`, `supabase_migrations/add_repo_type_to_plugins.sql@a94003^:10-25` |
| `repo_type` | JSON string or null; deleted migration: nullable `TEXT`. Values sent by the client are enum names. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:20`, `app/src/main/java/com/nuvio/tv/core/sync/PluginSyncService.kt:57-66`, `supabase_migrations/add_repo_type_to_plugins.sql@a94003^:7` |
| `created_at` | JSON timestamp string or null; historical SQL: `TIMESTAMPTZ NOT NULL DEFAULT now()`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:21`, `docs/supabase_setup.sql@b3a1cae^:66` |
| `updated_at` | JSON timestamp string or null; historical SQL: `TIMESTAMPTZ NOT NULL DEFAULT now()`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:22`, `docs/supabase_setup.sql@b3a1cae^:67` |

### `public.linked_devices`

| Column | Expected contract | Evidence |
|---|---|---|
| `id` | JSON string or null; historical SQL: `UUID NOT NULL DEFAULT gen_random_uuid()`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:50-52`, `docs/supabase_setup.sql@b3a1cae^:39-40` |
| `owner_id` | JSON string, required; historical SQL: `UUID NOT NULL`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:53`, `docs/supabase_setup.sql@b3a1cae^:41` |
| `device_user_id` | JSON string, required; historical SQL: `UUID NOT NULL`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:54`, `docs/supabase_setup.sql@b3a1cae^:42` |
| `device_name` | JSON string or null; historical SQL: nullable `TEXT`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:55`, `docs/supabase_setup.sql@b3a1cae^:43` |
| `linked_at` | JSON timestamp string or null; historical SQL: `TIMESTAMPTZ NOT NULL DEFAULT now()`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:56`, `docs/supabase_setup.sql@b3a1cae^:44` |

### Historical RPC-backing tables

These are exact for the deleted setup SQL, followed by the live additions that the wire contract requires. The absence of current SQL means nullability/defaults for each added `profile_id` are unknown.

| Relation | Historical columns and types | Live contract difference | Evidence |
|---|---|---|---|
| `sync_codes` | `id UUID NOT NULL DEFAULT gen_random_uuid()`, `owner_id UUID NOT NULL`, `code TEXT NOT NULL`, `pin_hash TEXT NOT NULL`, `is_active BOOLEAN NOT NULL DEFAULT true`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `expires_at TIMESTAMPTZ DEFAULT infinity`. | Current code adds no visible row fields; storage design remains opaque behind the three code RPCs. | `docs/supabase_setup.sql@b3a1cae^:20-29`, `app/src/main/java/com/nuvio/tv/data/repository/SyncRepositoryImpl.kt:23-69` |
| `watch_progress` | `id UUID`, `user_id UUID`, `content_id TEXT`, `content_type TEXT`, `video_id TEXT`, nullable `season INTEGER`, nullable `episode INTEGER`, `position BIGINT DEFAULT 0`, `duration BIGINT DEFAULT 0`, `last_watched BIGINT DEFAULT 0`, `progress_key TEXT`; required fields are `NOT NULL` in the historical SQL except season/episode. | Live rows additionally contain `profile_id` as a JSON integer defaulting to `1`; all live RPCs are profile-scoped. | `docs/supabase_setup.sql@b3a1cae^:99-111`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:83-97`, `app/src/main/java/com/nuvio/tv/core/sync/WatchProgressSyncService.kt:176-196` |
| `library_items` | `id UUID`, `user_id UUID`, `content_id TEXT`, `content_type TEXT`, `name TEXT DEFAULT ''`, nullable `poster TEXT`, `poster_shape TEXT DEFAULT 'POSTER'`, nullable `background TEXT`, nullable `description TEXT`, nullable `release_info TEXT`, nullable `imdb_rating REAL`, `genres TEXT[] DEFAULT '{}'`, nullable `addon_base_url TEXT`, `added_at BIGINT DEFAULT 0`, nullable/defaulted `created_at TIMESTAMPTZ`, nullable/defaulted `updated_at TIMESTAMPTZ`. | Live rows additionally contain `profile_id` as a JSON integer defaulting to `1`. | `docs/supabase_setup.sql@b3a1cae^:122-140`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseLibrarySyncRemoteDataSource.kt:148-165` |
| `watched_items` | `id UUID`, `user_id UUID NOT NULL`, `content_id TEXT NOT NULL`, `content_type TEXT NOT NULL`, `title TEXT NOT NULL DEFAULT ''`, nullable `season INTEGER`, nullable `episode INTEGER`, `watched_at BIGINT NOT NULL`, nullable/defaulted `created_at TIMESTAMPTZ`. | Live rows additionally contain `profile_id` as a JSON integer defaulting to `1`. | `docs/supabase_setup.sql@b3a1cae^:151-161`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:114-125` |

The remaining RPC response and mutation field contracts are listed with their functions in section 4. Their backing columns cannot be asserted because no physical relation is named.

## 3. Primary and Foreign Keys

- Historical primary keys: `sync_codes.id`, `linked_devices.id`, `plugins.id`, `addons.id`, `watch_progress.id`, `library_items.id`, and `watched_items.id` are UUID primary keys generated with `gen_random_uuid()`. Sources: `docs/supabase_setup.sql@b3a1cae^:20-21`, `docs/supabase_setup.sql@b3a1cae^:39-40`, `docs/supabase_setup.sql@b3a1cae^:59-60`, `docs/supabase_setup.sql@b3a1cae^:79-80`, `docs/supabase_setup.sql@b3a1cae^:99-100`, `docs/supabase_setup.sql@b3a1cae^:122-123`, `docs/supabase_setup.sql@b3a1cae^:151-152`.
- Historical FKs: `sync_codes.owner_id`, `linked_devices.owner_id`, `linked_devices.device_user_id`, and each historical `user_id` reference `auth.users(id) ON DELETE CASCADE`. Sources: `docs/supabase_setup.sql@b3a1cae^:22`, `docs/supabase_setup.sql@b3a1cae^:41-42`, `docs/supabase_setup.sql@b3a1cae^:61`, `docs/supabase_setup.sql@b3a1cae^:81`, `docs/supabase_setup.sql@b3a1cae^:101`, `docs/supabase_setup.sql@b3a1cae^:124`, `docs/supabase_setup.sql@b3a1cae^:153`.
- Historical unique keys: `linked_devices(owner_id, device_user_id)`, `library_items(user_id, content_id, content_type)`, and the watched-item expression `(user_id, content_id, COALESCE(season,-1), COALESCE(episode,-1))`. Sources: `docs/supabase_setup.sql@b3a1cae^:45`, `docs/supabase_setup.sql@b3a1cae^:139`, `docs/supabase_setup.sql@b3a1cae^:163-164`.
- The deleted plugin migration expects conflict identity `(user_id, md5(url), profile_id)`, but neither historical SQL file creates a matching unique constraint/index. Source: `supabase_migrations/add_repo_type_to_plugins.sql@a94003^:27-28`.
- The live app makes `profile_id` part of the logical identity for addons, plugins, watch progress, library items, and watched items. The repository does not define a profile table FK, a `profile_id` FK, or current profile-aware unique keys. Sources: `app/src/main/java/com/nuvio/tv/core/sync/AddonSyncService.kt:73-78`, `app/src/main/java/com/nuvio/tv/core/sync/PluginSyncService.kt:69-74`, `app/src/main/java/com/nuvio/tv/core/sync/WatchProgressSyncService.kt:176-196`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseLibrarySyncRemoteDataSource.kt:88-97`, `app/src/main/java/com/nuvio/tv/core/sync/WatchedItemsSyncService.kt:139-158`.
- `profile_id`/`profile_index` values are app integers in the range used by at most six profiles; `1` is the primary/default profile. This is an application constraint, not evidence of a database check constraint. Sources: `app/src/main/java/com/nuvio/tv/core/profile/ProfileManager.kt:28-35`, `app/src/main/java/com/nuvio/tv/domain/model/UserProfile.kt:12`, `app/src/main/java/com/nuvio/tv/core/profile/ProfileManager.kt:82-83`.
- No PK/FK can be stated for the RPC-only profile, lock, settings, collection, home-catalog, avatar, TV-login, device-registration, provider-credential, or delta-event data.

## 4. Required RPCs and Functions

Wire type notation below follows Kotlin serialization: `String`, `Int`, `Long`, `Float`, `Boolean`, JSON object/array, and nullable `?`. PostgreSQL argument/return types are unknown unless the historical SQL happens to define the same signature.

### Identity, linking, login, device, and avatars

| Function | Parameters | Required response | Evidence |
|---|---|---|---|
| `generate_sync_code` | `p_pin: String` | List with at least one `{code: String}` row. | `app/src/main/java/com/nuvio/tv/data/repository/SyncRepositoryImpl.kt:23-30`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:38-41` |
| `get_sync_code` | `p_pin: String` | List with at least one `{code: String}` row when a code exists. | `app/src/main/java/com/nuvio/tv/data/repository/SyncRepositoryImpl.kt:37-44`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:38-41` |
| `claim_sync_code` | `p_code: String`, `p_pin: String`, optional `p_device_name: String` | List with `{result_owner_id: String?, success: Boolean, message: String}`. | `app/src/main/java/com/nuvio/tv/data/repository/SyncRepositoryImpl.kt:51-69`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:43-48` |
| `unlink_device` | `p_device_user_id: String` | Body is not decoded; a successful PostgREST response is sufficient. | `app/src/main/java/com/nuvio/tv/data/repository/SyncRepositoryImpl.kt:76-81` |
| `get_sync_owner` | None | Scalar string user ID. It must return the linked owner ID for a linked device, otherwise the caller ID. | `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:257-275`, `docs/supabase_setup.sql@b3a1cae^:200-214` |
| `get_sync_overview` | None | Object with `addons`, `plugins`, `library_items`, `watch_progress`, and `watched_items` as maps from string profile ID to integer count; `profiles` maps profile ID to `{name: String, color: String}`. | `app/src/main/java/com/nuvio/tv/ui/screens/account/AccountViewModel.kt:485-507` |
| `register_current_device` | `p_installation_id: String`, `p_client_name: String`, `p_client_version: String`, `p_platform: String`, `p_device_name: String` | Body is not decoded. Client name is `Nuvio TV`; device name is capped at 160 characters; platform is `Android TV <OS version>`. | `app/src/main/java/com/nuvio/tv/core/auth/DeviceSessionRegistration.kt:26-29`, `app/src/main/java/com/nuvio/tv/core/auth/DeviceSessionRegistration.kt:99-121`, `app/src/main/java/com/nuvio/tv/core/auth/DeviceSessionRegistration.kt:123-144` |
| `start_tv_login_session` | `p_device_nonce: String`, `p_redirect_base_url: String`, optional `p_device_name: String`; the client retries without `p_device_name` for a legacy signature. | List with `{code: String, web_url: String, expires_at: String, poll_interval_seconds: Int}`. | `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:514-589`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:59-65` |
| `poll_tv_login_session` | `p_code: String`, `p_device_nonce: String` | List with `{status: String, expires_at: String?, poll_interval_seconds: Int?}`. Recognized statuses are `approved`, `pending`, `expired`, `used`, and `cancelled`. | `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:592-618`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:67-72`, `app/src/main/java/com/nuvio/tv/ui/screens/account/AccountViewModel.kt:653-680` |
| `get_avatar_catalog` | None | List rows `{id: String, display_name: String, storage_path: String, category: String, sort_order: Int, bg_color: String?}`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/AvatarRepository.kt:23-35`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:167-175` |

### Profiles, locks, settings, and collections

| Function | Parameters | Required response | Evidence |
|---|---|---|---|
| `sync_push_profiles` | `p_client_max_profiles: Int` (client sends `6`), `p_profiles: [{profile_index: Int, name: String, avatar_color_hex: String, uses_primary_addons: Boolean, uses_primary_plugins: Boolean, avatar_id: String?, avatar_url: String?}]`, `p_origin_client_id: String` | Body is not decoded. | `app/src/main/java/com/nuvio/tv/core/sync/ProfileSyncService.kt:46-69`, `app/src/main/java/com/nuvio/tv/core/profile/ProfileManager.kt:28-29` |
| `sync_pull_profiles` | None | List rows `{id: String?, user_id: String?, profile_index: Int, name: String, avatar_color_hex: String, uses_primary_addons: Boolean, uses_primary_plugins: Boolean, avatar_id: String?, avatar_url: String?, created_at: String?, updated_at: String?}`. | `app/src/main/java/com/nuvio/tv/core/sync/ProfileSyncService.kt:79-98`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:139-152` |
| `sync_delete_profile_data` | `p_profile_id: Int`, `p_origin_client_id: String` | Body is not decoded. | `app/src/main/java/com/nuvio/tv/core/sync/ProfileSyncService.kt:112-123` |
| `sync_pull_profile_locks` | None | List rows `{profile_index: Int, pin_enabled: Boolean, pin_locked_until: String?}`. | `app/src/main/java/com/nuvio/tv/core/sync/ProfileSyncService.kt:130-137`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:154-159` |
| `set_profile_pin` | `p_profile_id: Int`, `p_pin: String`, optional `p_current_pin: String` | Body is not decoded. Backend may reject replacement with the message `Current PIN is required`. | `app/src/main/java/com/nuvio/tv/core/sync/ProfileSyncService.kt:144-168` |
| `clear_profile_pin` | `p_profile_id: Int`, optional `p_current_pin: String` | Body is not decoded. | `app/src/main/java/com/nuvio/tv/core/sync/ProfileSyncService.kt:170-184` |
| `verify_profile_pin` | `p_profile_id: Int`, `p_pin: String` | First list row `{unlocked: Boolean, retry_after_seconds: Int}`; the client defaults to false/0 on an empty list. | `app/src/main/java/com/nuvio/tv/core/sync/ProfileSyncService.kt:188-199`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:161-165` |
| `sync_push_profile_settings_blob` | `p_profile_id: Int`, `p_settings_json: Object`, `p_platform: String` (`tv`), `p_origin_client_id: String` | Body is not decoded. | `app/src/main/java/com/nuvio/tv/core/sync/ProfileSettingsSyncService.kt:64-65`, `app/src/main/java/com/nuvio/tv/core/sync/ProfileSettingsSyncService.kt:197-212` |
| `sync_pull_profile_settings_blob` | `p_profile_id: Int`, `p_platform: String` (`tv`) | List rows `{profile_id: Int, settings_json: Object, updated_at: String?}`. | `app/src/main/java/com/nuvio/tv/core/sync/ProfileSettingsSyncService.kt:223-243`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:177-182` |
| `sync_push_collections` | `p_profile_id: Int`, `p_collections_json: JSON array/object`, `p_origin_client_id: String` | Body is not decoded. | `app/src/main/java/com/nuvio/tv/core/sync/CollectionSyncService.kt:53-72` |
| `sync_pull_collections` | `p_profile_id: Int` | List rows `{profile_id: Int, collections_json: JSON, updated_at: String?}`. | `app/src/main/java/com/nuvio/tv/core/sync/CollectionSyncService.kt:86-104`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:184-189` |
| `sync_push_home_catalog_settings` | `p_profile_id: Int`, `p_settings_json: Object`, `p_platform: String` (`home_catalog_shared`), `p_origin_client_id: String` | Body is not decoded. | `app/src/main/java/com/nuvio/tv/core/sync/HomeCatalogSettingsSyncService.kt:172-184` |
| `sync_pull_home_catalog_settings` | `p_profile_id: Int`, `p_platform: String`; the app reads `home_catalog_shared`, `tv`, and `mobile`. | List rows `{profile_id: Int, settings_json: Object, updated_at: String?}`. | `app/src/main/java/com/nuvio/tv/core/sync/HomeCatalogSettingsSyncService.kt:30-35`, `app/src/main/java/com/nuvio/tv/core/sync/HomeCatalogSettingsSyncService.kt:251-262`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:191-196` |
| `sync_delete_provider_credentials` | `p_profile_id: Int`, `p_provider: String` (only `trakt` is sent), `p_origin_client_id: String` | Body is not decoded. | `app/src/main/java/com/nuvio/tv/core/sync/TraktCredentialCleanupService.kt:16`, `app/src/main/java/com/nuvio/tv/core/sync/TraktCredentialCleanupService.kt:37-53` |

Profile settings JSON is exactly `{version: 1, features: {<feature>: {<preference-key>: {type, value}}}}`. Supported value tags are `string`, `boolean`, `int`, `long`, `float`, `double`, and `string_set`. Synced feature names are `theme_settings`, `layout_settings`, `experience_settings`, `player_settings`, `stream_badge_settings`, `trailer_settings`, `tmdb_settings`, `mdblist_settings`, `trakt_settings`, `debrid_settings`, `animeskip_settings`, and `track_preference`. Sources: `app/src/main/java/com/nuvio/tv/core/sync/ProfileSettingsSyncService.kt:169-182`, `app/src/main/java/com/nuvio/tv/core/sync/ProfileSettingsSyncService.kt:286-305`, `app/src/main/java/com/nuvio/tv/core/sync/ProfileSettingsSyncService.kt:550-585`, `app/src/main/java/com/nuvio/tv/data/local/ExperienceModeDataStore.kt:21`, `app/src/main/java/com/nuvio/tv/data/local/StreamBadgeSettingsDataStore.kt:39`.

Home-catalog settings JSON is `{hide_unreleased_content: Boolean, items: [{addon_id: String, type: String, catalog_id: String, enabled: Boolean, order: Int, custom_title: String, is_collection: Boolean, collection_id: String}]}`. Source: `app/src/main/java/com/nuvio/tv/core/sync/HomeCatalogSettingsSyncService.kt:37-53`.

### Addons, plugins, library, progress, and watched history

| Function | Parameters | Required response | Evidence |
|---|---|---|---|
| `sync_push_addons` | `p_addons: [{url: String, sort_order: Int, enabled: Boolean, name?: String}]`, `p_profile_id: Int`, `p_origin_client_id: String` | Body is not decoded. | `app/src/main/java/com/nuvio/tv/core/sync/AddonSyncService.kt:58-79` |
| `sync_push_plugins` | `p_plugins: [{url: String, name: String, enabled: Boolean, sort_order: Int, repo_type: String}]`, `p_profile_id: Int`, `p_origin_client_id: String` | Body is not decoded. | `app/src/main/java/com/nuvio/tv/core/sync/PluginSyncService.kt:57-75` |
| `sync_pull_library` | `p_profile_id: Int`, `p_limit: Int`, `p_offset: Int` | List rows `{id: String?, user_id: String?, content_id: String, content_type: String, name: String, poster: String?, poster_shape: String, background: String?, description: String?, release_info: String?, imdb_rating: Float?, genres: [String], addon_base_url: String?, added_at: Long, profile_id: Int}`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseLibrarySyncRemoteDataSource.kt:36-49`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseLibrarySyncRemoteDataSource.kt:148-165` |
| `sync_get_library_delta_cursor` | `p_profile_id: Int` | Scalar `Long`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseLibrarySyncRemoteDataSource.kt:52-58` |
| `sync_pull_library_delta` | `p_profile_id: Int`, `p_since_event_id: Long`, `p_limit: Int` | List rows with `event_id: Long`, `operation: String`, and all library content fields except `id`, `user_id`, and `profile_id`. Operations consumed are `upsert` and `delete`. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseLibrarySyncRemoteDataSource.kt:61-79`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseLibrarySyncRemoteDataSource.kt:167-183`, `app/src/main/java/com/nuvio/tv/domain/model/LibrarySyncReducer.kt:166-179` |
| `sync_push_library_items` | `p_items: [{content_id: String, content_type: String, name: String, poster: String?, poster_shape: String, background: String?, description: String?, release_info: String?, imdb_rating: Float?, genres: [String], addon_base_url: String?, added_at: Long}]`, `p_profile_id: Int`, `p_origin_client_id: String` | Body is not decoded. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseLibrarySyncRemoteDataSource.kt:83-99`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseLibrarySyncRemoteDataSource.kt:185-199` |
| `sync_delete_library_items` | `p_keys: [{content_id: String, content_type: String}]`, `p_profile_id: Int`, `p_origin_client_id: String` | Body is not decoded. | `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseLibrarySyncRemoteDataSource.kt:102-117`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseLibrarySyncRemoteDataSource.kt:201-205` |
| `sync_get_watch_progress_delta_cursor` | `p_profile_id: Int` | Scalar `Long`. | `app/src/main/java/com/nuvio/tv/core/sync/WatchProgressSyncService.kt:94-103` |
| `sync_pull_watch_progress_delta` | `p_profile_id: Int`, `p_since_event_id: Long`, `p_limit: Int` | List rows `{event_id: Long, operation: String, progress_key: String, content_id: String, content_type: String, video_id: String, season: Int?, episode: Int?, position: Long, duration: Long, last_watched: Long}`. Operations consumed are `upsert` and `delete`. | `app/src/main/java/com/nuvio/tv/core/sync/WatchProgressSyncService.kt:106-120`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:99-112`, `app/src/main/java/com/nuvio/tv/core/sync/WatchProgressSyncService.kt:31-33` |
| `sync_push_watch_progress` | `p_entries: [{content_id: String, content_type: String, video_id: String, season?: Int, episode?: Int, position: Long, duration: Long, last_watched: Long, progress_key: String}]`, `p_profile_id: Int`, `p_origin_client_id: String` | Body is not decoded. | `app/src/main/java/com/nuvio/tv/core/sync/WatchProgressSyncService.kt:163-197` |
| `sync_pull_watch_progress` | `p_profile_id: Int`, optional `p_since_last_watched: Long`, optional `p_limit: Int` | List rows `{id: String?, user_id: String, content_id: String, content_type: String, video_id: String, season: Int?, episode: Int?, position: Long, duration: Long, last_watched: Long, progress_key: String, profile_id: Int}`. | `app/src/main/java/com/nuvio/tv/core/sync/WatchProgressSyncService.kt:260-283`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:83-97` |
| `sync_delete_watch_progress` | `p_keys: [String]`, `p_profile_id: Int`, `p_origin_client_id: String` | Body is not decoded. | `app/src/main/java/com/nuvio/tv/core/sync/WatchProgressSyncService.kt:124-146` |
| `sync_get_watched_items_delta_cursor` | `p_profile_id: Int` | Scalar `Long`. | `app/src/main/java/com/nuvio/tv/core/sync/WatchedItemsSyncService.kt:90-99` |
| `sync_pull_watched_items_delta` | `p_profile_id: Int`, `p_since_event_id: Long`, `p_limit: Int` | List rows `{event_id: Long, operation: String, content_id: String, content_type: String, title: String, season: Int?, episode: Int?, watched_at: Long}`. Operations consumed are `upsert` and `delete`. | `app/src/main/java/com/nuvio/tv/core/sync/WatchedItemsSyncService.kt:102-116`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:127-137`, `app/src/main/java/com/nuvio/tv/core/sync/WatchedItemsSyncService.kt:30-33` |
| `sync_push_watched_items` | `p_items: [{content_id: String, content_type: String, title: String, season: Int?, episode: Int?, watched_at: Long}]`, `p_profile_id: Int`, `p_origin_client_id: String` | Body is not decoded. | `app/src/main/java/com/nuvio/tv/core/sync/WatchedItemsSyncService.kt:131-159` |
| `sync_pull_watched_items` | `p_profile_id: Int`, `p_page: Int`, `p_page_size: Int` | List rows `{id: String?, user_id: String?, content_id: String, content_type: String, title: String, season: Int?, episode: Int?, watched_at: Long, profile_id: Int}`. | `app/src/main/java/com/nuvio/tv/core/sync/WatchedItemsSyncService.kt:172-208`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:114-125` |
| `sync_delete_watched_items` | `p_profile_id: Int`, `p_keys: [{content_id: String, season?: Int, episode?: Int}]`, `p_origin_client_id: String` | Body is not decoded. | `app/src/main/java/com/nuvio/tv/core/sync/WatchedItemsSyncService.kt:380-432` |

Every mutation carrying `p_origin_client_id` receives a stable string generated as `nuvio-tv-` plus 32 lowercase alphanumeric characters; stored values are accepted only at length 16-96 with letters, digits, `-`, or `_`. Sources: `app/src/main/java/com/nuvio/tv/core/sync/SyncClientIdentity.kt:11-14`, `app/src/main/java/com/nuvio/tv/core/sync/SyncClientIdentity.kt:27-56`.

### Edge Function

| Function | Request | Response | Evidence |
|---|---|---|---|
| `tv-logins-exchange` (`/functions/v1/tv-logins-exchange`) | `{code: String, device_nonce: String}` using the anon API key and no bearer token | `{access_token: String, refresh_token: String, token_type: String?, expires_in: Long?, user: UserInfo?}`; `expires_in` must be present and greater than zero. | `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:625-650`, `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:777-783`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:74-81`, `app/src/main/java/com/nuvio/tv/core/auth/SupabaseAuthSession.kt:8-23` |

No Edge Function source is present in this repository.

## 5. Storage Buckets

- **No bucket name is present in the repository.** There are no Supabase Storage SDK calls and no Storage dependency/plugin. Sources: `app/build.gradle.kts:536-540`, `app/src/main/java/com/nuvio/tv/core/di/SupabaseModule.kt:57-64`.
- The app does require readable avatar objects: `get_avatar_catalog` returns `storage_path`, and the client resolves it against `AVATAR_PUBLIC_BASE_URL` (or uses the path verbatim when the base is blank). Sources: `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:167-175`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/AvatarRepository.kt:50-54`.
- Because `MainActivity` loads the avatar catalog unconditionally, the catalog RPC and resulting image URLs must work before sign-in. Source: `app/src/main/java/com/nuvio/tv/MainActivity.kt:386-390`.
- Therefore the exact bucket cannot be documented without guessing. The evidence supports only a public/readable object-base contract; it does not support a bucket name, upload permission, or object-key policy.

## 6. Required Auth Providers

- **Email/password is required.** The app signs up with `{email,password}` at `/auth/v1/signup` and signs in with password grant at `/auth/v1/token?grant_type=password`. Sources: `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:54-57`, `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:305-350`.
- Refresh tokens are required at `/auth/v1/token?grant_type=refresh_token`, and returned sessions require access token, refresh token, and positive `expires_in`. Sources: `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:461-485`, `app/src/main/java/com/nuvio/tv/core/auth/SupabaseAuthSession.kt:13-23`.
- The custom TV QR flow is also required, but it is not a Supabase Auth provider: it uses `start_tv_login_session`, `poll_tv_login_session`, and the `tv-logins-exchange` Edge Function to import a token response. Sources: `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:564-650`, `app/src/main/java/com/nuvio/tv/core/auth/SupabaseAuthSession.kt:8-11`.
- Session validation requires the remote and local user IDs to match and the user email to be nonblank. Source: `app/src/main/java/com/nuvio/tv/core/auth/AuthSessionValidator.kt:41-68`.
- No current Supabase OAuth, OTP/magic-link, phone, or anonymous sign-in call exists. The deleted setup SQL instructed enabling anonymous sign-ins, but that is historical and is not supported by a current client call. Source for the stale instruction: `docs/supabase_setup.sql@b3a1cae^:7-9`.

## 7. Required RLS Policies

The following are functional policy requirements; exact SQL is not invented where current migrations are absent.

| Relation/surface | Required behavior | Evidence |
|---|---|---|
| `addons` | Authenticated callers must be able to `SELECT` rows whose `user_id` is their effective sync owner and whose `profile_id` is requested. For a linked device, `auth.uid()` differs from the selected owner ID, so an owner-only policy is insufficient; an equivalent of `can_access_user_data(user_id)` is required. Direct write policies are not required by the client because writes use a SECURITY DEFINER RPC. | `app/src/main/java/com/nuvio/tv/core/sync/AddonSyncService.kt:38-41`, `app/src/main/java/com/nuvio/tv/core/sync/AddonSyncService.kt:89-106`, `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:257-275`, `docs/supabase_setup.sql@b3a1cae^:220-239` |
| `plugins` | Same linked-owner `SELECT` behavior as `addons`; writes use a SECURITY DEFINER RPC. | `app/src/main/java/com/nuvio/tv/core/sync/PluginSyncService.kt:39-42`, `app/src/main/java/com/nuvio/tv/core/sync/PluginSyncService.kt:85-102`, `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:257-275` |
| `linked_devices` | Owner can read links where `auth.uid() = owner_id`; linked device can read its own link where `auth.uid() = device_user_id`. | `app/src/main/java/com/nuvio/tv/data/repository/SyncRepositoryImpl.kt:88-95`, `docs/supabase_setup.sql@b3a1cae^:48-56` |
| `sync_codes` (if retained) | Historical policy permits users to manage only rows where `auth.uid() = owner_id`; current code supplies no replacement policy. | `docs/supabase_setup.sql@b3a1cae^:31-36` |
| Historical user-data tables (if retained) | Historical owner-only policies exist for `addons`, `plugins`, `watch_progress`, `library_items`, and `watched_items`. The `addons`/`plugins` policies are incompatible with current linked-device direct reads. RPC-mediated surfaces must resolve the effective owner server-side and prevent arbitrary cross-owner access. | `docs/supabase_setup.sql@b3a1cae^:71-76`, `docs/supabase_setup.sql@b3a1cae^:91-96`, `docs/supabase_setup.sql@b3a1cae^:114-119`, `docs/supabase_setup.sql@b3a1cae^:143-148`, `docs/supabase_setup.sql@b3a1cae^:168-173`, `app/src/main/java/com/nuvio/tv/core/sync/WatchProgressSyncService.kt:251-254` |
| RPC-only data | Current relation names and policies are unknown. Owner-scoped RPCs must derive identity from `auth.uid()`/`get_sync_owner()` rather than accepting `user_id` from the client; the client never sends a user ID to these sync RPCs. | `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:257-275`, `app/src/main/java/com/nuvio/tv/core/sync/SyncClientIdentity.kt:55-56` |
| Avatar objects | If Supabase Storage backs `AVATAR_PUBLIC_BASE_URL`, objects must be publicly readable or otherwise readable with no user bearer token. Bucket/policy names are unknown. | `app/src/main/java/com/nuvio/tv/MainActivity.kt:386-390`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/AvatarRepository.kt:50-54` |

## 8. Required Grants

- `authenticated` requires `SELECT` on `public.addons`, `public.plugins`, and `public.linked_devices`, subject to the RLS behavior above. The app performs no direct `INSERT`, `UPDATE`, or `DELETE` on a table. Sources: `app/src/main/java/com/nuvio/tv/core/sync/AddonSyncService.kt:100-106`, `app/src/main/java/com/nuvio/tv/core/sync/PluginSyncService.kt:96-102`, `app/src/main/java/com/nuvio/tv/data/repository/SyncRepositoryImpl.kt:88-95`.
- `authenticated` requires `EXECUTE` on every owner/profile/media RPC listed in section 4, including the link-code RPCs. The deleted SQL explicitly granted only its older 14-function set and therefore does not cover the current contract. Sources: `docs/supabase_setup.sql@b3a1cae^:217-242`, `docs/supabase_setup.sql@b3a1cae^:299-388`, `docs/supabase_setup.sql@b3a1cae^:422-599`.
- `anon` requires `EXECUTE` on `start_tv_login_session`, `poll_tv_login_session`, and `get_avatar_catalog`: start/poll are sent with only the anon `apikey`, and avatar catalog is loaded before authentication. Sources: `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:564-618`, `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:777-783`, `app/src/main/java/com/nuvio/tv/MainActivity.kt:386-390`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/AvatarRepository.kt:23-35`.
- The `tv-logins-exchange` Edge Function must accept invocation with the anon key and no bearer user token. Sources: `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:625-650`, `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:777-783`.
- Any SECURITY DEFINER function must have deliberate `EXECUTE` grants and must validate the caller/effective owner internally. The client comments explicitly require SECURITY DEFINER behavior for addon, plugin, collection, and watch-progress sync. Sources: `app/src/main/java/com/nuvio/tv/core/sync/AddonSyncService.kt:38-41`, `app/src/main/java/com/nuvio/tv/core/sync/PluginSyncService.kt:39-42`, `app/src/main/java/com/nuvio/tv/core/sync/CollectionSyncService.kt:49-52`, `app/src/main/java/com/nuvio/tv/core/sync/WatchProgressSyncService.kt:251-254`.
- No evidence supports direct table grants to `anon`, direct table mutation grants to app roles, Storage upload grants, or `service_role` use by the Android client.

## 9. Realtime-Enabled Tables

**None are required by the current client.** Repository-wide searches found no Supabase channel, Postgres Changes, broadcast, or presence subscription. The client has no Realtime dependency and installs no Realtime plugin. Sources: `app/build.gradle.kts:536-540`, `app/src/main/java/com/nuvio/tv/core/di/SupabaseModule.kt:57-64`.

`StartupSyncService.requestRealtimeSurfacePull` is only a local method that triggers ordinary pull calls; no caller or Supabase subscription exists. It is not evidence that any table must be in the `supabase_realtime` publication. Source: `app/src/main/java/com/nuvio/tv/core/sync/StartupSyncService.kt:194-259`.

## 10. Existing SQL Migrations That Should Be Run

**None.** The current checkout contains no `*.sql` files and no `supabase/` directory. The only SQL recoverable from Git history was deleted:

1. `docs/supabase_setup.sql` (deleted by commit `b3a1cae`) created seven early tables, owner-only RLS, and an older 12-app-RPC API plus two helpers. Its sync functions lack current `p_profile_id`, `p_origin_client_id`, paging, delta, settings, profiles, PINs, avatars, TV login, device registration, and overview contracts. It also defines `sync_push_library`, while the current app calls `sync_push_library_items`. Sources: `docs/supabase_setup.sql@b3a1cae^:20-173`, `docs/supabase_setup.sql@b3a1cae^:198-599`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseLibrarySyncRemoteDataSource.kt:83-98`.
2. `supabase_migrations/add_repo_type_to_plugins.sql` (deleted by commit `a94003`) adds `plugins.repo_type` and replaces `sync_push_plugins(p_plugins JSONB, p_profile_id INT DEFAULT 1)`. It assumes an existing `profile_id`, assumes a conflict identity not created in the file, omits `p_origin_client_id`, and has no explicit grant. Sources: `supabase_migrations/add_repo_type_to_plugins.sql@a94003^:7-46`, `app/src/main/java/com/nuvio/tv/core/sync/PluginSyncService.kt:57-75`.

These files are useful historical evidence but are not a runnable migration chain for the current app. Running either as the authoritative schema would leave the live contract incomplete and, for linked addon/plugin reads, retain incompatible owner-only RLS.

## 11. Missing Backend Components

1. **A current migration chain is missing.** No checked-in SQL defines the live three-table shape, all 41 RPCs, their current overloads/signatures, profile-aware keys, policies, and grants. Evidence: the current call inventory in section 4 versus the deleted function list at `docs/supabase_setup.sql@b3a1cae^:198-599`.
2. **`profile_id` migrations and profile-aware constraints are missing.** Historical `addons`, `plugins`, `watch_progress`, `library_items`, and `watched_items` do not define `profile_id`; the deleted plugin patch merely assumes it. Sources: `docs/supabase_setup.sql@b3a1cae^:59-173`, `supabase_migrations/add_repo_type_to_plugins.sql@a94003^:10-27`, live fields at `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:19`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:33`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:96`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:124` and `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseLibrarySyncRemoteDataSource.kt:164`.
3. **Current linked-device read policies are missing.** Historical addon/plugin policies permit only `auth.uid() = user_id`, while live linked devices directly select the owner’s rows. Sources: `docs/supabase_setup.sql@b3a1cae^:73-76`, `docs/supabase_setup.sql@b3a1cae^:93-96`, `app/src/main/java/com/nuvio/tv/core/sync/AddonSyncService.kt:89-106`, `app/src/main/java/com/nuvio/tv/core/sync/PluginSyncService.kt:85-102`.
4. **RPC-only physical schema is missing.** The repository does not define backing relations/keys for profiles, profile locks and PIN retry state, profile settings, collections, home catalog settings, provider credentials, avatars, registered devices, TV-login sessions, or any of the three delta streams. Their wire contracts are documented in section 4; physical schema must be obtained from the deployed project or authored separately without inferring table names.
5. **The `tv-logins-exchange` Edge Function implementation is missing.** Only the Android invocation and token response contract exist. Sources: `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:625-650`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:74-81`.
6. **The TV-login approval backend/web implementation is missing.** The client supplies a redirect base URL, receives `web_url`, and polls, but this repository contains no server that approves a code. Sources: `app/src/main/java/com/nuvio/tv/core/auth/AuthManager.kt:564-618`, `app/build.gradle.kts:217`, `app/build.gradle.kts:251`.
7. **The avatar backend is incomplete in-repo.** `get_avatar_catalog`, its backing data, the bucket/object deployment, and public object policy are absent; only the expected row and URL composition exist. Sources: `app/src/main/java/com/nuvio/tv/data/remote/supabase/AvatarRepository.kt:23-54`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:167-175`.
8. **The sample environment keys are inconsistent.** `local.example.properties` must use `NUVIO_SUPABASE_URL` and `NUVIO_SUPABASE_ANON_KEY` to match Gradle. Sources: `local.example.properties:1-2`, `app/build.gradle.kts:214-215`, `app/build.gradle.kts:248-249`.
9. **Provider-credential persistence is not fully specified.** The live app only calls delete for provider `trakt`; `SupabaseProviderCredential` exists as a response DTO, but no pull/push RPC uses it. This is insufficient evidence to invent a credential table or additional RPC. Sources: `app/src/main/java/com/nuvio/tv/core/sync/TraktCredentialCleanupService.kt:16-53`, `app/src/main/java/com/nuvio/tv/data/remote/supabase/SupabaseModels.kt:198-203`.

The exact deployed Supabase schema cannot be reconstructed beyond this contract from the repository alone. In particular, current function SQL, overload resolution, current PK/FK/index definitions, hidden relation names, and the avatar bucket name require access to the deployed Supabase project or an authoritative migration source.
