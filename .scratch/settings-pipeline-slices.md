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

### Slice 4 — Decoy feel (plumbing only)
✅ Plumbing-only landed: `[Misc] DecoyAliveTime` arena-global wired
end-to-end. Typed `decoy.groovy` adapter (`DecoyAdapter`, cs×10→ms) →
`DecoyConfig` → `ConfigRegistry.decoy()` slot.

Place path: `ConsumableSystem.actOut PLACEDECOY` decrements `Decoy`,
calls `GameEntities.createDecoy` which composes a marker entity
(`Parent(ship) + Decay(DecoyAliveTime ms) + Meta`). The canonical
Decay reaper deletes the marker at deadline — no separate system
needed.

Active arenas only (trench + deva) — values migrated 1:1 from each
preset's pre-migration `misc.groovy` (trench: 10000 cs = 100000 ms;
deva: 4500 cs = 45000 ms). SVS-family deferred. Tests:
`DecoyFactoryTest` pins the marker-entity projection contract;
`ConfigRegistrySystemLoadTest` extended to assert trench's `[Misc]
DecoyAliveTime` parses as `100000 ms`.

Follow-up (own slice): "decoy as radar fake" — adds the canonical
Subspace decoy mechanic (a phantom ship on enemy radar that mimics
the placer's heading). Crosses into client-side radar rendering, so
kept separate from this server-side migration slice. Also a
client-side input binding for `PLACEDECOY` (no key bound today;
server-side seam is canonical and ready).

### Slice 5 — Portal feel (plumbing only)
✅ Plumbing-only landed: `[Misc] WarpPointDelay` arena-global wired
end-to-end. Typed `portal.groovy` adapter (`PortalAdapter`, cs×10→ms) →
`PortalConfig` → `ConfigRegistry.portal()` slot. The dispatch table
crossed `Map.of`'s 10-entry ceiling; converted to `Map.ofEntries`.

Place path: `ConsumableSystem.actOut PLACEPORTAL` decrements `Portal`,
calls `GameEntities.createPortal` which composes a marker entity
(`Parent(ship) + Decay(WarpPointDelay ms) + Meta`). The canonical
Decay reaper deletes the marker at deadline — no separate system
needed.

Active arenas only (trench + deva) — values migrated 1:1 from each
preset's pre-migration `misc.groovy` (trench: 24000 cs = 240000 ms;
deva: 12000 cs = 120000 ms). Tests: `PortalFactoryTest` pins the
marker-entity projection contract; `ConfigRegistrySystemLoadTest`
extended to assert trench's `[Misc] WarpPointDelay` parses as
`240000 ms`.

**Scope correction during slicing:** initially planned to also wire
`WarpRadiusLimit` here based on a misread of its meaning. REFERENCE.md
clarifies it's a Spawn-mechanic — "Random spawn distance limit from
arena center (1024=anywhere)" — so it belongs with Slice 7 (Spawn-point
selection), not Portal. `WarpRadiusLimit` retained in trench/deva
`misc.groovy` pending Slice 7 migration.

Follow-up (own slice): "warp to placed portal" — adds the canonical
Subspace mechanic (ship within radius of its own placed portal can
teleport to it). That follow-up is also where `WarpRadiusLimit` may
finally be consumed if Subspace canon ties them together — TBD.

## Slice 6 — Status family infrastructure (biggest unlock; split into 6a/6b/6c)

### Slice 6a — Cloak + Stealth (with shared Status/Energy infrastructure)
✅ Foundation landed: per-ship `CloakStatus`/`StealthStatus` +
`CloakEnergy`/`StealthEnergy` wired end-to-end.

- New `StatusStats(status, energyDrainPer1000Cs)` record (shared by
  6b's XRadar/AntiWarp).
- `ShipConfig.cloak` / `ShipConfig.stealth` nullable fields.
- `GroovyShipLoader` DSL: `cloak status: <0..2>, energy: <0..32000>` +
  `stealth status: …, energy: …` blocks.
- `ShipSpawnSystem.projectCloak` / `projectStealth` — always set
  `*Status`; set `*Energy` when status >= 1; seed toggle component
  (`Cloak`/`Stealth`) on `resetLivePool` per status (off at 1, on at 2).
- Applier rewrites: `CloakPrizeApplier` + `StealthPrizeApplier` from
  ❌ stub → ✅ canonical tri-state (no-op on status 0; toggle on at >=1).
- New `StatusDrainSystem` (parallels `RocketBuffSystem` shape) — watches
  Cloak/Stealth EntitySets, drains Health via `EnergySystem.damage(-d)`
  at Subspace canonical rate `energy × tpf / 10` per tick.
- Registered `StatusDrainSystem` in `GameServer`.
- Bug fix: 4 `*Status` components missing `implements EntityComponent`
  (Cloak/Stealth/XRadar/Antiwarp) — fixed all four since they're a
  shared latent bug, even though 6a only wires Cloak+Stealth.
- Active arenas only (trench + deva): per-ship `cloak`/`stealth` blocks
  authored in `ships.groovy` for ships with status > 0; legacy
  `CloakStatus`/`StealthStatus`/`CloakEnergy`/`StealthEnergy` keys
  stripped from `ship-<name>.groovy` for trench + deva.
- Tests: `CloakPrizeApplierTest` (tri-state behaviour);
  `StatusDrainSystemTest` (per-tick drain math + edge cases);
  `ConfigRegistrySystemLoadTest` extended (LEVIATHAN stealth=2,
  WEASEL cloak=2+stealth=2, WARBIRD both omitted).

**Behaviour change on active arenas:** trench/weasel and trench/leviathan
now drain energy while cloaked/stealthed at the canonical Subspace
rate (was: silently no-op since the appliers threw `UnsupportedOperationException`
and the drain system didn't exist). Operators can dial the drain or
set status=0 in `ships.groovy` if the rates feel wrong.

Follow-up (own slice): client-side input binding to toggle
cloak/stealth off (no key bound today; in Subspace this is the player's
explicit cloak/stealth-off action). Until that lands, status==2 ships
have permanent toggle-on (matches Subspace pre-input behaviour).

### Slice 6b — XRadar + AntiWarp
✅ Landed. Reused `StatusStats` + `StatusDrainSystem` infrastructure
from 6a. Added:
- `ShipConfig.xradar` / `ShipConfig.antiwarp` slots + DSL.
- `projectXRadar` / `projectAntiwarp` in `ShipSpawnSystem`.
- Two more EntitySets + drain loops in `StatusDrainSystem`.
- `XRadarPrizeApplier` + `AntiWarpPrizeApplier` from ❌ stub → ✅
  canonical (tri-state respecting).

Bug fix: `AntiwarpEnergy` component had a `boolean enabled` field —
clearly a copy-paste error from the toggle component, since the
Javadoc and the parallel `CloakEnergy`/`StealthEnergy`/`XRadarEnergy`
all hold an int drain rate. Fixed to int + `getEnergy()` accessor;
no other callers existed.

Active arenas only (trench + deva): per-ship `xradar` / `antiwarp`
blocks authored in `ships.groovy` for ships with status > 0 (trench
SPIDER/LEVIATHAN/TERRIER/WEASEL/LANCASTER/SHARK; all 8 deva ships
get xradar; no deva ship has antiwarp). Legacy XRadar/AntiWarp keys
stripped from 16 `ship-<name>.groovy` files. Tests: `XRadarPrizeApplierTest`
(parallels CloakPrizeApplierTest); `ConfigRegistrySystemLoadTest`
extended (SPIDER xradar=2+antiwarp=1, LANCASTER xradar=1+no antiwarp,
WARBIRD both omitted).

**Behaviour change:** trench/spider now has start-active xradar that
drains energy at 200/1000 per cs (= 20/sec); trench/lancaster + shark
+ leviathan + terrier can acquire xradar via prize. Most deva ships
have xradar (status 1 or 2) but with energy=0 → drain math is 0 → no
actual energy cost (matches operator intent in legacy fragments).

### Slice 6c — MultiFire (applier flip only)
✅ Landed. `MultiFirePrizeApplier` from ❌ stub → ✅ canonical: stamps
`Multishot(true)` on the ship unconditionally (no per-ship `*Status`
tri-state for MultiFire per REFERENCE.md "Bullets"). Test:
`MultiFirePrizeApplierTest`.

Tiny slice — the heavy lift (per-ship `MultiFireEnergy` /
`MultiFireDelay` / `MultiFireAngle` plumbing into the firing path) is
deferred to a follow-up "MultiFire firing mode" slice that extends
`WeaponsSystem`. Until that lands, the `Multishot` toggle is set but
unconsumed; gun fire ignores it.

Follow-up (own slice — sits with Slice 14 in the queue):
1. New per-ship MultiFire stats on `ShipConfig` + DSL.
2. Project `MultiFireEnergy` / `MultiFireDelay` / `MultiFireAngle` to
   per-entity components at spawn.
3. `WeaponsSystem` gun-fire path reads `Multishot` toggle + the
   per-ship knobs to fire 3+ bullets at the canonical angle spread.
4. `DoubleBarrel` (separate per-ship 0/1 toggle, no prize) — also
   deferred. `DoubleBarrel` component currently missing
   `implements EntityComponent` (latent bug; fix when wiring it up).

## Slices 7–9 — Coherent mid-effort features

### Slice 7 — Spawn-point selection
✅ `[Spawn]` per-team spawn data wired end-to-end (Subspace authors
4 teams × X/Y/Radius; Infinity's typed shape is a `List<TeamSpawn>`
of any length, looked up via `freq % teams.size()` — generalizes the
canonical "Freq 4 → Team0, Freq 5 → Team1, …" wraparound to N teams).
`SpawnConfig` + `TeamSpawn` records (api/), `SpawnAdapter`,
`ConfigRegistry.spawn()` slot, dispatch entry, and
`ArenaSystem.getArenaSpawn(arenaName, freq)` reading typed
`SpawnConfig` with legacy `ArenaConfig.spawnX/spawnZ` fallback. Two
consumer call sites (`GameSessionHostedService.resolveInitialSpawn`
freq=0 connect-time, `AvatarSystem.requestShipChange` reading
ship's `Frequency`) updated. Active arenas only (trench + deva)
authored `spawn.groovy` with team0 = pre-migration single-spawn
coords (1:1 behaviour preservation); `spawn x, z` directives stripped
from each arena.groovy. Test:
`ConfigRegistrySystemLoadTest` extended with spawn-parse +
freq-wraparound assertions.

`warpRadiusLimit` lives on `SpawnConfig` as an unconsumed slot per
the agreed scope; consumption deferred to the WarpSystem-randomization
follow-up.

Follow-ups (own slices):
- WarpSystem-randomization — wires the unconsumed
  `SpawnConfig.warpRadiusLimit` slot. The WARP-key + Warp-prize
  consumers (`WarpSystem.warpToCenter` callers) currently warp to
  arena centre; canon Subspace warps to a random spot within
  `WarpRadiusLimit`.
- Migrate `(default)` arena + SVS-family presets to typed
  `spawn.groovy`; once every active preset has typed spawn data, a
  cleanup slice deletes the legacy `ArenaConfig.spawnX/spawnZ` fields
  + the `arena.groovy spawn x, z` directive.

## Slice 8 — Prize lifecycle knobs (sub-slices)

The original Slice 8 description bundled 8 disparate `[Prize]` knobs
into one slice. After grilling, that's three different mechanics —
declarative-spawner lifetime randomisation, death-dropped prizes,
negative prizes, and a Subspace-canonical global hidden-prize regen
loop — which violates the slice queue's WIP=1 vertical-slice ethos.
Split into four sub-slices, each independently shippable. Infinity
keeps its declarative `prizeSpawners { ... }` model (strictly more
flexible than the canon hidden-prize loop) and cherry-picks the canon
knobs that map cleanly onto declarative spawners.

### Slice 8a — Prize random lifetime range (`PrizeMinExist`)
✅ `[Prize] PrizeMinExist` paired with already-wired `PrizeMaxExist`
gives each prize a random lifetime in `[PrizeMinExist, PrizeMaxExist]`
(REFERENCE.md `## Prize`). `PrizeConfig.defaultMinDecayMs` field added,
`PrizeAdapter` parses optional `minExist <cs>` (×10→ms) and throws when
`minExist > maxExist`. `PrizeSystem.sampleDecayMs` samples uniformly
in `[min, max]`; `[min, min]` collapse for arenas omitting `minExist`
preserves 1:1 behaviour. Per-spawner explicit `ttlMs` remains a fixed
override (precedence α). Active arenas authored: trench
`minExist 4000` (40s, paired with maxExist 12000=120s), deva
`minExist 2000` (20s, paired with maxExist 5000=50s). Test:
`ConfigRegistrySystemLoadTest` extended.

