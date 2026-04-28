# FlagGamePoints module

Status: ready-for-human
Cross-ref: [GH #88](https://github.com/assofohdz/subspace-infinity/issues/88)
Parent: [../PRD.md](../PRD.md)
Labels: area:modules, mode:flag

Part of the parent. Phase 1 — flag-based scoring.

## What it does

Awards points on flag events (capture, drop, win condition). Integrates with kill rewards via `FlaggerKillMultiplier`.

## Arenas that need it

`warzone`, `jackpot`, `running`, `rabbit`, `(default)`.

## Settings surface

`[Flag]` section: `FlagReward`, `FlagRewardMode`, `FlaggerKillMultiplier`, `FlaggerOnRadar`, `FlaggerGunUpgrade`, `FlaggerBombUpgrade`, `FlaggerFireCostPercent`, `FlaggerDamagePercent`, `FlaggerBombFireDelay`, `FlaggerSpeedAdjustment`, `FlaggerThrustAdjustment`, `CarryFlags`, `FlagMode`, `FlagResetDelay`, `MaxFlags`.

## Integration on Infinity

- Hook: flag-pickup, flag-drop, win-condition detection
- Depends on: KillPoints (for flagger kill multiplier), basic flag entity model
- Variant considerations: `FlagMode` 0 (running/jackpot) vs 1 (warzone win-on-all-held) — distinct win resolution paths

## Comments
