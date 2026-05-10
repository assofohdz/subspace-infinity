# Player-Count Scaling

When designing a spawn cadence, balance threshold, target-count knob, or
any other gameplay quantity, ask whether it should scale with active
player count. Many mechanics that look like flat constants (prize density,
AI spawn rate, regen batch, drop chance) feel correct at one playerload
and wrong at another.

## How to apply

- **Default-static is fine when justified.** Per-team spawns, fixed-position
  decorations, server-debug knobs — these are reasonably static.
- **For density / cadence / target-count knobs**, the design checklist is:
  *"what does this look like with 1 player vs 32 players?"* If the answer
  is "off" at one end, the knob should scale.
- **Scaling shape is additive by default**: `effective = base + perPlayer × N`.
  Multiplicative scaling (`base × N`) hits zero at N=0, which is rarely the
  right behaviour for a single-player or empty-arena fallback.
- **Active-player count is per-arena, not global.** Filter the relevant
  EntitySet by `ArenaId` before counting. Spawners without an `ArenaId`
  (legacy paths, test modules) typically collapse to no-scaling so behaviour
  stays predictable.
- **Surface the question even when you decide static.** A `// scaling
  considered: static OK because <reason>` comment is worth more than a
  silent constant.

## Why

Player-count scaling is a recurring tuning knob in Subspace canon
(`PrizeFactor`, `UpgradeVirtual`) and in any multiplayer arena game with
varying playerload. Forgetting to consider it tends to produce mechanics
that feel either deserted (flat-N too high) or oppressive (flat-N too low)
in the long tail of arena populations.

## Reference

- Slice 8d (Spawner scaling) is the canonical example —
  [`SpawnerSpec`](../../api/src/main/java/infinity/config/SpawnerSpec.java)'s
  `countPerPlayer` and `radiusPerPlayer` apply additive scaling per active
  ship in the spawner's arena. See
  [`PrizeSystem.update`](../../infinity-server/src/main/java/infinity/systems/PrizeSystem.java)
  for the consumer + `countPlayersInArena` helper.
- [`config-pattern.md`](./config-pattern.md) — template (`*Config`) vs
  instance (component) split. Per-player scaling fields live on the
  template; the spawn system projects per-tick effective values rather
  than mutating components.