### Slice 8b — Death-dropped prizes (`DeathPrizeTime`)
✅ `[Prize] DeathPrizeTime` wired end-to-end with the simplest viable
contract (option B from grilling — no threshold, always-drop):
on ship death, spawn 1 weighted prize at the ship's current
`BodyPosition` with `deathPrizeTimeMs` lifetime. Prize-type via the
arena's `[PrizeWeight]` table (same selector default-cadence
spawners use). Plumbed:
- `PrizeConfig.deathPrizeTimeMs` field (default `0L` = disabled),
  `PrizeAdapter` parses optional `deathPrizeTime <cs>` (×10→ms).
- `PrizeSystem.spawnDeathPrize(shipId, deathPosition, timeNs)` —
  no-op when `deathPrizeTimeMs == 0`; selector falls back to
  `globalFallbackSelector` for ships without an `ArenaId`.
- `EnergySystem` death branch (the only seat where
  `Health<=0 → Dead` happens today) inlines a synchronous call to
  `prizeSystem.spawnDeathPrize` filtered by `Player.class +
  BodyPosition.class` so non-ship dying entities don't trigger
  drops.

Active arenas authored: trench + deva each set
`deathPrizeTime 1500` (= 15s, Subspace SVS canon).
Test: `ConfigRegistrySystemLoadTest` extended.

