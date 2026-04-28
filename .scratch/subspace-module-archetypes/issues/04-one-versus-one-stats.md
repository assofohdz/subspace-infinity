# OneVersusOneStats module

Status: ready-for-human
Cross-ref: [GH #90](https://github.com/assofohdz/subspace-infinity/issues/90)
Parent: [../PRD.md](../PRD.md)
Labels: area:modules, mode:match

Part of the parent. Phase 1 — 1v1 dueling stats.

## What it does

Per-duel match stats tracking: wins, losses, K/D, session history. Ties matches to player identity for ranked dueling.

## Arenas that need it

`duel`.

## Settings surface

Stats DB keys, match-end events. Arena config: `[General] SeeEnergy=None`, `NearDeathLevel`, `InitialSpec=1` (matchmaking controls spawn).

## Integration on Infinity

- Requires: match lifecycle (start/end), player identity, persistence layer
- Depends on: basic matchmaking (may need to land alongside a minimal match-state system)
- Persistence: Infinity's `EntityData` vs. a separate stats DB — design decision

## Comments
