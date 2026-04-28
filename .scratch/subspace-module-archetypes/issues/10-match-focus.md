# MatchFocus module

Status: ready-for-human
Cross-ref: [GH #96](https://github.com/assofohdz/subspace-infinity/issues/96)
Parent: [../PRD.md](../PRD.md)
Labels: area:modules, mode:match

Part of the parent. Phase 3 — match isolation inside a shared arena.

## What it does

Lets multiple parallel matches coexist in one arena by filtering what each player sees/hears: only teammates and opponents in your match, not others nearby. Filters kill packets, weapons, chat scope.

## Arenas that need it

`2v2pub`, `3v3pub`, `4v4pub`, `4v4league`, `4v4prac`, `4v4caps`.

## Settings surface

`FilterKillPackets=1` and related visibility flags.

## Integration on Infinity

- Hook: network packet routing — filter player-visible events by match membership
- State: player → active-match mapping
- Depends on: match lifecycle (TeamVersusMatch or equivalent); non-trivial integration with `SimEthereal` zone broadcasts
- Design note: SimEthereal broadcasts per-zone; per-match filtering may require a secondary filter layer or custom visibility rules

## Comments
