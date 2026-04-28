# MatchLvz module

Status: ready-for-human
Cross-ref: [GH #97](https://github.com/assofohdz/subspace-infinity/issues/97)
Parent: [../PRD.md](../PRD.md)
Labels: area:modules, mode:match

Part of the parent. Phase 3 — match-HUD overlays via LVZ.

## What it does

Manages LVZ (client-side visual overlay) objects tied to match state: scoreboard, timer, statbox, match-status banners. Shows/hides overlays as matches start and end.

## Arenas that need it

All matchmaking arenas with `LevelFiles = match.lvz`.

## Settings surface

`[General] LevelFiles=<lvz-name>` plus per-match LVZ object toggle rules.

## Integration on Infinity

- Hook: match lifecycle events → toggle LVZ object visibility for match participants
- Depends on: working LVZ loader (currently a placeholder — needs real parser first; see `MapSystem.loadMap`'s `.lvz` path, and standalone PRD [`lvz-files`](../../lvz-files/PRD.md))
- Scope: client-side rendering work likely needed alongside server-side toggle logic

## Comments
