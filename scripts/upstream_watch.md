# Upstream Nuvio watch

Reframe tracks `NuvioMedia/NuvioTV` `dev` without merging it.

```powershell
python scripts/upstream_watch.py
```

- **HIGH** — home / Coil / prefetch / perf. Port by hand if the files still exist here.
- **CONFLICT** — usually trailers. We capped YouTube at 720p for Shield; do not restore 4K streams.
- **MEDIUM** — player, TMDB, subtitles, watched. Review.
- Never `git merge origin/dev` onto a Netflix-home branch.

Last reviewed upstream tip: `082af4e29` (2026-08-18). Merge-base with this branch: `f6dc2f1c7` (2026-08-07).

Taken in 0.8.25-reframe:

- Coil cache 15/20/25% by RAM (keep `allowHardware(true)` — software bitmaps blew heap on Shield)
- Drop offscreen compositing on Modern poster cards
- Tighter Modern poster prefetch + 240ms vertical debounce
- Cancel the previous home trailer extract on focus change
- Hot `StateFlow` for installed addons
- Background meta prefetch on collection focus
