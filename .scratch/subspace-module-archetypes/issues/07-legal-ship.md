# LegalShip enforcer module

Status: ready-for-human
Cross-ref: [GH #93](https://github.com/assofohdz/subspace-infinity/issues/93)
Parent: [../PRD.md](../PRD.md)
Labels: area:modules, mode:ball

Part of the parent. Phase 2 — ship-class enforcement per arena/frequency.

## What it does

Restricts which ship classes players may fly, either arena-wide or per-frequency. Enforces on ship-change attempts.

## Arenas that need it

`pb` — one ship class per frequency (Warbird on freq 1, Javelin on freq 2, Spider on freq 3, Leviathan on freq 4) via `ArenaMask` + per-freq `MaskFreq*` settings.

## Settings surface

`[LegalShip]` — `ArenaMask` (8-bit mask: bit per ship class), `MaskFreq1..N` (per-frequency overrides). `[Misc] FrequencyShipTypes=1` triggers the per-freq model.

## Integration on Infinity

- Hook: ship-select / ship-change requests
- Reject: illegal ship picks; force legal default
- Depends on: player frequency (`FrequencySystem`), ship selection path

## Comments
