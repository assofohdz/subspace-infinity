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

🔲 Single focused commit, ~150 LOC removed, no behavior change.
Removes confirmed-dead code paths surfaced during the grilling. Land
this before B0 so the new architecture isn't built on top of dead
infrastructure.

- Delete `ArenaSettings` ECS component
  (`api/src/infinity/es/arena/ArenaSettings.java`) and the
  `setComponent(arena, new ArenaSettings(...))` call at
  `ArenaSystem.java:762`. **Side benefit: drops `org.ini4j` dep from
  api/.**
- Delete `SettingListener` interface
  (`infinity/settings/SettingListener.java`) and the
  `addListener` / `removeListener` / `settingChanged` /
  `fireChangeDiff` machinery in `SettingsSystem.java`. Zero implementors
  ever existed.
- Delete `SettingsSystem.setSetting(...)` method. Zero callers.
- Verify `ArenaSettings` is not registered in
  `GameServer.registerSerializers`; if it is, drop the registration
  line too.

Test: `:infinity:test` green; manual smoke `~loadArena trench`.

## Slice B0 — Move load orchestration into `ConfigRegistrySystem`

🔲 **Foundation, must land before B1.** No new typed adapters yet; B0
just moves the seam.

- Add `ConfigRegistrySystem.load(arenaId, fragmentPaths)` entry point.
  Atomic full-replace per call. Same method serves initial load and
  hot reload.
- Add the centralized dispatch table (`Map<String, FragmentInstaller>`
  keyed by basename) inside `ConfigRegistrySystem`. Initial entries:
  `ships.groovy` (existing typed loader) + the still-INI-routed
  `weapons` + `prize` paths kept as compatibility shims until B1
  migrates them.
- Replace `ArenaSystem.applyWeaponsConfig(...)` and
  `shipLoader.apply(...)` orchestration with `configRegistry.load(arenaId, paths)`
  at the bootstrap path + the file-watcher callbacks
  (`registerFileWatch` `onChange` lambdas at `ArenaSystem.java:725-749`).
  Behavior unchanged — the ships re-projection branch stays intact.
- File watcher polling cadence (`zone.groovy` `scriptPollIntervalNanos`,
  default 5s) is preserved.
- Test: orchestration smoke — `ConfigRegistrySystem.load` with a real
  preset's fragment list produces a `ConfigRegistry` matching the
  pre-migration `applyWeaponsConfig` + `shipLoader.apply` chain.

## Slice B1 — Flatten `WeaponsConfig` + per-file typed adapters

🔲 The big content slice. Sub-tasks:

**Flatten `WeaponsConfig`:**
- Delete `api/src/infinity/config/WeaponsConfig.java`.
- Promote sub-records to direct `ConfigRegistry` slots: `bullet()`,
  `bomb()`, `gravBomb()`, `mine()`, `burst()`, `repel()`, `thor()`.
- Update consumers: `WeaponsSystem` (~6 call sites), `ConsumableSystem`
  (~2 call sites). Pattern: `cfg.weapons().bomb()` → `cfg.bomb()`.

**Per-section adapters:**
- For each typed slot above + `prize`, create a per-section adapter
  implementing `GroovySettingsAdapter<TConfig, TBuilder>`. Each
  produces one typed record. Register in the dispatch table.
- New typed Groovy DSL blocks (canonical Subspace key names):
  `bullet { damageLevel 200; damageUpgrade 100; aliveTime 550 }`,
  `bomb { damageLevel 750; aliveTime 6000; explodeDelay 150; … }`,
  `mine { aliveTime 12000; teamMaxMines 12 }`,
  `burst { damageLevel 515 }`,
  `repel { speed 5000; time 225; distance 512 }`,
  `gravBomb { … }`, `thor { … }`,
  `prize { maxExist 8000; minExist 4000; … }`.
- Unit conversions (cs → ms etc.) at the adapter boundary.

**DEFAULTS rewrite to Subspace canon (per-section, paired with each
adapter):**
- For each `*Config` record, rewrite `DEFAULTS` to match Subspace VIE
  canonical values from
  [`REFERENCE.md`](subspace-ini-reference/REFERENCE.md). Today's
  DEFAULTS reflect legacy Java constants, not canon.
- Per-preset overrides become deltas from canon. e.g.
  `BulletConfig.DEFAULTS.damageLevel == 200` (Subspace canon),
  `trench-04-2026/bullet.groovy` overrides `damageLevel 520`.

