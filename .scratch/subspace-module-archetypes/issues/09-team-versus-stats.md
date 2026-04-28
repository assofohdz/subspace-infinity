# TeamVersusStats module

Status: ready-for-human
Cross-ref: [GH #95](https://github.com/assofohdz/subspace-infinity/issues/95)
Parent: [../PRD.md](../PRD.md)
Labels: area:modules, mode:match

Part of the parent. Phase 3 — small-team match stats.

## What it does

Per-team, per-match stats tracking for small-format arenas (2v2, 3v3, 4v4). Records wins/losses, K/D, match duration; persists to stats store with seasonal scoping.

## Arenas that need it

`2v2pub`, `2v2league`, `3v3pub`, `4v4pub`, `4v4league`, `4v4prac`, `4v4caps`.

## Settings surface

`[SS.Matchmaking.TeamVersusMatch]` section — match rules (lives, time limit). `DefaultSeasonId` for league persistence.

## Integration on Infinity

- Hook: match-start / match-end lifecycle from the underlying matchmaking system
- State: match record with participants, teams, score, outcome
- Persistence: needs a stats store (design choice: embedded DB vs. external)
- Depends on: basic match lifecycle (`MatchFocus` or the matchmaking core)

## Comments
