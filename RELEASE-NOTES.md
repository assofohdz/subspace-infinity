# Release notes

Latest release only. Earlier history lives in git tags + commit log
(`git log v<previous>..v<this>`).

## v1.0.15 — 2026-05-09

Re-architecting wave: collision radii moved from hardcoded Java
constants into engine-tier config, the last `CorePhysicsConstants.java`
file is deleted, the manual O(N) per-tick spatial scans are replaced
with `mphys.queryBounds`, and a new architectural rule
(Replacement-as-Mutation — single-writer-per-component) is codified.
Player-visible: F1 help no longer lies, mine drops are clean,
long-running servers don't leak. 22 commits across 5 slices.

### For players

**Bug fixes that you'll notice:**

- **F1 help screen used to lie.** The hardcoded keybinding list omitted
  thrust, turn, bomb, bullet, mine, burst, ship-select, and repel —
  most of the actual game keys. Now auto-derived from the active input
  mappings, grouped by category, with `(unbound)` placeholders for
  declared-but-unmapped functions so dev-builds also surface gaps.

- **Mines drop cleanly.** The "mine velocity = (0,0,0)" hack is gone;
  mines now use a real per-ship `MineSpeed` knob (default 0 = inert
  drop, matches Subspace canon). Visible behaviour unchanged on
  default-tuning arenas.

- **Long-running connections used to leak entity tracking on every
  player-count query.** Closed.

**Performance:**

- Bomb splash, proximity-arming, and bomb-safety-spawn scans now use
  the physics engine's spatial-query API instead of walking every
  ship/projectile every tick. Higher-playerload arenas (32+ ships) get
  measurably better tail latency on bomb hits.

### For authors (zones, arenas, ship presets)

**`engine.groovy` gained 12 collision-radius knobs.** Promoted from
hardcoded Java to operator-editable engine-tier:

```
shipRadius      1.0
bulletRadius    0.125
bombRadius      0.5
mineRadius      0.5
thorRadius      0.5
prizeRadius     0.5
burstRadius     0.125
repelRadius     0.125
over1Radius     0.5
over2Radius     1.0
over5Radius     0.1
flagRadius      0.5
```

These are physics-engine facts identical across every arena in a build,
which is why they live engine-tier rather than per-arena. Default
values match the previously-hardcoded constants — no behaviour change
unless you tune them.

**`ships.groovy` per-ship knob added: `mines.speed`.** Default 0
(inert drop). Set non-zero in any ship's `mines:` block to author
kicker-mines that launch forward without inheriting the firing ship's
velocity. Subspace canon doesn't define this — Infinity extension.

**Prize-applier Javadocs now cite REFERENCE.md.** Each prize applier
under `infinity/src/main/java/infinity/systems/ship/applier/` documents
its Subspace canon section, the canonical knob names, units, and any
inline divergences (e.g. the VIE↔UI "Recharge"/"QuickCharge" naming
inversion). Useful when reasoning about per-ship prize tables or
debugging an apply path.

**`physics-audit.md` pruned to current state only.** Completed findings
removed; git history preserves the resolution path.

### For contributors (to this repo)

**New architectural rule: Replacement-as-Mutation (RaM).** Each
component type has exactly one canonical writer; every other system
that wants to influence the value emits an intent (request component
or event) into a queue the canonical writer drains each tick.

- Rule: [`replacement-as-mutation.md`](.claude/rules/replacement-as-mutation.md)
- PRD: [`replacement-as-mutation/PRD.md`](.scratch/replacement-as-mutation/PRD.md)
  (9-item migration backlog, ordered by impact-per-effort)
- Existing canonical examples documented: `EnergySystem` for
  `HealthChange + Buff` intents; mphys integrator for `Impulse`;
  `ShipSpawnSystem` for spawn-time projection; central decay reaper
  for `Decay`.
- Pilot migration slice: `WeaponsSystem` god-class extraction
  (Fire / Reaper / Impact split with intent-emit for Health/Energy).

**`CorePhysicsConstants.java` deleted entirely.** Was the last
general-purpose constants holder; values either belonged engine-tier
(12 collision radii → `EngineConfig`) or were dead. Future code: put
operator-editable physics tunables in `engine.groovy`; put
developer-tier physics constants in `EngineConfig.DEFAULTS` (or, if
they're per-arena gameplay knobs, in `*Config` records under
`api/src/infinity/config/`).

**Mine special-case removed.** `if (weaponFlag == MINE)
projectileVelocity.set(0,0,0)` replaced with `INERT_DROPS = Set.of(MINE)`
constant + Step-3 ship-velocity-inheritance gate. Mirrors the existing
`CENTERED_NO_PROJECTILE` Set idiom in `ConsumableSystem`.
`applyWeaponSpeedScale` gained a real MINE branch reading the new
`MineSpeed` component.

**Spatial queries.** `ProximityFuseSystem.tryArm`,
`WeaponsDamageLogic.applySplashDamage`, and
`WeaponsEligibility.bombSafetyClear` use
`physicsSpace.queryBounds(SphereVolume, QueryFilter)`. Health-bearer
EntitySets preserved post-query as the ECS-state gate; strict
point-distance math preserved for bit-exact radius semantics
(`SphereVolume` inflates by body bounds radius).

