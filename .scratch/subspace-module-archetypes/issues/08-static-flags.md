# StaticFlags module (incl. PersistentTurfOwners)

Status: ready-for-human
Cross-ref: [GH #94](https://github.com/assofohdz/subspace-infinity/issues/94)
Parent: [../PRD.md](../PRD.md)
Labels: area:modules, mode:flag

Part of the parent. Phase 2 — static territorial flags.

## What it does

Treats flags as **map fixtures** (not carryable): players don't pick them up, they touch them to claim ownership for their team. Ownership can persist across sessions (`PersistentTurfOwners`). Rewards accrue via periodic ticks, not discrete captures.

## Arenas that need it

`turf`, `tce`.

## Settings surface

`[Flag] CarryFlags=0` (signals static mode), `PersistentTurfOwners=1`. Periodic reward config in `[Periodic]` — `RewardDelay`, `RewardMinimumPlayers`, `RewardPoints`, `SplitPoints`. Kill-flag bonuses: `[Kill] KillPointsPerFlag`, `KillPointsMinimumBounty`.

## Integration on Infinity

- Hook: player-touches-flag → claim for team
- State: per-flag team ownership component (persists between sessions if `PersistentTurfOwners=1`)
- Tick: periodic reward loop scored against current ownership map
- Depends on: KillPoints (for per-flag kill bonuses)

## Comments
