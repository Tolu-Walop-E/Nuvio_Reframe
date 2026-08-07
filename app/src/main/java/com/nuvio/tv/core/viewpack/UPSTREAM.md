# Pack runtime → upstream

Thin slices so Reframe Studio packs customize **Netflix home** without shipping the Debug fork.

## What to PR

1. **Pack schema + Netflix binding** — `showFocusedPosterInfo`, hero helpers, pack-forces-Netflix, footer + hero pin.
2. **Rail scales** — `homeRowScalesFromPack` + `catalogueRailGeometry(scale)`.
3. **Collection open style** — pack `collectionsOpenInReframe` + per-rail `collectionOpenStyle` via `FolderDetailViewModel.resolvePackOpenPrefs`.
4. **Trailer + poster grow** — per-block `trailer` / `posterGrow` on catalog rails + hero trailer gate.
5. **Pack = source of truth** — when a pack is active, disable Netflix collection fan-out and discovery rail injection so home matches Studio order.

Contract: Reframe Studio `PACK_RUNTIME_CONTRACT.md`.

## Users

Layout settings → View pack import (clipboard / URL). Or push `current.view.json` to the app external files dir for auto-import.

## Debug fork

Treat `NuvioTV_Fork` as a temporary lab. After vanilla proves, rebase experiments onto vanilla and drop the duplicate Netflix port.