**Out of scope (own follow-up slice):** ship-bounty growth + the
threshold check ("only drop if bounty &gt; N"). Infinity has no
ship-bounty tracking today (no per-ship score component, no
prize-pickup-bumps-bounty hook, no respawn-resets-bounty).
Building it requires a new `ShipBounty(int)` component, migration
of `InitialBounty` to typed `ShipConfig`, prize-pickup wiring, and
respawn-reset wiring — too much surface for one slice. Tracked as
"ship-bounty + scoring" follow-up; once it lands, an "8b-extension"
slice adds the `deathPrizeThreshold` config knob.

**Out of scope (orthogonal to 8b):** the dormant
`infinity.systems.DeathSystem` (unregistered class with `Decay(now,
now)` reap logic + a "doesn't work with how DecaySystem works" TODO
comment) is left alone. It doesn't run today; 8b doesn't activate
it. Whoever fixes the broader respawn flow (Slice TBD) decides
whether to register it, replace it, or delete it.

### Slice 8c — Negative prizes (`PrizeNegativeFactor`)
✅ `[Prize] PrizeNegativeFactor` wired via DUD substitution
(option E from grilling — gentler than canonical inverse stat
degradation): when the 1-in-N roll hits at prize-spawn time, the
selected prize-type is replaced by `Dud` (existing no-op applier)
instead of running an inverse stat-loss effect. Preserves the
canonical *probability* semantics so a future follow-up slice can
swap DUD for proper inverse appliers without re-authoring presets.
Plumbed:
- `PrizeConfig.prizeNegativeFactor` field (default `0` = disabled).
- `PrizeAdapter` parses optional `negativeFactor <int>`.
- `PrizeSystem.maybeRollNegative(prizeType, prizeConfig)` —
  no-ops when `factor <= 0` or selected is already `Dud`; otherwise
  `random.nextInt(factor) == 0` triggers DUD substitution.
- `spawnBounty` + `spawnDeathPrize` both route through the helper.

Active arenas authored: trench + deva each set
`negativeFactor 1000` (Subspace SVS canon, 1-in-1000 odds).
Test: `ConfigRegistrySystemLoadTest` extended.

**Out of scope (own follow-up slice):** the inverse-applier matrix
(stat-downgrade Energy/Recharge/Rotation/Thruster/TopSpeed
appliers, weapon-level demotion, inventory removal). The DUD
substitution preserves the probability knob's semantics so the
follow-up just swaps the dispatch target without touching
`prize.groovy` author surface.

### Slice 8d — Player-scaled spawners (was: hidden-prize regen loop)
✅ Pivoted scope after grilling: instead of wiring the Subspace
canonical `[Prize] PrizeFactor`/`PrizeDelay`/`MinimumVirtual`/
`UpgradeVirtual`/`PrizeHideCount` arena-global keys into `PrizeConfig`,
absorb the *concepts* (player-count scaling, batch regen, hidden mode)
into the per-spawner DSL. Subspace canon stays unwired; divergence is
documented per-key in `SpawnerSpec` Javadoc.

**C1 (rename, no behavior change) — landed `f1f62b2f`.**
- `PrizeSpawnerSpec` → `SpawnerSpec`; `prizeSpawners {}` → `spawners {}`;
  `createWeightedPrizeSpawner` → `createSpawner`; cascade through 4
  prod callsites + 3 module testers + ArenaConfig field rename.

**C2 (player scaling + hidden mode + regen batch) — in progress.**
- `Hidden` empty-marker component (`api/src/infinity/es/`),
  registered in `GameServer.registerSerializers`.
- `Spawner` ECS component gained `countPerPlayer`, `radiusPerPlayer`,
  `regenBatch`, `hidden` (10-arg ctor; getters).
- `SpawnerSpec` record gained the same 4 fields; class Javadoc carries
  the Subspace divergence table.
- `GroovyArenaLoader.SpawnersBlock.spawn` DSL accepts the 4 new fields
  with no-op defaults (`countPerPlayer: 0`, `radiusPerPlayer: 0.0`,
  `regenBatch: 1`, `hidden: false`).
- `GameEntities.createSpawner` (15-arg overload) + `createPrize` (7-arg
  overload accepting `boolean hidden`) — original signatures preserved
  via delegating overloads for ABI stability.
- `ArenaSystem.materializePrizeSpawners` forwards the 4 new fields.
- `PrizeSystem.update` computes per-arena player count via new
  `countPlayersInArena(ArenaId)` helper; effective max =
  `maxCount + countPerPlayer × players`; effective radius =
  `radius + radiusPerPlayer × players`; spawns up to
  `min(regenBatch, deficit)` prizes per interval tick.
- `PrizeSystem.spawnBounty` passes `spawner.isHidden()` to
  `createPrize`.
- `ModelContainer.addObject`/`updateObject` early-return when target
  entity carries `Hidden` — tracked in container map but no spatial
  bind. Server-side collision / pickup unaffected.
- New project rule `.claude/rules/player-scaling.md` ("consider
  player scaling when designing spawn / balance knobs"; soft phrasing,
  additive default) + CLAUDE.md path-scope entry.
