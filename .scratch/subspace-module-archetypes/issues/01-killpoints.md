# KillPoints module

Status: ready-for-human
Cross-ref: [GH #87](https://github.com/assofohdz/subspace-infinity/issues/87)
Parent: [../PRD.md](../PRD.md)
Labels: area:modules

Part of the parent. Phase 1 — foundational scoring.

## What it does

Awards points to the killer on every player kill, reads bounty-derived formulas from `[Kill]` settings.

## Arenas that need it

Every scoring arena: `(default)`, `warzone`, `jackpot`, `rabbit`, `king`, `speed`, `running`, `turf`, `tce`, and all `3v3pub`/`4v4pub/league/prac/caps` matchmaking arenas.

## Settings surface

`[Kill]` section: `MaxBonus`, `MaxPenalty`, `RewardBase`, `BountyIncreaseForKill`, `FixedKillReward`, `BountyRewardPercent`, `JackpotBountyPercent`, `KillPointsPerFlag`, `KillPointsMinimumBounty`, `DebtKills`, `NoRewardKillDelay`.

## Integration on Infinity

- Hook: player-kill event (weapon damage → death)
- Write: score component / stats update on killer entity
- Read: above settings via `SettingsSystem.getInt(arenaName, "Kill", key, default)`
- Listen: `SettingListener` for live-tuning

No specific blockers; depends on existing damage/death plumbing.

## Comments
