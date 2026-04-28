# RecklessPlayPenalty module

Status: ready-for-human
Cross-ref: [GH #98](https://github.com/assofohdz/subspace-infinity/issues/98)
Parent: [../PRD.md](../PRD.md)
Labels: area:modules, mode:match

Part of the parent. Phase 3 — anti-grief for match arenas.

## What it does

Penalizes players who die within N seconds of match start (discouraging throw-the-match behavior). Penalty duration scales with how early the death happens.

## Arenas that need it

`2v2pub`, `3v3pub`, `4v4pub`, `4v4prac`, `4v4caps`.

## Settings surface

Threshold (e.g. 300s window) and penalty range (120–600s lockout). Specific keys live in `[SS.Matchmaking.RecklessPlayPenalty]` or similar — confirm at implementation.

## Integration on Infinity

- Hook: player-death event with match context
- Action: apply temporary spec lock / match-join cooldown
- Depends on: match lifecycle (for the "match-start timestamp" reference), player identity

## Comments