- Tests: `SpawnerProjectionTest` (factory-level: createSpawner stamps
  new Spawner fields; createPrize(hidden=true) stamps Hidden;
  legacy 6-arg createPrize stays visible). `GroovyArenaLoaderTest`
  extended for new DSL fields (omitted = no-op defaults; explicit =
  pass-through). Math + per-arena scoping extracted from
  `PrizeSystem.update` into static helpers
  (`computeEffectiveMaxCount`, `computeEffectiveRadius`,
  `computeRegenAmount`, `countPlayersInArena`) and unit-tested in
  `PrizeSystemScalingTest` (16 cases — additive formulas, regen-batch
  capping, cross-arena scoping, null-arena fallback,
  ArenaId-entity-id-equality irrelevance).

**Deferred (own follow-up slices):**
- Wall-aware spawn sampling — affects all spawners, not just hidden.
  Today's `getSpawnLocation` is wall-blind (pre-existing bug, not a
  regression).
- `BodyContainer` Hidden filter — only ModelContainer covered today
  since prizes are static (SpawnPosition+ShapeInfo). Dynamic-Hidden
  consumers (ship cloaking) need to extend the filter.
- Subspace canonical `[Prize]` arena-global keys themselves —
  `PrizeFactor`/`PrizeDelay`/`MinimumVirtual`/`UpgradeVirtual`/
  `PrizeHideCount` stay unwired. Operators porting a Subspace map
  hand-author one large `spawners {}` entry covering the arena.
- Full `PrizeSystem.update` integration test (real spawner entity,
  contact loop, time stepping) — heavy fixture; sits with the broader
  spawn-projection harness backlog. The math seams it would cover are
  now unit-tested via the static helpers above; what's missing is
  end-to-end behaviour over multiple ticks.

**C3 (optional arena migration) — deferred.** trench/deva keep
behaviour-preserving defaults (no scaling, visible, regen=1). Operator
opt-in by editing arena.groovy.

## Slice 9 — Proximity bomb mechanic (split into 9a/9b/9c-BombSafety/9c-JitterTime)

The original Slice 9 description bundled 5 disparate `[Bomb]` keys —
splash radius, proximity arming, fuse delay, fire safety, screen jitter
— that are independent mechanics. After grilling, split into four
sub-slices, each independently shippable:

- **9a — Splash damage path.** `BombExplodePixels` wired with per-level
  scaling (Subspace canon: L1×1, L2×2, L3×3, L4×4); bomb damage path
  switches from "single-target point damage" to "AoE damage in a radius
  around the contact point." Adds an arena-wide `friendlyFire` int knob
  (0=off, 1=bomb splash only, 2=all weapons) consumed by every damage
  path so the AoE has a sensible default.
- **9b — Proximity arming + fuse.** `ProximityDistance` (per-level
  +1 additive radius) + `BombExplodeDelay` (fuse cs→ms) wired via a new
  `ProximityFuseSystem` per-tick scan — chosen over the originally
  sketched per-bomb sensor-ghost + `Delay`-component path because Moss
  has no static-sensor-following-a-dynamic-body pattern; the
  per-tick scan is O(bombs × ships) which is negligible at typical
  arena sizes. Bombs no longer detonate on direct enemy body contact
  — they arm at proximity radius, fuse for the configured delay,
  then explode.
- **9c-BombSafety — fire-time safety gate.** `BombSafety` (boolean)
  AND'd into `WeaponsSystem.canAttackBomb`; rejects bomb fire when an
  enemy sits inside the firing ship's effective proximity-arm radius
  (per-level scaled). Reuses 9b's radius math + FF gate; auto-no-ops
  when proximity is disabled. Server-only.
- **9c-JitterTime — bomb-hit screen jitter.** `JitterTime` (cs→ms) —
  server stamps a `Jitter` deadline on the victim, client camera-shakes
  for the duration. Crosses into client work; deferred until a
  client-side jitter / camera-shake AppState exists.

### Slice 9a — Splash damage path
✅ Landed.

- `ArenaConfig.friendlyFire` (int 0/1/2) added — `arena.groovy` directive
  validated 0–2 in `GroovyArenaLoader`. Default 0 (off) preserves the
  existing trench/deva PVP behaviour where the `CategoryFilter` allowed
  same-team contacts but no FF check existed; the new gate now skips
  same-team damage by default.
- `BombConfig.explodeRadius` (tiles / world units, L1 base) added; typed
  `bomb.groovy` `explodeRadius` directive in `BombAdapter`. Default 5.0
  tiles (= SVS canonical `BombExplodePixels 80` divided by the 16 px/tile
  rate). Diverges from Subspace's pixel unit because pixels aren't a
  meaningful unit at the simulation layer (documented on `BombConfig`).
  The `aliveTime` DSL setter was also renamed to `aliveTimeCs` for
  unit-explicit naming at the call site.
- New `infinity.es.SplashDamage(radiusWorldUnits)` component (server-only,
  no serializer). Stamped on bomb projectiles at fire time by
  `WeaponsSystem.createProjectileBomb` using
  `splashRadiusForLevel(explodeRadius, bombLevel)` — per-level multiplier
  with no unit conversion (input is already in tiles).
- `WeaponsSystem.newContact` rewritten to switch between the splash AoE
  path (when `SplashDamage` is present) and the direct-hit path
  (everything else). Both paths run through `shouldDamageVictim` — a
  pure-function tri-state FF gate (mode 0/1/2) that respects same-team.
  World-hit detonations also trigger the splash scan when the bomb has
  `SplashDamage`, so wall-strikes still AoE.
- Active arenas: trench + deva keep the safe `friendlyFire 0` default;
  testarena opts into mode 1 (bomb splash only) so smoke testers can
  exercise the AoE path. trench / deva / testconf `bomb.groovy` all
  author the canon `explodePixels 80`.
- Tests: `BombFactoryTest` (api-side `createBomb` decay/parent contract);
  `WeaponsSystemSplashTest` (per-level scaling + tri-state FF gate);
  `GroovyArenaLoaderTest` extended (default 0, valid 0/1/2,
  out-of-range rejected, DSL parse); `ConfigRegistrySystemLoadTest`
  extended (trench's `[Bomb] BombExplodePixels = 80`).

**Behaviour change on active arenas:** bombs now do AoE damage in a
5-tile (L1) → 20-tile (L4) radius around the contact point instead of
single-target damage at the bomb's body. Same-team is gated (no
trench/deva same-team damage) until operator opts into FF mode 1 or 2
in `arena.groovy`.

