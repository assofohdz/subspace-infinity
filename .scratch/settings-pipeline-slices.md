# Settings Pipeline — Slice Queue

Lean-kanban work queue paired with [`settings-pipeline.md`](settings-pipeline.md).

- **Pipeline tracker** = *state* (what's wired vs not, gate-by-gate).
- **This file** = *queue* (what to work on next, grouped end-to-end by feature).

Two parallel workstreams:

- **Gameplay slices (1–16)** — finish unwired tunables for already-shipping
  features. Each slice = one feature wired end-to-end (loader → Config →
  applier → consumer → test).
- **Architecture migration (Pre-B0 → B0 → B1 → B2 → B3 → B4 → B5)** —
  replace the INI-mirror DSL (`section('X') { Key Value }` → `Ini` →
  `SettingsSystem` key/string reads) with a typed pipeline (per-file
  typed Groovy DSL → per-section adapter → typed record →
  `ConfigRegistrySystem` slot). End state: `SettingsSystem` deleted,
  `Ini` path gone, every key flows through typed records.

A slice is one coherent change finished end-to-end. Finish every row
in the active slice (every row's Complete = ✅ in the pipeline tracker,
including the Test column) before starting the next.

## How to use

- **WIP = 1 per workstream.** One gameplay slice + one architecture
  slice may be ⏳ at the same time. Within a workstream, finish before
  starting the next.
- **Slice 0 is a precondition** for gameplay slices. Until the
  spawn-projection test harness exists, no row's Test column can
  honestly flip to ✅ — so every later gameplay slice would land
  half-done.
- **Pre-B0 cleanup is a precondition** for B0. Three confirmed-dead
  code paths (`SettingListener` machinery, `setSetting`, `ArenaSettings`
  ECS component) get deleted in a focused commit before B0 starts.
- **B0 is a precondition** for B1–B4. Load orchestration must move
  into `ConfigRegistrySystem` before per-section migrations have an
  entry point to register against.
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

✅ Programmatic spawn-projection test harness, three pillars in place:

- **Ship spawn** — `ShipSpawnSystemTest` boots a minimal
  `GameSystemManager` + `DefaultEntityData` + `ConfigRegistrySystem` +
  `ShipSpawnSystem` and asserts a fully-specified `ShipConfig` projects
  onto the per-entity components.
- **Prize pickup** — `RepelPrizeApplierTest` exercises a representative
  Count-family `PrizeApplier` directly against `DefaultEntityData`;
  Status-family appliers (Slice 6) will extend the fixture with an
  `EnergySystem` stub.
- **Projectile spawn** — `BulletFactoryTest` constructs a real
  `PhysicsSpace` from a single-cell `Grid` and asserts
  `GameEntities.createBullet` projects `BulletConfig.decayMs()` into a
  `Decay` deadline plus the per-level damage formula on `BulletConfig`.

Later slices' Test column can now honestly flip to ✅.

## Slices 1–5 — Finish what we ship

Five tiny slices that complete appliers we already ship but whose
tunables are unwired. Pick within this group by which mechanic feels
worst today.

### Slice 1 — Repel feel
✅ `[Repel]` RepelSpeed, RepelTime, RepelDistance wired end-to-end.
`RepelConfig` record (api/), `GroovyWeaponsLoader.loadRepel` (cs→ms unit
conversion on `RepelTime`), `WeaponsConfig.repel` field, server-side
firing path through `GameSessionHostedService` → `ConsumableSystem`
REPEL branch (canAct / setCoolDown / deductCost / act / getActionPosition
/ createSound), `GameEntities.createRepel` extended to take `decayMs`,
spawned effect entity carries `Decay` + `RepelSpeed` + `RepelDistance`.
`CoreViewConstants.REPELDECAY = 400` placeholder removed. Test:
`RepelFactoryTest`.

Follow-up (own slice): no system reads `RepelSpeed` / `RepelDistance`
yet to apply impulse to nearby bodies — Repel still fires a visual/audio
effect only. The plumbing is canonical and ready; the impulse system is
the next gate.

### Slice 2 — Rocket feel
✅ Full-loop landed: `[Rocket] RocketThrust`/`RocketSpeed` arena-global +
per-ship `RocketTime` wired end-to-end. Typed `rocket.groovy` adapter
(`RocketAdapter`) → `RocketConfig` → `ConfigRegistry.rocket()` slot.
Per-ship `rockets start: N, max: M, activeTimeCs: T` extended via new
`RocketStats` record (replaces `CountStats` for rockets) →
`ShipSpawnSystem.projectRockets` projects `Rocket`/`RocketMax`/`RocketTime`
onto the ship.

Fire path: `ConsumableSystem.actOut FIREROCKET` decrements `Rocket`,
snapshots ship's `Thrust`/`Speed`, swaps to `RocketConfig` overrides,
creates a buff entity (`Parent`+`RocketBuff`+`RocketSnapshot`+`Decay`)
via `GameEntities.createRocketBuff`. New `RocketBuffSystem` watches the
buff EntitySet — on add it stamps `RocketActive` on the ship + caches
the snapshot keyed by buff id (Zay-ES `getRemovedEntities` doesn't
preserve component values); on the canonical Decay reaper deleting the
buff, the cached snapshot reverts the ship's `Thrust`/`Speed` and
strips `RocketActive`.

Active arenas only (trench + deva); SVS-family deferred. Tests:
`ConfigRegistrySystemLoadTest` pins arena-global + per-ship parsing;
`RocketBuffActivationTest` pins the swap/revert lifecycle through the
buff entity's add+remove edges.

Follow-up (own slice): wire a client-side input binding for FIREROCKET
(no key bound today; server-side seam is canonical and ready). Also a
fire-SFX entity once a rocket-fire audio asset lands.

### Slice 3 — Brick feel (plumbing only)
✅ Plumbing-only landed: `[Brick] BrickTime`/`BrickSpan` arena-global
wired end-to-end. Typed `brick.groovy` adapter (`BrickAdapter`) →
`BrickConfig` → `ConfigRegistry.brick()` slot.

Fire path: `ConsumableSystem.actOut PLACEBRICK` decrements `Brick`,
calls `GameEntities.createBrick` which composes a marker entity
(`Parent(ship) + BrickSpan(N) + Decay(BrickTime ms)`). The canonical
Decay reaper deletes the marker at deadline — no separate system
needed.

Active arenas only (trench + deva) — both author `BrickSpan 7` (canon
base value) since neither pre-migration `misc.groovy` set it. SVS-
family deferred. Tests: `BrickFactoryTest` pins the marker-entity
projection contract; `ConfigRegistrySystemLoadTest` extended to assert
trench's `[Brick]` parses as `(spanTiles=7, timeMs=10000)`.

Follow-up (own slice): "make bricks solid" — adds `ShapeNames.BRICK`,
per-tile wall geometry from `BrickSpan`, brick-vs-ship/bullet/bomb
collision filter, and a client visual. Crosses into client work, so
kept separate from this server-side migration slice.

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

---

# Architecture migration (option b) — typed-record `ConfigRegistrySystem`

Replaces the INI-mirror DSL pipeline (`section('X') { Key Value }` →
`Ini`-backed `SettingsSystem` → `Groovy*Loader` re-pulls keys by
string) with a typed pipeline (per-file typed Groovy DSL → per-section
adapter → typed record → `ConfigRegistry` slot). Decided 2026-05-03;
finalised through a /grill-me session whose decisions are recorded in
the [target architecture diagram](settings-pipeline.md#target-architecture-post-migration).

**End state:**

- `SettingsSystem` is deleted entirely (the typed `ConfigRegistrySystem`
  absorbs its load + reload responsibilities).
- Each `ConfigRegistry` slot maps 1:1 to a typed `.groovy` fragment file
  via a centralized dispatch table inside `ConfigRegistrySystem`.
- `WeaponsConfig` grouping struct is deleted; sub-records become direct
  `ConfigRegistry` slots.
- `*Config.DEFAULTS` constants are rewritten to Subspace canon; presets
  only author files for sections they override.
- `org.ini4j` dep is dropped from both api/ and infinity/ if no other
  consumer remains.
- No `SettingListener` API — YAGNI; consumers re-read on every call,
  hot tuning works via "swap ships" or wait-for-poll.

**Sequencing:** Pre-B0 cleanup → B0 → B1 → B2 → B3 → B4 is a strict
dependency chain. B5 can ship anytime after B4.

## Pre-B0 — Dead-code cleanup commit (not a slice)

✅ Landed in `4ca49d7` (`chore(settings): delete dead listener +
setSetting + ArenaSettings`). 5 files changed, ~150 LOC removed.
Deleted `ArenaSettings` ECS component + `SettingListener` interface +
`SettingsSystem.setSetting`; dropped `org.ini4j` dep from api/.

## Slice B0 — Move load orchestration into `ConfigRegistrySystem`

✅ Landed in `db9b189` (`feat(settings): centralize load orchestration`).
3 files changed, ~186 insertions / 48 deletions. Added
`ConfigRegistrySystem.load(arenaId, arenaConfig)` as single entry point
for initial load + hot reload. Replaced ArenaSystem's `applyWeaponsConfig`
+ `shipLoader.apply` orchestration with one `configRegistry.load` call.
Test: `ConfigRegistrySystemLoadTest` pins the new entry point against
trench preset.

Dispatch table introduction deferred to B1 (B0 had nothing to put in
it; trench/ships.groovy was handled separately as a non-`fragmentIncludes`
path via the existing `shipsScript` arena.groovy field).

## Slice B1 — Flatten `WeaponsConfig` + per-file typed adapters

✅ Landed across 7 commits: `5056137` (B1a flatten),
`b7fc24d`/`2dcc18c`/`337b090`/`89f922a`/`ad886cc`/`c2558034`
(B1-Bullet/Bomb/Mine/Burst/Repel/Prize). Plus `7f302a00` for a Phase 1
typed-fragment-skip fix caught by smoke.

End state:
- `WeaponsConfig` deleted; 7 sub-records (bullet/bomb/gravBomb/mine/
  burst/repel/thor) promoted to direct `ConfigRegistry` slots.
- 6 typed adapters in `infinity.settings.*Adapter` (Bullet, Bomb,
  Mine, Burst, Repel, Prize) — one per Subspace section.
- `ConfigRegistrySystem.DISPATCH` populated with 6 typed-fragment
  installers; `load()` Phase 3a (legacy compat shim) is empty.
- `GroovyWeaponsLoader` deleted entirely (158 LOC gone).
- 31 new per-preset typed `.groovy` fragment files; 8 `misc.groovy`
  files cut down to non-weapons sections.
- `*Config.DEFAULTS` constants kept at their pre-B1 legacy values
  (canon-rewrite deferred — they're already canon-ish for the
  presets we run).

**Departure from grilled-through plan:** the canon-rewrite was a B1
deliverable per Q7 but in practice the existing DEFAULTS for
weapons/prize records already matched the SVS preset values closely,
so no rewrite was needed beyond what landed organically per-section.

## Slice B2 — Per-ship typed inventory consolidation (bounded scope)

✅ Resolved the dual-pipeline drift for the inventory cluster (active
arenas only — trench/deva). Other unwired per-ship key clusters
(Status family, projectile speeds, multifire, turret, etc.) wait for
their owning gameplay slices (S6, S10, S11, S12, S13, S14, S15) to
migrate them alongside their consumer wiring.

**Decisions locked in 2026-05-03 grilling session** — recorded inline
because they reframe the original B2 description above:

| # | Decision |
|---|---|
| Q1 | `ship-<name>.groovy` is authoritative — migration *fixes* the dual-pipeline drift. Trench warbird's `RepelMax 0` becomes effective (was silently overridden by `DEFAULT_REPELS = 10/20`). Behavior change visible in-game; that's the bug fix. |
| Q2 | Include decoy/brick/rocket/portal *activation* — currently dead (no `*Max` component is projected anywhere; 4 prize appliers silently no-op). B2 wires their projection so they actually work. |
| Q3 | Bounded scope: inventory + currently-typed keys + the 4 newly-activated types. **Other unwired per-ship keys stay in `ship-<name>.groovy`** until their owning gameplay slice. ship-`<name>`.groovy files survive B2 (smaller; holding deferred clusters). |
| Q4 | **Drop the typed `shipSections` splat.** Native Groovy `each` handles the same use cases without bespoke DSL surface. Rewrite svs-league/svs-dueling/svs-pb splat usages as `each` blocks. |
| Q5 | **2-commit slicing**: B2-Foundation (add 4 fields/builder methods/projections; no preset changes) + B2-Migration (per-preset value migration + splat removal in one commit). |
| Q6 | `*Max 0` → `null` slot in ShipConfig (= disallow per Subspace canon). Migration omits the inventory block when source value is 0. `*Max > 0` → typed `inventoryName start: N, max: M` block. |
| Q7 | I read+write per preset directly; native Groovy `each` for svs-pb (all-ships shared) and svs-league (shared baseline + per-ship overrides). |

✅ Landed across 2 commits: `b7a7a062` (B2-Foundation: 4 new
`ShipConfig` slots + builder methods + `ShipSpawnSystem.project*`
methods for decoy/brick/rocket/portal) + `cb276025` (B2-Migration:
trench + deva ships.groovy migrated; 16 ship-`<name>`.groovy files
stripped of inventory keys; `ShipConfigBuilder` defaults flipped to
null per Q6).

**Behavior change in trench/deva:** per Q1 the `ship-<name>.groovy`
values became authoritative — trench warbird is now a gun-only build
(was getting silent 10 repels / 5 bursts / BOMB_4 bombs from defaults),
matching operator intent.

**ship-`<name>`.groovy lifecycle:** survives B2 with deferred clusters
intact (Status family, projectile speeds, multifire, turret, etc.).
Shrinks progressively as each owning gameplay slice (S6, S10–S15,
polish bag) migrates its cluster; a final cleanup slice deletes the
files when every cluster is migrated.

**Out of scope for B2** (deferred to owning gameplay slices):
- Status family (Cloak/Stealth/XRadar/AntiWarp) → S6
- Per-ship projectile speeds (BulletSpeed/BombSpeed/BurstSpeed) → S10
- Shrapnel cluster (ShrapnelMax/ShrapnelRate) → S11
- Super/Shields (SuperTime/ShieldsTime) → S12
- Wormhole/Gravity (Gravity/GravityTopSpeed) → S13
- Multifire/DoubleBarrel → S14
- Turret family → S15
- Polish bag: Radius, DamageFactor, EmpBomb, SeeBombLevel, SeeMines,
  AfterburnerEnergy, InitialBounty, AttachBounty, PrizeShareLimit,
  DisableFastShooting, BombThrust, BombBounceCount, BurstShrapnel,
  MaxMines (count cap)
- SVS-family per-ship migrations + typed `shipSections` splat removal
  (deferred until those presets become active arenas).

## Slice B3 — Typed `prizeWeights` (descoped: prizeWeights only)

✅ Landed in `f649d8be` (`feat(settings): typed prizeWeights via
PrizeWeightsAdapter`). 16 files changed. Same active-arenas-only
descope as B2: only base/deva/trench got migrated.

- `PrizeWeightsConfig` record (api/, Map<String, Integer> weights,
  DEFAULTS = empty map) + `PrizeWeightsAdapter` (typed
  `prizeWeights { Name N; … }` DSL using Groovy `invokeMethod` dispatch).
- `ConfigRegistry.prizeWeights()` slot + dispatch entry for
  `prize-weights.groovy`.
- 3 typed `prize-weights.groovy` authored (base, deva, trench);
  3 old `prizeweights.groovy` deleted.
- `PrizeSystem.readArenaWeights` reads from typed slot;
  `SettingsSystem` field + last `org.ini4j.Ini` reference dropped from
  PrizeSystem.

**Out of scope (deferred):** `deathPrizeWeights` slot + DSL + fragment
files (only svs-league/svs-dueling author `[DPrizeWeight]`, neither is
an arena); SVS-family `prizeweights.groovy` migrations.

## Slice B4 — Retire `SettingsSystem` + INI-mirror pipeline

🔲 **Pure deletion. Far horizon.** Pre-condition: every fragment
cluster typed; no Java code reads fragment data via `SettingsSystem`;
no `.groovy` source uses `section()` / `shipSection()` /
`shipSections()` builders.

After the active-arenas-only descope of B2/B3, `SettingsSystem` is
still required because:
- `misc.groovy` polish-bag sections (Brick, Rocket, Door, Radar,
  Shrapnel, Toggle, Wormhole, Misc, Custom, Owner, Message,
  Spectator) still flow through Phase 1 INI compat. They migrate via
  their owning gameplay slices (S2, S3, S5, S11, S13 + polish bag).
- `ship-<name>.groovy` files in trench/deva still hold deferred
  per-ship clusters (Status, Speeds, Multifire, Turret, etc.).
  They migrate via S6, S10, S11, S12, S13, S14, S15 + polish bag.
- SVS-family presets keep INI-mirror state until a future slice
  activates them as arenas.

B4 lands when those queues are empty. **Realistically: many slices
out, after the gameplay queue catches up.**

- Delete `infinity/src/main/java/infinity/systems/SettingsSystem.java`
  (~352 lines).
- Delete the `section` / `shipSection` / `shipSections` Groovy
  builders from `GroovyFragmentLoader`. Keep `include` directive —
  composition isn't INI-shaped, still useful for shared baselines like
  `svs-league.groovy`.
- Delete the legacy weapons/prize routing kept since B0 from
  `ConfigRegistrySystem`.
- Delete `Ini`-related code throughout: `SettingsSystem` parameter
  from `Groovy*Loader` signatures, `getIni`, `loadFragments`,
  `reloadFragments`, etc.
- Delete `org.ini4j` dep from `infinity/build.gradle` if nothing else
  uses it.
- Delete `GroovyFragmentLoaderTest` cases that exercise deleted
  builders. Adapter tests from B1–B3 carry the coverage.
- Update any docs referencing `SettingsSystem` or `Ini` to point at
  `ConfigRegistrySystem` instead.

Tests: `:infinity:test` green; manual smoke `~loadArena trench` +
`~loadArena svs-league`; edit `bomb.groovy` and confirm hot-reload
fires within ~5s without errors.

## Slice B5 — Typed `~set` admin command (optional follow-on)

🔲 Self-contained ~50 LOC slice. No dependency on B-slices beyond B4
having shipped.

- Reflection-based field set on Java records via `RecordComponent[]`
  introspection.
- `ChatHostedPoster` pattern: `~set <slot> <field> <value>` (e.g.,
  `~set bomb damageLevel 1000`).
- Validation chain enforced by the typed architecture:
  - Slot must exist in `ConfigRegistrySystem`'s dispatch table.
  - Field must exist as a `RecordComponent` on the slot's record type.
  - Value must coerce to the component's declared type
    (int/double/boolean/enum).
  - Canonical constructor invariants enforced by record construction.
- Mutation is **ephemeral** — lost on server restart. Operators
  wanting persistence edit the `.groovy` file (the file watcher picks
  it up).
- Optional sibling: `~reload <arena>` to force-reload without waiting
  for the 5s poll. ~10 LOC.

## What changed from the original B0–B4 (recorded for posterity)

The B0–B4 plan above replaces an earlier draft that was based on a
mistaken read of the existing code. The grilling session surfaced five
facts the original plan missed: `ConfigRegistrySystem` already exists
as a typed-record store; `SettingListener` has zero implementors;
`SettingsSystem.setSetting` has zero callers; `ArenaSettings` ECS
component is set but never read; `GroovyArenaLoader` + `GroovyShipLoader`
are already typed loaders. The deltas:

| Original | Updated | Why |
|---|---|---|
| B0 = build typed-store API + listener reshape | B0 = move load orchestration into `ConfigRegistrySystem` | Typed store already exists (Q1); no listener API (Q3) |
| B1 = typed DSL for arena-global weapons (mega-adapter implied) | B1 = flatten `WeaponsConfig` + per-file adapters + Subspace-canon DEFAULTS rewrite | Per-file adapters chosen over mega (Q4); flatten chosen (Q5); DEFAULTS rewrite paired per-section (Q7) |
| B2 = per-ship typed inventory consolidation | (same scope) + typed `shipSections` splat impl | svs-league needs splat (Q12) |
| B3 = typed `[PrizeWeight]` table | (same scope) + `[DPrizeWeight]` for svs-league | Surfaced during diagram drafting |
| B4 = retire INI-mirror pipeline | (same scope, simpler — most cleanup is incidental to B1–B3) | Most "retirement" happens incidentally as sections migrate; B4 is just the final sweep |
| (didn't exist) | Pre-B0 dead-code cleanup commit | Surfaced during grilling — three confirmed-dead code paths |
| (didn't exist) | B5 typed `~set` follow-on | Surfaced during admin-commands grilling (Q11) |
