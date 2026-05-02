# Settings Pipeline — Slice Queue

Lean-kanban work queue paired with [`settings-pipeline.md`](settings-pipeline.md).

- **Pipeline tracker** = *state* (what's wired vs not, gate-by-gate).
- **This file** = *queue* (what to work on next, grouped end-to-end by feature).

A slice is one coherent feature finished end-to-end. Finish every row
in the active slice (every row's Complete = ✅ in the pipeline tracker,
including the Test column) before starting the next.

## How to use

- **WIP = 1.** Exactly one slice is ⏳ at any time.
- **Slice 0 is a precondition.** Until the spawn-projection test
  harness exists, no row's Test column can honestly flip to ✅ — so
  every later slice would land half-done.
- **State changes are paired:** when a row in the pipeline tracker
  flips Complete = ✅, update the owning slice here in the same edit.
  When you start a slice, flip it to ⏳.
- **Re-rank freely.** This is a starter ordering, not a contract.
  Operator priorities trump the queue.
- **Polish bag is not a queue.** Pick from it ad-hoc between slices.

## State markers

- ⏳ — in progress (exactly one slice at a time)
- 🔲 — queued
- ✅ — done (every row in the slice is Complete = ✅ in pipeline tracker)
- ⏸ — paused / blocked (note why next to the marker)

---

## Slice 0 — Test harness (precondition)

🔲 Build the programmatic spawn-projection test harness.

- Without this, the Test column can never honestly flip to ✅, and
  every later slice lands as ⚠️ instead of ✅.
- DoD: programmatic test can spawn a ship of a given type in a given
  arena and assert template-projected components carry the expected
  values. Same harness should extend to projectile spawn and prize
  pickup mutations.

## Slices 1–5 — Finish what we ship

Five tiny slices that complete appliers we already ship but whose
tunables are unwired. Pick within this group by which mechanic feels
worst today.

### Slice 1 — Repel feel
🔲 `[Repel]` RepelSpeed, RepelTime, RepelDistance.
Applier `RepelPrizeApplier` already ✅; this slice adds the loader,
`RepelConfig`, and consumer wiring in `ConsumableSystem`.

### Slice 2 — Rocket feel
🔲 `[Rocket]` RocketThrust, RocketSpeed; per-ship `RocketTime`.
Applier `RocketPrizeApplier` already ✅.

### Slice 3 — Brick feel
🔲 `[Brick]` BrickTime, BrickSpan.
Applier `BrickPrizeApplier` already ✅.

### Slice 4 — Decoy feel
🔲 `[Misc]` DecoyAliveTime.
Applier `DecoyPrizeApplier` already ✅; route DecoyAliveTime through
typed loader → `Decay` per the TTL rule.

### Slice 5 — Warp feel
🔲 `[Misc]` WarpPointDelay, WarpRadiusLimit.
Applier `WarpPrizeApplier` already ✅.

## Slice 6 — Status family infrastructure (biggest unlock)

🔲 Per-ship `CloakStatus`/`StealthStatus`/`XRadarStatus`/`AntiWarpStatus`
+ `*Energy` drain rates; `[PrizeWeight]` Cloak/Stealth/XRadar/AntiWarp/
MultiFire applier completion; loader + Config + applier + consumer
wiring.

ECS components already exist (`Cloak`, `CloakStatus`, `CloakEnergy`,
etc., per the Component column audit). One coherent infra build flips
five stub appliers from ❌ to ✅. Biggest single-slice leverage in
the doc.

If the slice is too big, natural sub-batches: (Cloak+Stealth)
together, then (XRadar+AntiWarp), then (MultiFire). They all share
the same toggle-wiring infrastructure.

## Slices 7–9 — Coherent mid-effort features

### Slice 7 — Spawn-point selection
🔲 `[Spawn]` 12 keys (4 teams × X/Y/Radius). Replaces hardcoded
`centerOfArena` in `WarpSystem`.

### Slice 8 — Prize spawning loop
🔲 `[Prize]` PrizeFactor, PrizeDelay, MinimumVirtual, UpgradeVirtual,
PrizeMinExist, DeathPrizeTime, PrizeNegativeFactor, PrizeHideCount.
Controls when/where/how often prize entities spawn.

### Slice 9 — Proximity bomb mechanic
🔲 `[Bomb]` BombExplodeDelay, BombExplodePixels, ProximityDistance,
JitterTime, BombSafety.

## Slice 10 — Projectile speed refactor

🔲 Per-ship `BulletSpeed`, `BombSpeed`. Replaces inline magic numbers
(`addLocal(0,0,50)`, `25`) in `WeaponsSystem`. Touches projectile
creation hot path so independence is lower than slices 1–9, but
visibility is high — these are two of the most-noticed weapon
attributes. Promote ahead of 7–9 if balance pressure dictates.

## Slices 11–15 — Bigger absent features

### Slice 11 — Shrapnel system
🔲 `[Shrapnel]` 4 keys + per-ship ShrapnelMax/ShrapnelRate +
`[PrizeWeight]` Shrap + new `Shrapnel`/`ShrapnelMax` components.

### Slice 12 — Super / Shields prizes
🔲 `[PrizeWeight]` Super + Shields + per-ship SuperTime/ShieldsTime +
new active-state components.

### Slice 13 — Wormhole / gravity
🔲 `[Wormhole]` GravityBombs, SwitchTime + per-ship Gravity/
GravityTopSpeed.

### Slice 14 — Multishot / DoubleBarrel firing modes
🔲 Per-ship MultiFireEnergy, MultiFireDelay, MultiFireAngle,
DoubleBarrel. Components exist (`Multishot`, `DoubleBarrel`); slice
finishes loader → Config → consumer wiring.

### Slice 15 — Turrets
🔲 Per-ship TurretThrustPenalty, TurretSpeedPenalty, TurretLimit.
Whole turret-attachment feature.

## Slice 16 — Tricky stub appliers (catch-all)

🔲 Three remaining stub prize appliers that don't fit the slices
above:

- `BouncingBullets` — needs Bounce ship-toggle component
- `Glue` (Engine Shutdown) — needs EngineShutdown component + timer
  (links `[Prize]` EngineShutdownTime)
- `MultiPrize` — recursive dispatcher (links `[Prize]` MultiPrizeCount)

Could be split if any one grows, but they're small enough to bundle.

---

## Polish bag (ad-hoc, not a queue)

Single rows with no obvious cluster. Pick when you want a small win
between bigger slices. Each item is 1 row, ~1 hour:

- `[Bullet]` ExactDamage
- `[Mine]` TeamMaxMines
- `[Door]` DoorDelay, DoorMode
- `[Radar]` RadarMode, RadarNeutralSize, MapZoomFactor
- `[Spectator]` HideFlags, NoXRadar
- `[Toggle]` AntiWarpPixels
- `[Misc]` BounceFactor, SafetyLimit, NearDeathLevel,
  AntiWarpSettleDelay, TickerDelay, ActivateAppShutdownTime,
  VictoryMusic, TimedGame, SendPositionDelay, SlowFrameCheck,
  SlowFrameRate, AllowSavedShips, FrequencyShift, ExtraPositionData,
  MaxTimerDrift, DisableScreenshot
- `[Owner]` Name
- `[Custom]` SaveStatsTime
- `[Message]` AllowAudioMessages, BongAllowed
- `[Prize]` TakePrizeReliable, S2CTakePrizeReliable
- Per-ship: InitialBounty, AttachBounty, AfterburnerEnergy, Radius,
  DamageFactor, PrizeShareLimit, EmpBomb, SeeBombLevel, SeeMines,
  BurstSpeed, BurstShrapnel