**Out of scope (own follow-up slices):**
- Distance attenuation — splash currently applies full damage at every
  point inside the radius; canon Subspace attenuates linearly with
  distance from the explosion. Polish-bag.
- Mines, gravity bombs splash — only regular bombs get `SplashDamage`
  today. Mines / gravbombs retain direct-hit semantics through the same
  FF gate. Decide per-mechanic in their own slice (mines: own slice;
  gravbombs: 9b candidate since they share bomb code paths).
- `CategoryFilter` same-team rejection — FF=0 today swallows damage at
  the contact-handler seam, which means projectiles still detonate
  visually on a teammate. Subspace canon would have the contact never
  fire at all. A future slice can extend `CategoryFilter` if the visual
  pop is undesirable.

### Slice 9b — Proximity arming + fuse
✅ Landed.

- `BombConfig.proximityDistance` (tiles, base L1) + `BombConfig.explodeDelayMs`
  (cs×10→ms) added; `BombAdapter` exposes `proximityDistance` +
  `explodeDelayCs` setters. Defaults `0/0` = disabled (preserves 9a
  direct-contact behaviour for arenas that haven't opted in).
- New api/-side server-only components: `ProximityFuse(radiusWorldUnits,
  fuseMs)` and `ProximityArmed(armedAtSimNanos)`. Neither crosses the
  wire (per `components.md`); no serializer registration.
- `WeaponsSystem.createProjectileBomb` projects per-level radius via new
  `proximityRadiusForLevel(base, level) = base + (level-1)` helper —
  REFERENCE.md ## Bomb canon "Each level adds 1." Distinct from
  `splashRadiusForLevel`'s multiplicative scaling (×level).
- Detonation logic extracted into `WeaponsSystem.detonateProjectile(id,
  damage, point, directVictim?, nowSimNanos)` — shared by the slice 9a
  contact path AND the new proximity-fuse expiry path. Wall-hits bypass
  the proximity gate (canonical: bombs explode on wall touch regardless
  of arm state). Direct ship-contact on an unarmed proximity bomb is
  swallowed (`contact.disable()`); arming flows through the per-tick
  scan instead.
- New server system `infinity.systems.ship.ProximityFuseSystem` —
  per-tick arming scan + fuse-elapsed detonation. Pure-function helpers
  `shouldArmOn(ownerFreq, victimFreq)` (canonical: friendlies never
  arm, regardless of arena `friendlyFire` mode) and
  `fuseElapsed(armedAt, now, fuseMs)` (boundary-inclusive `≥` check)
  extracted for unit testing. Registered after `StatusDrainSystem` in
  `GameServer`.
- Active arenas authored: trench + deva + testconf bomb.groovy each
  set `proximityDistance 3, explodeDelayCs 10` (canon SVS — 3 tiles
  base → L1=3 / L2=4 / L3=5 / L4=6 tile arming radius; 100 ms fuse).
- Tests: `ProximityFuseSystemTest` (arm-gate tri-state + fuse
  arithmetic); `WeaponsSystemSplashTest` extended with
  `proximityRadiusForLevel*` cases; `ConfigRegistrySystemLoadTest`
  extended (trench parses `ProximityDistance=3,
  BombExplodeDelay=10cs (=100ms)`).

**Behaviour change on active arenas:** proximity-fuse bombs no longer
detonate on direct enemy body contact — they arm at 3-tile (L1) →
6-tile (L4) radius around an enemy, fuse for 100 ms, then explode at
the bomb's position. Wall hits still detonate immediately. Same-team
ships glide past unarmed bombs. Operators can dial back via lower
`proximityDistance` / shorter `explodeDelayCs`, or disable entirely by
setting either to 0.

**Deviation from REFERENCE.md (documented on `BombConfig` +
`ProximityFuseSystem` Javadoc):** canon says the bomb explodes
"immediate if ship leaves trigger area" once armed. Infinity runs the
fuse to completion regardless. Edge case visible only on near-miss
fly-throughs of fast ships; tracked as polish-bag.

**Integration test gap (per existing memory note):** the per-tick scan
+ body-position lookup + cross-system detonation call is not exercised
by any automated test — only the static helpers + parse path are. Same
gap as splash damage's contact-path integration; deferred to the
spawn-projection harness backlog.

### Slice 9c-BombSafety — fire-time safety gate
✅ Landed.

- `BombConfig.bombSafety` (boolean) added; `BombAdapter` exposes the
  typed DSL setter `bombSafety(boolean)`. Default `false` so arenas not
  authoring the key keep slice 9b's "fire allowed regardless of nearby
  enemies" behaviour.
- `WeaponsSystem.canAttackBomb` extended with a `bombSafetyClear` step
  AND'd after the existing cooldown + energy gates. Auto-no-ops when
  `BombConfig.bombSafety == false` OR `BombConfig.proximityDistance == 0`
  (the bomb wouldn't proximity-arm anyway, so "inside the arming
  radius" has no meaning).
- Effective radius reuses slice 9b's `proximityRadiusForLevel(base, level)`
  (additive per-level scaling: L1 = base, L4 = base+3) — captures "would
  my bomb arm immediately on a hugging enemy?" semantics.
- FF gate reuses `ProximityFuseSystem.shouldArmOn` (cross-package call —
  same `infinity.systems.ship.*` package): friendlies hugging the firer
  don't block fire (canonical Subspace — same-team ships never arm a
  proximity bomb, so they don't trip the safety either).
- Scan source reuses WeaponsSystem's existing `energyEntities`
  (`EntitySet(Health.class)` already lifecycle-managed + applyChanges'd
  per tick).
- Pure-function helper `WeaponsSystem.victimBlocksBombFire(ownerFreq,
  victimFreq, ownerPos, victimPos, radius)` extracted (mirrors
  `shouldDamageVictim` + `proximityRadiusForLevel` extraction style) —
  unit-tested with 7 cases (enemy inside / outside / boundary,
  friendly inside, zero radius, null-freq tri-state, 3D radius).
- Active arenas authored: trench + deva + testconf `bomb.groovy` add
  `bombSafety true` (= SVS canon `BombSafety=1`).
- Tests: `WeaponsSystemSplashTest.victimBlocksBombFire_*` (7 new cases);
  `ConfigRegistrySystemLoadTest` extended to assert trench parses
  `bombSafety true`.

**Behaviour change on active arenas:** trench + deva ships can no
longer fire a bomb when an enemy sits inside their effective
proximity-arm radius (trench L1 = 3 tiles; L4 = 6 tiles). Silent
rejection — no fire, no cooldown, no energy spent. Operator can dial
back via `bombSafety false` in `bomb.groovy` if it feels too defensive.

**Deviation from canon:** none — Subspace VIE specifies BombSafety as a
binary self-protect; Infinity matches semantics exactly. The boolean
storage (vs Subspace's int 0/1) is a consumer-side clarity choice
documented on `BombConfig`.

**Out of scope (own follow-up slice — perf review):** the per-fire
`canAttackBomb` scan currently walks every `Health`-bearing entity in
the arena's EntitySet for *every* fire attempt. At typical arena sizes
this is fine (a few dozen ships, fire rate ~1-2/sec), but worth a perf
review pass alongside the per-tick `ProximityFuseSystem` scan — both
paths could benefit from arena-level pre-filtering on `ArenaId` (today
the scan is global because EntitySet membership is global), and from
caching per-arena player counts so other systems (`PrizeSystem.update`,
`StatusDrainSystem`) don't recompute it every tick. Sits with the
spawn-projection / ECS broad-phase backlog.

**Integration test gap (per existing memory note):** the EntitySet
walk + body-position lookup + cross-tick fire-attempt timing is not
exercised by any automated test — only the per-victim pure helper is.
Same gap as splash damage's contact-path integration; deferred to the
spawn-projection harness backlog.

### Slice 9c-JitterTime — bomb-hit screen jitter
🔲 `[Bomb] JitterTime` (cs→ms) — server stamps a `Jitter`
deadline component on the victim of a bomb hit (both direct + splash
paths in `WeaponsSystem.detonateProjectile`); client reads `Jitter`
on the local avatar's id to camera-shake (ChaseCamera offset
perturbation, or `FilterPostProcessor`-based screen shake). Greenfield
on the client side — no `BombHitState`, no camera-shake AppState
exists today. Land alongside the broader client visual-feedback queue.

## Slice O1 — Operator-editable conf as external assets

🔲 Today every Groovy config file (`zone.groovy`, per-arena `arena.groovy`,
`conf/<preset>/*.groovy`) is bundled inside the `infinity-fat.jar`.
Operators tuning a live server can't edit these without a full rebuild
+ redeploy.

**Goal:** zone + arena + per-preset conf files become **external assets**
loaded from a config directory next to the jar, hot-reloadable while
the server runs. Engine-tier config (`engine.groovy`, see Slice 10)
stays inside the jar because it's developer-tuned, not operator-tuned.

**Out of scope for this slice (deferred to its own follow-up):**
- Authentication / signing of external configs (operator-supplied
  Groovy is a security surface).
- Persistence of `~set <slot> <field> <value>` mutations to the
  external config files. Slice B5 introduces ephemeral `~set`;
  persisting them is a separate concern.

**Scope sketch (subject to grilling when the slice is picked):**
- Resource loader gains an "external first, classpath fallback"
  ordering for any path under `conf/`.
- New `--config-dir <path>` CLI flag (default: `./conf` next to jar).
- `ConfigRegistrySystem`'s file watcher already handles in-jar
  hot-reload — extend to watch the external directory.
- Update player-build scripts / installer to ship a `conf/` directory
  alongside the jar with the active arena presets.

**Sequence position:** independent of gameplay queue. Land when ops
lifecycle pressure dictates (e.g., tournament servers wanting same-day
balance tweaks without a rebuild).

## Slice R1 — Rename `Gun*` → `Bullet*` to match Subspace canon

✅ Landed. Projectile-side weapon family renamed `Gun*` → `Bullet*` to
match Subspace canon (`[Ship] BulletSpeed`/`BulletFireDelay`/
`BulletFireEnergy`). Components (`Bullet{Cost,FireDelay,CurrentLevel,
MaxLevel,Speed}`), record `BulletStats`, enum `BulletLevel`, client
visuals `BulletVisuals`, `WeaponsSystem.BULLET` constant, methods
(`canAttackBullet`/`setCoolDownBullet`/`deductCostOfAttackBullet`/
`createProjectileBullet`/`projectBullets`), DSL block (`bullets`),
`ShipConfig.bullets` accessor, audio constants (`FIRE_BULLETS_L*`),
3 arena presets (trench/deva/testconf), all tests, and pipeline
trackers updated in lockstep. Prize-side stays unchanged
(`GunPrizeApplier`, `PrizeTypes.GUN`, `Gun` prize-type identifier =
"Gun Upgrade") — the rename is the projectile-side family only.

## Slice P2 — Physics implementation audit

⏳ Open-ended audit / cleanup pass on Infinity's physics layer. Surfaced
during Slice 10 grilling: there's persistent unit confusion across the
existing physics integrations, no documented conversion boundary, and
multiple "magic numbers" that exist only because nobody's written down
what unit anything is in.

**Concrete symptoms (audit material, not pre-decided answers):**

1. **Unit mismatch — ship vs projectile speed.** Trench warbird
   authors `speed initial: 2000, max: 6000` (Subspace pixel-flavored
   units), consumed by `PlayerDriver` and turned into something the
   physics integrator accepts. Same trench warbird's bullets fire at
   `addLocal(0, 0, 50)` (jME world-units / sec). Two different unit
   conventions in the same ship — no documented translation.
2. **Inline magic numbers throughout `WeaponsSystem`.** Bullet `50`,
   bomb `25`, thor `50` (in `ConsumableSystem`). Each is a tuning
   knob in disguise — Slice 10 promotes bullet/bomb/burst to typed
   `*Speed` fields, but the audit should sweep the rest:
   `BombThrust`, `BombBounceCount`, `AfterburnerEnergy`, `Radius`,
   `Gravity`, `GravityTopSpeed`, etc. — multiple have authored values
   in `ship-X.groovy` that are stale-and-never-read.
3. **`dragFactor`, `turnResponsiveness`, `bounceRestitution` are
   typed and consumed**, but their unit semantics aren't documented
   (they live in `ShipConfig` Javadoc as "0..1" or "1/sec" but
   converting between Subspace `BounceFactor` and Infinity's
   `bounceRestitution` is empirical).
4. **`SpeedMax`, `ThrustMax`, etc. carry Subspace-canonical numeric
   ranges** (4-digit) but the integrator probably scales them
   internally somewhere. That scale factor isn't centralized — it's
   wherever PlayerDriver does its thing. Possibly a hard-coded
   constant; possibly part of the Moss physics integration.
5. **Mines force `velocity = (0, 0, 0)` in `getAttackInfo`** as a
   special-case, instead of the spawn pipeline projecting an
   appropriate component value. Same shape of problem as bullet/bomb
   speed before Slice 10 — could be folded into the same Pattern 4
   solution.

**Audit questions to answer (not pre-decided):**

- Is there a single "Subspace-units → jME world-units" conversion
  factor we can centralize, or is the relationship non-linear and
  per-mechanic?
- Should `*Speed` / `*Thrust` / etc. be authored in jME world-units
  uniformly (Slice 10 / 9a precedent — own the divergence), or in
  canon Subspace units with an adapter-side conversion (cleaner for
  operators porting from SVS, but introduces a "magic constant" in
  the loader)?
- Are there magic numbers in `Moss` / `mphys` integration we can
  replace with typed config?
- Does `ShipConfig` need a unit-aware wrapper type (e.g. `WorldUnit`
  vs `SubspaceUnit` value-objects) to surface unit-mismatch bugs at
  compile time? Probably overkill for the size of the codebase, but
  worth considering once.

**Scope:** investigation + documentation first; concrete refactors
fall out of what the audit surfaces. Likely produces 1-2 follow-up
slices (e.g. "S2 — Centralize Subspace-to-Infinity speed conversion"
or "S3 — Promote `BombThrust` / `Radius` / etc. to typed `ShipConfig`
fields with documented units").

**Non-goals:**
- Not a Moss-replacement slice. Infinity's physics integration is
  Moss/mphys; switching frameworks is a vastly larger conversation.
- Not a gameplay-tuning slice. Audit informs future tuning; doesn't
  do the tuning itself.

**Sequence position:** post-everything. Land after the gameplay queue
catches up so the audit has the full picture of what's wired vs not.
Likely after Slice 16 + B4 + B5.

## Slice U1 — Arena outlines on radar

✅ Landed. New `ArenaFootprint(Vec3d[] vertices)` component in
`infinity.es.arena.*` carries a closed polygon — server stamps it on
each arena entity at load time alongside `ArenaMap` (rectangle from
`min`/`max`). Client `RadarState` opens an `EntityContainer<Node>` on
`ArenaFootprint` and builds two child geometries per footprint: a
fan-triangulated interior fill at `arenaTintColor` and a
`Mesh.Mode.Lines` outline at `arenaOutlineColor`, layered behind blips
via Y-offset
(blips at Y=0, outlines Y=-1, fills Y=-2). Viewport clear color flipped
from `backgroundColor` to `voidTintColor` (darker green, what
non-arena pixels show). Smoke-verified in trench: trench / deva /
testarena footprints all visible simultaneously.

Naming: initial `RadarShape` proposal collided with existing
`RadarShapeInfo` (blip-shape names). Renamed to `ArenaFootprint` under
`infinity.es.arena.*` per user feedback that "Region" is a Subspace
canon term (eLVL REGN chunks; see `infinity.map.Region`). Component is
generic on closed polygons — future entities (wormholes, safe zones,
eLVL regions per `infinity.map.Region`) can stamp themselves the same
way via `ArenaFootprint.rectangle(min, max)` or the raw `Vec3d[]
vertices` constructor.

Current-vs-neighbor styling deferred to backlog
(`refactor-backlog/BACKLOG.md` "Radar `ArenaFootprint` — current-vs-
neighbor styling") — picked up if uniform styling feels noisy in
extended play.

## Slice P1 — ECS broad-phase + per-arena scan perf review

🔲 Cross-cutting perf pass after slices 8d / 9a / 9b / 9c-BombSafety
landed multiple per-tick / per-fire scans over global EntitySets.
Each scan is fine alone; the concern is that they all walk the same
underlying `Health.class` set without arena-pre-filtering, and a few
recompute per-arena player counts on every tick.

**Specific questions to answer:**

1. **Do we need to scan arena vs ship every tick?**
   `PrizeSystem.update` calls `countPlayersInArena(ArenaId)` per
   spawner per tick (Slice 8d). `StatusDrainSystem` walks every
   Cloak/Stealth/XRadar/AntiWarp toggle holder per tick (6a/6b).
   These each filter the same global EntitySets by `ArenaId` from
   scratch — could share a per-arena cache.

2. **Do we need to scan proxbomb vs ship every tick?**
   `ProximityFuseSystem.tryArm` walks every `Health` ship for every
   in-flight proximity bomb each tick (Slice 9b). At 8 bombs × 32
   ships × 60 tick = ~15k checks/sec just for arming. Plus
   `WeaponsSystem.applySplashDamage` (per-detonation, finite),
   `WeaponsSystem.bombSafetyClear` (per-fire-attempt, ~1-2/sec/ship).
   Could pre-filter by arena via shared infrastructure.

3. **Should EntitySet membership filter on `ArenaId` at the source?**
   Today the EntitySets are global. Per-arena EntitySets would
   eliminate the filter loop in every consumer. Trade-off: more
   EntitySets to lifecycle-manage; per-arena cardinality might be
   high enough that the win is marginal.

4. **What per-arena snapshots are worth caching cross-system?**
   Player count, ship-IDs-in-arena, bomb-IDs-in-arena, FF mode (the
   last is already cached but recomputed via ED lookup per-call).
   A per-arena `ArenaScanCache` updated once per tick by a single
   producer system, read by all consumers, would centralize the cost.

**Scope:** plumbing-only. No gameplay knob changes. Goal: cut the
per-tick worst-case scan count by ≥1 order of magnitude under typical
arena load (8 bombs in flight, 4 active per-arena ship spawners,
8 Status holders, 32 ships) without changing any observable behavior.

**Acceptance:**
- Existing tests stay green.
- A new micro-benchmark / perf test (TBD harness) shows the per-tick
  scan-count delta for a representative scenario.
- Each of the four scans documents its post-pass cost in Javadoc.

**Sequence position:** independent — pick when there's no urgent
gameplay slice in flight, ideally after another 1-2 gameplay slices
add their scans (so the perf pass has the full picture). Could promote
ahead of Slice 11 if frame-time becomes an in-arena concern.

## Slice 10 — Projectile speed refactor
✅ Landed.

Per-ship `BulletSpeed` / `BombSpeed` / `BurstSpeed` wired end-to-end,
authored in Subspace canon velocity units, translated to jME world
units at fire time via a new engine-tier conversion + cap.

**Engine-tier config introduction (new pattern — first of its kind):**
- `infinity/src/main/resources/engine.groovy` — game-wide developer-tunable
  knobs, packaged in jar. Distinct from per-arena/per-zone configs
  (operator-tunable, intended to externalize per Slice O1).
- `EngineConfig(double subspaceVelocityScale, double maxProjectileSpeedJme)`
  record + `DEFAULTS = (0.01, 100.0)`.
- `GroovyEngineLoader` (typed `engine { … }` DSL, mirrors `GroovyZoneLoader`
  shape) + `EngineConfigSystem` (loads once at startup, exposes
  `EngineConfig get()`). Registered in `GameServer` alongside
  `ConfigRegistrySystem`.

**Per-ship type changes:**
- `BombStats(start, max, cost, fireDelayCs, **speed**)` — 5th field.
- `GunStats(start, max, cost, fireDelayCs, **speed**)` — 5th field.
- New `BurstStats(start, max, speed)` — replaces `CountStats` for
  bursts only (decoys/bricks/portals stay on `CountStats`).
- `ShipConfig.bursts` field type: `CountStats` → `BurstStats`.
- New components in `api/src/infinity/es/ship/weapons/`:
  `GunSpeed(int)`, `BombSpeed(int)`, `BurstSpeed(int)`. Server-only
  (no client-side reference); skip serializer registration per
  `components.md`.

**DSL extensions:**
- `bombs start: …, max: …, cost: …, fireDelay: …, **speed: 2000**`
- `guns  start: …, max: …, cost: …, fireDelay: …, **speed: 2000**`
- `bursts start: …, max: …, **speed: 3000**`

**Spawn projection:** `ShipSpawnSystem.projectBombs` /
`projectGuns` / `projectBursts` extended to stamp the new `*Speed`
components. Bursts also project `BurstMax` (already wired).

**WeaponsSystem refactor:**
- `getAttackInfo` reads per-ship `GunSpeed` / `BombSpeed` /
  `BurstSpeed` component, applies `effectiveProjectileSpeed(rawValue,
  scale, maxJme) = clamp(rawValue × scale, ±maxJme)`.
- Pure-function helper `effectiveProjectileSpeed` extracted (mirrors
  `proximityRadiusForLevel` + `splashRadiusForLevel` style).
  Negative-input pathway preserved for Slice 10b backward firing.
- **Latent burst-broken bug fixed:** previously `getAttackInfo`'s
  switch had no `case BURST` and threw `AssertionError` on every
  burst fire. Both switches (velocity + position offset) now handle
  BURST; burst projectiles inherit the bullet-radius position offset.

**Per-arena migrations (Q6 lift-1:1):**
- Trench/deva: per-ship speed values lifted directly from each
  `ship-<name>.groovy` into the typed `ships.groovy` DSL. Stale
  `BulletSpeed` / `BombSpeed` / `BurstSpeed` keys stripped from all
  16 `ship-<name>.groovy` files via `sed`.
- Trench javelin's legacy `BulletSpeed 64636` (likely SVS int16
  overflow encoding for backward firing — Slice 10b territory) lifts
  as raw `64636`; engine cap clamps the translated `646.36` to
  `100.0` jME forward (intentionally wrong-direction-but-bounded
  until 10b lands; comment in trench `ships.groovy`).
- testconf: lifted from `svsSettings.cfg` SVS canon (uniform
  `bulletSpeed: 2000, bombSpeed: 2000, burstSpeed: 3000`).

**Behaviour change on active arenas:**
- Trench warbird `BulletSpeed 5000 → 50` jME (matches today).
- Trench warbird `BombSpeed 5000 → 50` jME (was hardcoded `25`,
  now `50` — 2× faster bombs). Operator-noticeable gameplay shift.
- Most other ships' speeds shift slightly (jav 2250→22, lev 4000→40,
  etc.) per the 1:1 lift; previous behaviour ignored the ship-X
  values entirely so this is the first time per-ship variation is
  visible.

**Tests:**
- `WeaponsSystemSplashTest.effectiveProjectileSpeed_*` (6 cases:
  scale math, cap clamping, zero, low, negative+sign, alternate scale).
- `ConfigRegistrySystemLoadTest`: trench warbird parses
  `BulletSpeed = 5000` through `ShipConfig.guns().speed()`.
- New `EngineConfigSystemTest` (3 cases: packaged groovy parses to
  defaults, missing path falls back, pre-init returns DEFAULTS).
- `ShipSpawnSystemTest`: `GunSpeed` / `BombSpeed` / `BurstSpeed`
  projection asserted alongside existing inventory components.
  Constructor calls updated for the new `BombStats` / `GunStats` /
  `BurstStats` arity.

**Out of scope (own follow-up slice — `BurstShrapnel` per-ship):**
canon Subspace authors `BurstShrapnel` per-ship in `[Ship]` sections,
but Infinity currently consumes via arena-global
`BurstFireConfig.projectileCount` (= per-arena, not per-ship). This
is a known divergence; aligning canon would mean adding `count` to
`BurstStats` and reading from the per-ship component instead of the
arena-global config. Not bundled here.

**Out of scope (deferred — slice 10b):** backward-firing semantics
for legacy SVS int16-overflow values. Trench javelin currently
fires forward+capped instead of backward.

**Out of scope (deferred — slice R1):** rename `Gun*` → `Bullet*`
across the component family (`GunSpeed` → `BulletSpeed`,
`GunCost` → `BulletFireEnergy`, etc.) for canon alignment.

## Slice 10b — Backward-firing projectiles

✅ Landed. Typed DSL `bulletSpeed` / `bombSpeed` accept signed Java
ints — negative fires backward (signed scalar; magnitude capped at
`maxProjectileSpeedJme`, sign preserved by `effectiveProjectileSpeed`
since slice 10). Trench javelin migrated from `speed: 64636` to
`speed: -900` (manual int16 → signed lift); `GunStats` / `BombStats`
Javadoc carries the contract; `BurstStats.speed` documented as
magnitude-only (radial-equidistant fan geometry has no "backward"
direction). No loader change (`intArg` already passes negatives
through); no engine-config change (cap already sign-preserving). Pinned
by `ConfigRegistrySystemLoadTest` (`javelin.guns().speed() == -900`) +
the slice-10-shipped `effectiveProjectileSpeed_negativePreservesSignAndClamps`
helper test. Smoke-verified in trench: javelin bullets fire opposite
ship facing.

Trench shark `BombSpeed 1` not migrated — confirmed slow-forward
intent (literal value, doesn't fit int16-overflow shape).

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