**Per-preset migration:**
- For each preset (8 presets), split `misc.groovy`'s weapons-section
  blocks into per-file fragments per Q4 + Q8: `bullet.groovy`,
  `bomb.groovy`, `gravbomb.groovy`, `mine.groovy`, `burst.groovy`,
  `repel.groovy`, `thor.groovy`, `prize.groovy`. Keep `misc.groovy`
  filename for the residual `[Misc]` slot (semantic narrowing).
- Update each preset's `arena.groovy` `includeFragment` list. Drop
  any fragment file whose values match the new DEFAULTS (preset
  doesn't need to author it).

**Tests (rigor tier M, per Q12):**
- Per-adapter unit tests (3 per adapter: full block / partial block /
  invalid value). ~24 tests.
- `ConfigRegistrySystem.load` orchestration tests (~5).
- **Roundtrip tests for `trench-04-2026` and `deva-04-2026`**:
  load preset's full fragment list → assert resulting `ConfigRegistry`
  matches expected values authored in test code.

## Slice B2 — Per-ship typed inventory consolidation (bounded scope)

🔲 Resolves the [dual-pipeline drift](settings-pipeline.md#known-issue-dual-pipeline-drift-on-per-ship-inventory)
for the inventory cluster only. Other unwired per-ship key clusters
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

**B2-Foundation commit scope:**
- New `ShipConfig` slots: `decoys` / `bricks` / `rockets` / `portals`
  (all `@Nullable CountStats`, matching `bursts` / `repels`).
- New `ShipConfigBuilder` methods: `decoys`/`bricks`/`rockets`/`portals`
  taking `start: N, max: M` named-arg maps (matching the existing
  `bursts` / `repels` builder pattern).
- New `ShipSpawnSystem.projectDecoys`/`projectBricks`/`projectRockets`/
  `projectPortals` methods (mirror `projectRepels` shape: skip on null,
  else write `Decoy`+`DecoyMax` etc., reset live pool on respawn).
- `ShipSpawnSystem.project()` calls the 4 new projectors after the
  existing 6 inventory projections.
- ECS components (`Decoy`/`DecoyMax`/`Brick`/`BrickMax`/`Rocket`/`RocketMax`/
  `Portal`/`PortalMax`) **already exist** in `api/src/infinity/es/ship/actions/` —
  no new component classes to author.
- Serializer registration: **not needed** — these are server-only
  components (no `infinity.client.*` references), matching the existing
  Repel/Burst/Thor inventory pattern.
- Behavior: zero behavior change — no preset has `decoys`/`bricks`/`rockets`/
  `portals` blocks in `ships.groovy` yet, so all four fields default to
  `null` (disallow). Manual smoke = "still loads" same as B0.

**B2-Migration commit scope (descoped 2026-05-03):** active arenas only.

The original B2-Migration plan covered all 7 presets that have
`ship-<name>.groovy` files. **In practice the only arenas in
`infinity/zone/arenas/` are `(default)` (→ base), `deva` (→
deva-04-2026), and `trench` (→ trench-04-2026)**. The other 5
SVS-family presets (svs, svs-pb, svs-tce, svs-turf, svs-league) are
scaffolding for content that doesn't ship today; migrating them is
sunk cost. Pragmatic descope:

- **In scope: trench-04-2026, deva-04-2026.** Both are
  `-04-2026` presets currently in active development.
- **Out of scope: base, svs, svs-pb, svs-tce, svs-turf, svs-league.**
  Their `ship-<name>.groovy` and INI-mirror `shipSections` splats
  stay as-is. `(default)` arena (which uses base preset) continues
  to rely on `GroovyShipLoader.DEFAULT_*` Java fallback constants
  (= permissive 10/20 repels, etc.) for inventory. No behavior
  change for `(default)`. Future slice migrates these presets when
  they become active arenas.

**For trench + deva (the migrated presets):**
- For each ship, read the `*Max` and `Initial*` values for the 10
  inventory items (Bombs, Guns, Mines, Repels, Bursts, Thors,
  Decoys, Bricks, Rockets, Portals) from `ship-<name>.groovy`.
- Apply Q6 rule: `*Max == 0` → omit the typed inventory block;
  `*Max > 0` → emit `inventoryName start: Initial*, max: *Max` in
  the typed `ship(Ship.X) { … }` block in `ships.groovy`.
- Mines: per-ship `MaxMines` (count cap) is **not yet represented in
  the typed mines block** (only level + cost + fireDelay are typed).
  Migration treats `MaxMines 0` as "omit mines block"; for `MaxMines
  > 0` keeps the mines block with the per-ship cost+fireDelay and
  default BombLevel range. The count cap stays in `ship-<name>.groovy`
  as deferred until a future slice models it.
- Strip the 18 inventory keys (Initial*+*Max for all 10 items + 6
  fire-cost/delay keys; not MaxMines, not *Upgrade) from
  `ship-<name>.groovy`. Other unwired per-ship keys (Status, speeds,
  etc.) stay in the file for their owning gameplay slice to migrate.

**For all presets (Q6 default change):** `ShipConfigBuilder` per-field
inventory defaults changed from permissive `DEFAULT_*` to `null`. An
explicit `ship(Ship.X) { … }` block now disallows any inventory type
whose block isn't declared (the Q6 contract). The permissive
`DEFAULT_*` constants remain in use only by `FALLBACK` (the snapshot
installed when `ships.groovy` is missing or fails to parse).

**Behavior change**: trench and deva will start playing without the
inventory items their operators wrote `*Max 0` for. Trench warbird,
for example, becomes a gun-only build (no bombs / no repels / no
bursts), matching `ship-warbird.groovy`'s authored intent. Manual
smoke must verify the new loadouts feel right.

**Splat removal (descoped):** with the SVS-family migrations deferred,
the `shipSections` splat usages in `svs-league.groovy` /
`svs-dueling.groovy` / `svs-pb/ships.groovy` stay in place. Q4's
"drop splat" decision still holds for when the SVS migration runs;
it's just deferred along with the rest.

**Tests:**
- B1 tests stay green.
- Extend `ConfigRegistrySystemLoadTest`'s trench assertion to cover
  per-ship inventory: pick warbird (gun-only build) + leviathan
  (heavy bomber with mines+portals). Asserts both opt-ins
  (decoys/thors/portals present at expected counts) and Q6 disallow
  (bombs/repels/bursts/bricks all `null` for warbird), pinning the
  drift fix.

**Out of scope for B2** (deferred to owning gameplay slices):
- Status family (Cloak/Stealth/XRadar/AntiWarp) → S6
- Per-ship projectile speeds (BulletSpeed/BombSpeed/BurstSpeed) → S10
- Shrapnel cluster (ShrapnelMax/ShrapnelRate) → S11
- Super/Shields (SuperTime/ShieldsTime) → S12
- Wormhole/Gravity (Gravity/GravityTopSpeed) → S13
- Multifire/DoubleBarrel → S14
- Turret family → S15
- Polish bag (Radius, DamageFactor, EmpBomb, SeeBombLevel, SeeMines,
  RocketTime, AfterburnerEnergy, InitialBounty, AttachBounty,
  PrizeShareLimit, DisableFastShooting, BombThrust, BombBounceCount,
  BurstShrapnel) → polish-bag slices

**ship-`<name>`.groovy lifecycle:** survives B2 (with deferred clusters
intact); shrinks progressively as each owning gameplay slice migrates
its cluster; final cleanup slice deletes the files when every cluster
is migrated.

## Slice B3 — Typed `prizeWeights` + `deathPrizeWeights`

🔲 New typed Groovy blocks + new `*Config` record:

- `prizeWeights { repel 100; xradar 100; multiFire 255; … }` →
  `PrizeWeightsConfig` record (new — Map<PrizeType, Integer> shape).
- `deathPrizeWeights { … }` → same record type, separate
  `ConfigRegistry` slot.
- Add `prizeWeights()` + `deathPrizeWeights()` slots to `ConfigRegistry`.

**Per-preset migration:**
- Migrate `prizeweights.groovy` (~8 presets) → renamed to
  `prize-weights.groovy` per Q8 (lowercase + hyphens).
- Migrate `section('DPrizeWeight') { … }` blocks in `svs-league.groovy`
  + `svs-dueling.groovy` → typed `deathPrizeWeights { … }` blocks in
  new `death-prize-weights.groovy` files for those presets.
- Consumers: `PrizeSystem.handlePrizeAcquisition` (and
  `PrizeSystem.readArenaWeights` at `PrizeSystem.java:291-316`) read
  via `cfg.prizeWeights()` / `cfg.deathPrizeWeights()` directly.
  `org.ini4j.Ini` reference in PrizeSystem dies here.

**Tests:**
- `PrizeWeightsAdapter` unit tests (full block / partial block / unknown
  prize type).
- Extend the svs-league roundtrip from B2 to assert
  `cfg.deathPrizeWeights()` matches the SVS league config.

## Slice B4 — Retire `SettingsSystem` + INI-mirror pipeline

🔲 **Pure deletion.** Pre-condition: B1–B3 done; no Java code reads
fragment data via `SettingsSystem`; no `.groovy` source uses
`section()` / `shipSection()` / `shipSections()` builders.

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