**Discipline-ratchet bundle:**

- `LayerDependencyTest` archunit now enforces `infinity.sim..` +
  `infinity.config..` (was only `infinity.es..` + `infinity.events..`).
- Archunit exemptions changed from `doNotHaveSimpleName(...)` to FQN
  match — no more silent collisions on a class-name reused elsewhere.
- `TileType` + `TileKey` fields are now `final` (closes
  `components.md` immutability gap).
- `AvatarSystem.getShipCount` EntitySet leak closed (`try/finally`
  with `release()`).
- `Main.java` graveyard cleaned: 11 commented-out AppState
  instantiations + 2 dead mapper-init calls removed (the AppStates
  attached connection-scoped via `GameSessionState` continue to work).
- `SISpatialFactory.java` dead code removed: `DEBUG_COG = false`
  branches + 4 `@SuppressWarnings("unused")` private factory methods
  + cascaded `createThrustEmitter` (-107 LOC, 2 jME imports dropped).

**Static-analysis ceilings.** Per-tool, per-module strict pin in
`gradle.properties`:

```
maxApiCheckstyleViolations=37
maxApiPmdViolations=0
maxInfinityCheckstyleViolations=921
maxInfinityCheckstyleTestViolations=16
maxInfinityPmdViolations=3
maxModulesCheckstyleViolations=12
maxModulesPmdViolations=0
```

Build fails if any tool's count exceeds the ceiling, with a clear
remediation message. To ratchet down: capture new count, lower the
property, commit. Error Prone + NullAway out of scope (need
stdout-parsing — they emit via `javac` stdout, not separate XML
reports).

**HelpState architecture.** Hardcoded `keyHelp[]` array replaced with
iteration over `InputMapper.getFunctionIds()`. Hybrid description-
override map seeded with the friendly text from the deleted array;
falls back to `FunctionId.getName()`. Grouped by
`FunctionId.getGroup()`. `(unbound)` placeholder for unmapped
FunctionIds turns F1 into a self-debugging tool for binding-table
drift.

**`ModelViewState.cleanup` leaks closed.** Released the `avatarEntity`
WatchedEntity (with null-guard), removed the previously-missing "Lobs"
debug-value removal, nulled `posRef`, added defensive
`bodies/models/largeModels.stop()` for the cleanup-while-disabled
edge in jME's `BaseAppState` lifecycle contract.

**`GameEntities.create*` factories gained `radius` parameters** with
backward-compat overloads using `EngineConfig.DEFAULTS.<radius>()` so
module callers (`basicTester`, `warpTester`, etc.) compile unchanged.
Same shape across all 12 affected factories.

**New PRDs:**

- `.scratch/debug-state-bindings/PRD.md` — F12 toggle to swap between
  Game-mode and Debug-mode key bindings; canonical Subspace key
  layout. Multi-slice plan; not started.
- `.scratch/arch-review.md` — Read-only architectural review (13
  active findings ranked by impact ÷ effort). Tier 1 fully cleared
  this release; Tier 2 #1 (`WeaponsSystem` extraction as RaM pilot)
  is the natural next slice.

**Deleted scratch + workflow files** (no replacement; git log carries
the history):

- `.scratch/active-work.md` (per-machine claims tracker, no longer
  needed)
- `.scratch/config-consumers.md` (Infinity-only config registry —
  coverage moved to per-rule documentation)
- `.claude/rules/multi-machine-workflow.md` (workflow no longer
  enforced)
- `.scratch/guns/PRD.md`, `.scratch/radar-viewport/*` (landed features)

**Tooling:**

- `.gitignore` extended with `.claude/scheduled_tasks.lock` (transient
  scheduler artefact from Claude Code's experimental agent-team
  feature).

### Breaking changes

- **`CorePhysicsConstants.java` is deleted.** Any external module that
  imported it no longer compiles. All values either moved to
  `EngineConfig` or were dead.
- **`EngineConfig` constructor extended** from 4 to 16 parameters
  (added 12 collision radii). Test fixtures updated; presets
  without explicit radii fall through to `EngineConfig.DEFAULTS`.
- **`ShipConfig`'s nested `MineStats` gained `speed`** (Subspace
  velocity units, default 0). All `ships.groovy` fragments updated.
- **`GameEntities.create*` factories** (Ship, PlayerShip, Bomb,
  Bullet, Mine, Burst, Repel, Over5, AsteroidSmall, AsteroidMedium,
  TurfStationaryFlag, Prize, Thor) all gained a `double radius`
  parameter. Backward-compat overloads forward via
  `EngineConfig.DEFAULTS` for ABI safety.
- **`ShapeNames.createShip(byte, EntityData)` →
  `ShapeNames.createShip(byte, EntityData, double radius)`.**
- **`WeaponsLogic.applyProjectileRadiusOffset`** signature gained
  `bulletRadius` + `bombRadius` parameters.

### Acceptance + smoke-test

- `./gradlew :api:test :infinity:test :modules:test` — green.
- Static-analysis ceilings — green at baseline; synthetic-violation
  smoke verified failure-with-clear-message.
- Manual smoke (recommended): load trench arena, fire bullets / bombs
  / bursts / mines, confirm physics feel unchanged. F1 should now
  show every binding grouped by category. Mine drops should remain
  in place (no drift).
