# Release notes

Latest release only. Earlier history lives in git tags + commit log
(`git log v<previous>..v<this>`).

## v1.0.19 — 2026-05-14

Code-quality and architectural-review release. Lint/Sonar/PMD baselines ratcheted to zero across both server and client, pre-push hook wired so `./gradlew check` runs locally before every push, JaCoCo coverage ratchets in CI, and the P1 + P2 architectural-review batches landed (~148 new test methods + P2 follow-ups). One concrete gameplay fix: the gravbomb fire path actually works now. 34 commits since v1.0.18.

### For players

**Gravbombs are firable.** Previously no system stamped the `GravityBombCost` component, so the gravbombs `EntitySet` was always empty and the fire path was dead code. Reshaped to inventory-style (per-ship count + cooldown) mirroring Thor: bomb-carrying ships in trench / deva / testconf start with a small gravbomb stockpile and a 2-second per-fire cooldown. Projectile level inherits the ship's current bomb level (Subspace canon: gravbombs are level-3 bombs).

Otherwise no observable gameplay changes.

### For authors (zones, arenas, ship presets)

**New `gravBombs` DSL in `ships.groovy`** — opt in to inventory-style gravbombs per ship:

```groovy
ship(Ship.JAVELIN) {
    bombs     start: BombLevel.BOMB_1, max: BombLevel.BOMB_1, cost: 1100, fireDelay: 75, speed: 2250, thrust: 400
    gravBombs start: 3, max: 5, fireDelay: 200    // count + cooldown (cs); mirrors thors shape
    // ...
}
```

Omit the `gravBombs` block to disallow gravbombs on that ship (= the `null` disallow signal, same as other inventory blocks). The arena-wide `[Wormhole] GravityBombs` knob in `misc.groovy` is unrelated (it gates wormhole pull on bombs).

No other `.groovy` file edits required.

### For developers / contributors

**Pre-push hook + CI gates.**

- **Pre-push githook** runs `./gradlew check` on every `git push`; commit `4bf12c14`. Prevents lint/test regressions from reaching the remote. Skips with `git push --no-verify` only when intentional.
- **JaCoCo coverage ratchet** wired into `:api:check` / `:infinity-server:check` / `:infinity-client:check` per module; ratchets land as baseline values in `gradle.properties` and only go up. CI now fails on coverage regression.

**Lint baselines at zero.**

- **PMD** — both `:infinity-server:pmdMain` and `:infinity-client:pmdMain` baselines cleaned to **0 violations**. New PMD rules enabled (Sonar parity). `pmdPath` task added for per-file ratchet (see [`pmd-on-touched-files.md`](.claude/rules/pmd-on-touched-files.md)).
- **Checkstyle** — 825 fixes across the tree; ceilings ratcheted down to match.
- **Sonar MCP** integrated for local IDE lookups. Tiers A through E3 cleared mechanically: S1192 (literal duplication), S1948 (non-serializable fields), S2178 (unsafe `&` short-circuit), S1845 (case-only name collisions), S115 (constant naming), S1452 (raw types), S2447 (Boolean return null), S1186 (empty method intent), S116 (Hungarian-notation `m_*` field rename), S1104 (encapsulation).

**Architectural review batches.**

- **P1 batch** — ~148 new test methods covering canonical-writer behaviour, lifecycle release, projection determinism. `architectural-review-2026-05-13.md` follow-ups closed.
- **P2 batch** — refactors landed for the items P1 surfaced; doc sweep on `.claude/rules/*.md` to reflect the post-batch state.

**Shape-interface refactor (ADR 0001 follow-up).**

- `EnergyCost` interface now unifies the cost-deduction path for energy weapons (`BulletStats`, `BombStats`, `MineStats` all implement it).
- `InventoryCount` / `InventoryCap` / `DeltaChange` interfaces unify the inventory-count writer path; `BaseInventoryCountSystem<C, D, S>` generic captures the recipe. `BurstSystem`, `BrickSystem`, `DecoySystem`, `PortalSystem`, `RepelCountSystem`, `RocketSystem`, `ThorSystem`, `GravBombSystem` are 5-line subclasses now (was ~50 lines of drain/clamp boilerplate each).
- Status-family drain unified into `BaseEnergyDrainSystem` (subclassed by `StatusDrainSystem`; `AfterburnerDrainSystem` will be a sibling).

**Concurrency fix.** `DefaultColumnDb` was unsynchronised across read/write/save. Striped locks per column path + atomic file replace (write-then-rename) so concurrent writes can't corrupt the on-disk format. Commit `8a8e419d`.

**TODO/FIXME triage.** 45 in-code comments cleared: 16 deleted (stale / done), 8 reshaped into `NOTE` comments where the explanation is the point, 21 promoted to `.scratch/code-todos-backlog.md` for tracking. Commit `4ece3d21`.

**Removed.**

- `GravityBomb` (level marker) and `GravityBombCost` — replaced by `GravBomb` (inventory count) + `GravBombStats` (`InventoryCap`). Per-ship gravbomb level reads from `BombCurrentLevel` now.
- `GravityBombFireDelay.copy()` — unused, deleted.
- `.devcontainer/` — unused, deleted.

**Module-author breaking changes:** none beyond `GravityBomb` / `GravityBombCost` removal (these only existed in dead code paths in the tree as of v1.0.18; no external author was using them).

## v1.0.18 — 2026-05-13

Architecture-formalisation release. The single ADR (0001) the v1.0.17 release shipped now has six siblings; the operational rules that previously lived only in `.claude/rules/*.md` prose have formal ADRs behind them, and three ArchUnit guards lock in the discipline at build time. Plus three concrete bugs caught by the P0 audit (client `EntitySet` / `WatchedEntity` leaks across 7 app states; `Delay` wall-clock breaking pause / replay; per-shot `BombConfig` hot-path read in `WeaponsEligibility`). 10 commits since v1.0.17.

### For players

No observable gameplay changes. Subtle memory-growth fix from the client lifecycle cleanup may help long sessions; otherwise the visible game is unchanged.

### For authors (zones, arenas, ship presets)

**No `.groovy` file edits required.** Same fragment shape, same hot-reload behaviour. The architectural changes are all internal.

**Naming change:** "Pattern 4" is retired in favour of **Config-Component Projection (CCP)** per ADR-0002. Anywhere your zone docs or notes used "Pattern 4" to describe the template-vs-component split, that's now CCP. Behaviour unchanged.

### For developers / contributors

**Six new ADRs** in `docs/adr/`, each formalising a previously-implicit decision:

- **ADR-0002** — Config-Component Projection (CCP). Template (`*Config` records in `api/`) → component (per-entity, ECS) projection at spawn. Hot-path code reads components, never templates.
- **ADR-0003** — Communication channels. Intent components (`*Change` + `ChangeTarget`) for mutation requests, EventBus for announcements, ContactSystem-shape for high-frequency domain firehoses. Decision tree included.
- **ADR-0004** — `zone/` extension surface. Settings pipeline (host + adapter + `ConfigRegistry`) **plus** the guardrailed Groovy module-loader design — modules ship `*System` (server) + `*AppState` (client) + components, compiled against api/ only.
- **ADR-0005** — Layered architecture. api / server / client / modules with directed deps; `infinity.sim.internal..` carved out from the api-tier namespace for the server-side relocation.
- **ADR-0006** — Tuning knobs vs. magic numbers. The literal-promotion decision rule (when does a Java `60` belong in Groovy?).
- **ADR-0007** — Entity TTL via `Decay`. Single mechanism, deadline-shaped, multi-writer-by-design exception. `Delay` and `Jitter` explicitly distinguished as adjacent concerns.

Plus `docs/adr/README.md` indexes them with suggested reading order.

**Three ArchUnit guards** added to `LayerDependencyTest` + `CanonicalWriterTest`:

- **Layer Rule 1 extended** — api/ classes may not depend on `infinity.settings..` (was: only systems/server/client/modules/ai forbidden).
- **Hot-path config-import guard** — no class in `infinity.systems..` imports `infinity.config..` except a named-allowlist of spawn-tier / creation-time / admin sites. Mechanises ADR-0002's discipline.
- **Canonical-writer registry extended** — `CanonicalWriterTest` now covers 32 component types (was 24), closing ADR-0001's TBD-3 open item.

**Server-internal relocation** (potentially breaking for module authors who imported these directly):

- `infinity.sim.CubeFactory` → `infinity.sim.internal.CubeFactory`
- `infinity.sim.InfinityDefaultLeafWorld` → `infinity.sim.internal.InfinityDefaultLeafWorld`
- `infinity.sim.InfinityEntityBodyFactory` → `infinity.sim.internal.InfinityEntityBodyFactory`
- `infinity.sim.InfinityPhysicsManager` → `infinity.sim.internal.InfinityPhysicsManager`
- `infinity.sim.PlayerDriver` → `infinity.sim.internal.PlayerDriver`
- `infinity.sim.Driver` — **deleted** (was unused interface, zero references)

api-side `infinity.sim..` is now unambiguously the module-facing ABI (`ShipFactory`, `WeaponFactory`, `MapFactory`, `PhysicsManager`, `TimeManager`, `ChatHostedPoster`, `AccountManager`, …).

**P0 bug fixes (audit-driven):**

- **Client `EntitySet` / `WatchedEntity` / `EntityContainer` release sweep.** 7 client `BaseAppState` classes never released their resources in `cleanup()` — `InfinityCameraState`, `MobDebugState`, `AudioState`, `HudLabelState`, `MapState`, `SpeechViewState`, `PlayerListState`. Defensive `if (field != null) { stop/release(); field = null; }` pattern applied.
- **`Delay` stores SimTime deadline.** Was using `System.nanoTime()` in the constructor — wall-clock in a sim component breaks pause, replay, and deterministic test fixtures. Now mirrors `Decay`'s `(startTime, endTime)` shape.
- **`BombSafetyRadius` projected at ship spawn.** `WeaponsEligibility` was reading `BombConfig.bombSafety()` + `BombConfig.proximityDistance()` on every shot check (CCP hot-path leak). Now reads a per-ship component projected at spawn.

**New api/ component / utility:**

- `infinity.es.ship.weapons.BombSafetyRadius` — per-ship snapshot of arena bomb-safety + L1 prox tiles.
- `infinity.config.PhysicsDefaults` — physics fallback constants for the no-arena case (`DEFAULT_WALL_FRICTION = 0.0`).
- `ArenaSystem.getWallFriction(arenaName)` — hides `ArenaConfig` from per-contact callers.

**Rule cross-links.** Each `.claude/rules/*.md` file with a paired ADR now references it at its header (`config-pattern.md` → ADR-0002; `decay-ttl.md` → ADR-0007; etc.). `CLAUDE.md`'s path-scoped rule list adds ADR parentheticals so agents loading CLAUDE.md as first context see both pointers.

**Architectural review.** Full review in `.scratch/architectural-review-2026-05-13.md` — 4 P0 items + 3 P1 items closed this release; P0 tier is now empty.

## v1.0.17 — 2026-05-12

ECS architecture release. ADR 0001 (Continuous + Stats with
Change-entity mutation) landed end-to-end across 19 ship-state
aspects, 4 fresh-find multi-writers, a 5-class non-ship audit, and
an architectural enforcement test — every ship-state component now
has exactly one canonical writer in `infinity-server/`, enforced at
build time. Followed by a repo-wide Javadoc sweep that trimmed
~5800 lines of recipe restatement + migration history while
preserving every Subspace canon translation, race-condition
mitigation, ordering constraint, and REFERENCE.md cite. 30+ commits
since v1.0.16.

### For players

No observable gameplay changes. Damage, recharge, ship swap,
team change, status toggles, weapon firing, prize pickup, rocket
buff, warp commands, and Groovy hot-reload all behave the same.
The migration was structural; the visible game is unchanged.

### For authors (zones, arenas, ship presets)

**No `.groovy` file edits required.** Same fragment shape; same
`SettingsSystem` accessors; same hot-reload behaviour. The internal
template→component projection switched plumbing but the inputs and
outputs are unchanged.

**One new admin chat command: `~prize <name>`.** Grants any prize
(Bomb / Gun / Energy / Cloak / Antiwarp / etc.) to your avatar via
the same applier path the collision-driven pickup uses. Useful for
testing without flying over a spawner. PLAYER_LEVEL access — same
as the other test commands like `~tparena` and `~warpCenter`.

### For developers / contributors

**The Change-entity recipe** is the canonical post-creation
mutation shape. Every aspect follows the same template:

- `*` (Continuous): live, frame-touched scalar
- `*Stats` (record): cold rules — max, drain rate, cooldown, …
- `*Change(delta)`: mutation payload on a transient holder
- `*System`: canonical writer that drains `*Change + ChangeTarget`

Emitters create holders with `ChangeTarget(target, source) +
*Change(delta)` (optionally `+ Decay` for temporary effects); the
canonical writer applies, clamps, sums same-tick deltas, and
reverses Decay-bound deltas on reaper removal. See
[`docs/adr/0001-ecs-component-model.md`](docs/adr/0001-ecs-component-model.md)
+ [`.claude/rules/replacement-as-mutation.md`](.claude/rules/replacement-as-mutation.md).

**`CanonicalWriterTest` enforces one-writer-per-target** for 21
registered (component → writer) pairs. Multi-writer violations
fail CI rather than slip past review. Adding a new aspect = add a
row to the registry + a canonical writer class. Spawn-tier
projectors (`ShipSpawnSystem`, `ShipStatusProjector`,
`ShipWeaponsProjector`) are allowlisted per ADR §"Spawn-time
projection is the single-writer at creation time".

**Deleted types** (no replacements needed; the wire is unchanged):
`Buff`, `HealthChange`, `Intent`, `CapBump`, `CapField`,
`RocketBuffIntent`, `WarpTo` (intent shape — `WarpToChange` is the
post-ADR replacement). Plus ~25 scattered `*Max` / `*Upgrade` /
`*Cost` / `*FireDelay` constituent components folded into bundled
`*Stats` records.

**One real bug fixed mid-migration:** `ThorPrizeApplier` no longer
hardcodes `new ThorFireDelay(1000)` on first-time pickup —
previously this clobbered the per-ship `[Ship] ThorFireDelay`
config value. The applier now reads `ThorStats.fireDelayMillis()`.

**Javadoc discipline rule** (`.claude/rules/javadoc-discipline.md`)
is now path-scoped and explicit about the three-category test:
DELETE pattern restatement + WHAT-describing-the-code; KEEP domain
facts (Subspace canon, ordering constraints, race-condition
mitigations, REFERENCE.md cites, architectural maps). New
contributors get the rule auto-loaded when they touch any `.java`
file.

## v1.0.16 — 2026-05-10

Major restructure release. The single `:infinity:` module has been
split into `:infinity-server:` (headless-capable) and `:infinity-client:`
(fat client + local-host). Two distribution targets now ship from one
build: a headless dedicated-server zip and a client zip that can host
its own server in-process. Game data (`assets/`, `zone/`) moved to the
project root and the runtime registers external `FileLocator`s so
`zone/engine.groovy` and per-arena Groovy presets hot-reload from a
*dist install* — edit the file in your unzipped distribution, observe
the reload within seconds, no rebuild. Plus the post-`v1.0.15`
architectural review is fully cleared (all 13 findings landed across 4
bundle commits + a 5-teammate post-refactor review identified 28 new
items — see `.scratch/BACKLOG.md`). 31 commits since v1.0.15.

### For players

**Two ways to play, finally as separate downloads.**

- **Fat client (`infinity-1.0.16.zip`)** — 618 MB. Includes everything;
  can host its own server (the same "host a game" flow as before).
  Double-click `bin/infinity-client` (or `infinity-client.bat` on
  Windows). On Linux/Wayland, use `bin/infinity-client` with
  `WAYLAND_DISPLAY=` cleared, or run with the `runX11` script if
  you've cloned the repo.
- **Headless dedicated server (`infinity-server-1.0.16.zip`)** —
  324 MB. No rendering; no Lemur UI deps. Run from terminal via
  `bin/infinity-server`. For people who want to run a persistent
  game arena from a server box without spinning up the GUI.

**Radar shows your current arena differently.** When you have multiple
arenas loaded, the one your avatar is in renders at full saturation
(today's behaviour) and other loaded arenas mute to ~50% saturation.
The radar's polygon outlines all looked identical before; now there's
a hierarchy at a glance.

**Keybinding fix.** `F_REPEL` was accidentally bound to four keys
(F3 + F4 + F5 + F7) at the same time, while `F_DECOY` / `F_ROCKET` /
`F_BRICK` / `F_ATTACH` had no bindings at all. Repel now lives on its
canonical key only; the others are intentionally unbound until their
client-side handler ships.

### For authors (zones, arenas, ship presets)

**Game data moved to project root.** Your edits go to:

```
assets/        (was infinity/assets/)
zone/          (was infinity/zone/)
zone/engine.groovy   (was infinity-server/src/main/resources/engine.groovy)
```

The same paths sit at the dist-zip root — extract a release, edit
`zone/conf/<preset>/ships.groovy` or `zone/engine.groovy`, save, and
the running server picks up the change within ~5 seconds via the
mtime watcher. Per-arena `ships.groovy` reload was already wired;
zone-tier `engine.groovy` and `zone.groovy` reload are new in this
release.

**Repel `distance` now in tiles, not Subspace pixels.** If you author
SVS-canon `distance: 512`, that's 512 pixels — divide by 16 to get
tile units. Default in `zone/conf/<preset>/repel.groovy` is now
`distance: 32` (the 512-px equivalent). `BombConfig.explodeRadius`
already shipped tile-only in v1.0.15; Repel matches the pattern now.

**`zone/conf/svs*` presets** (`svs`, `svs-league`, `svs-pb`, `svs-tce`,
`svs-turf`) are NOT referenced by any active arena. They're authoring
templates for the original SVS canon — historical reference. The
`base`/`trench-04-2026`/`deva-04-2026`/`testconf` presets are the
actively-used ones.

**Historical config dump moved.** The 1998-1999 `.CFG` / `.INI`
reference files (Trench Wars, ASWZ, etc.) moved from `infinity/configs/`
to `.scratch/historical-configs/` — out of the build path, still
available for git-archaeology.

### For contributors (to this repo)

**Module split (arch-review #11).** `:infinity:` is gone. New layout:

```
api/                  ← shared contract layer (gained net/, util/, InfinityConstants)
infinity-server/      ← headless server (~169 files: ai/, map/, server/, settings/, sim/, systems/, tools/)
infinity-client/      ← fat client + HostState bridge (~73 files: Main.java + client/)
modules/              ← Gradle subproject; *Tester stubs deleted (BaseGameModule abstraction stays in api/)
```

Build commands updated (`CLAUDE.md` already reflects):

```
./gradlew :infinity-client:run        (was :infinity:run)
./gradlew :infinity-client:runX11     (Linux/Wayland)
./gradlew :infinity-client:runMac     (-XstartOnFirstThread)
./gradlew :infinity-server:run        (NEW — headless dedicated server)
```

`distZip` now produces both artifacts. `infinity-server/build.gradle`
includes `jme3-desktop` (no rendering — just the AWT-based AssetManager
factory needed for the platform delegate at headless boot).

**13 of 13 arch-review findings closed across 4 bundles.**

- `arch-review-tier2-bundle` (5 items) — `WeaponsSystem` god-class
  split into `WeaponsFireSystem` + `WeaponsImpactSystem` +
  `WeaponsReaperSystem` (RaM pilot — emits `DamageSource` intent
  alongside the existing `HealthChange + Buff`); `ConfigRegistry`
  slot-store + abstract `SingleClosureAdapter`/`Validators` across 12
  fragment loaders; client/server/net seed tests; `EnergySystemIntentTest`
  harness pillar; `EngineConfigSystem` mtime watcher.
- `arch-review-tier3-bundle` (6 items) — `BaseInfinitySystem.requireSystem`
  helper retrofitted across 14 systems; `WatchedEntity` lifecycle
  convention pinned (release in `cleanup()`); `GameEntities` (976 LOC)
  split into `ShipFactory` + `WeaponFactory` + `MapFactory`; `GroovyShipLoader`
  789 → 219 LOC (extracted `ShipFallback` + `ShipConfigBuilder`);
  api/test sourceset + 7 factory tests moved to `api/`; `Main.java`
  Simsilica boilerplate → project SPDX header.
- `arch-review-megasplit-bundle` — the module split itself.
- `backlog-cleanup-bundle` (7 items) — typed `MapAction` enum (closes
  the byte-constant layer leak); `MapSystem` split into
  `LegacyMapProjector` + `WallLightDecorator`; `ArenaSpatialIndex`
  extracted from `ArenaSystem`; `SISpatialFactory` split into
  `EffectSpatialFactory` + `QuadMeshes`; Repel pixels → tiles;
  AvatarMovementFunctions keybinding fix; library `'+'` versions
  pinned to currently-resolved.
- `backlog-final` — `modules/` *Tester stubs deleted; 18 `*Spec`
  records introduced for factories with >5 args; radar muted styling.

**Hot-reload from dist install** (slice/hot-reload-from-dist).
`AssetLoaderService` (server) and `Main.simpleInitApp` (client) both
register `FileLocator` against `./assets/` and `./zone/` working-dir-
relative — project root in dev (`run.workingDir = rootProject.projectDir`),
dist root in production. `GroovySettingsHost.resolveOnDisk` reads
filesystem-first, falls back to classpath. `engine.groovy` moved out
of module resources to `zone/engine.groovy` so the existing
`EngineConfigSystem` watcher activates from a dist install. New
zone-tier watcher (`ArenaSystem.pollZoneGroovyReload`) covers
`zone/zone.groovy`.

**Replacement-as-Mutation (RaM) pilot landed.** `WeaponsSystem`
extraction was the proving ground. `DamageSource(EntityId source,
byte weaponFlag)` is the new attribution component, layered as an
optional sibling on `HealthChange + Buff` so existing reactors still
work; `HitFeedback`-style reactors that need to fork on intent now
have something to fork on. Migration backlog (cap-bump intents,
inventory decrements, etc.) lives in `.scratch/replacement-as-mutation/PRD.md`.

**Architectural review v2.** Post-refactor 5-lens review (planner /
config-2 / spawn / cleanup / client) identified 28 fresh findings —
mostly small wins from the recent refactoring's surface area. Highest-
value Tier 1 items: `MapState.java:449` DELETE-uses-wrong-axis bug,
`WeaponsImpactSystem.lastTickNanos` non-volatile cross-thread,
`EffectSpatialFactory.ef` NPE landmine. Tier 4 items: continuing the
RaM migration to inventory + status families (the largest live
multi-writer cluster). Full ranking in `.scratch/BACKLOG.md`.

**`physics-audit.md` aggressively trimmed.** 299 → 120 LOC. Stale
slice claims (S4 spatial-query promotion was actually landed) corrected;
landed slices removed; deferred slices (S3 bomb-bounce, S9 wormhole-
Gravity, S10 afterburner) relocated to BACKLOG.md.

**New rules + skill updates:**

- `.claude/rules/entity-sets.md` extended with the post-pilot
  "release in `cleanup()`" client-side convention.
- `.claude/skills/infinity-architecture/SKILL.md`,
  `.claude/skills/project-overview/SKILL.md`,
  `.claude/skills/lvl-format/SKILL.md`,
  `.claude/skills/arena-settings/SKILL.md`,
  `.claude/skills/jme-appstate/SKILL.md`,
  `.claude/skills/sio2-system/SKILL.md`,
  `.claude/skills/jme-effects/SKILL.md`,
  `.claude/skills/moss-world-grid/SKILL.md`,
  `.claude/skills/subspace-moss-terminology/SKILL.md`
  all updated to post-megasplit module names + paths.

### Breaking changes

- **`:infinity:` Gradle module no longer exists.** External callers /
  custom build scripts that target it must switch to `:infinity-server:`
  or `:infinity-client:` depending on what they want.
- **`GameEntities.java` deleted.** Use `ShipFactory.create*`,
  `WeaponFactory.create*`, or `MapFactory.create*` (per-domain
  factories). No facade preserved; the `GameEntities`-as-grab-bag
  growth was the calcification problem the split closed.
- **`WeaponsSystem.java` deleted.** Logic split into
  `WeaponsFireSystem`, `WeaponsReaperSystem`, `WeaponsImpactSystem`.
  Any external module that referenced `WeaponsSystem.{BOMB,BULLET,MINE}_LEVEL_PREFIX`
  finds them now on `WeaponsFireSystem`. Detonation triggers go through
  `WeaponsReaperSystem.detonate(...)`.
- **`MapSystem.CREATE` / `MapSystem.DELETE` byte constants removed.**
  Use the new `infinity.events.MapAction` enum (`CREATE`, `DELETE`).
  The RMI signature changed too: `GameSession.map(byte, Vec3d)` →
  `GameSession.map(MapAction, Vec3d)`.
- **`assets/` and `zone/` moved to project root.** Build files using
  `rootProject.file('infinity/assets/...')` need updating to
  `rootProject.file('assets/...')`.
- **`engine.groovy` moved.** From `infinity-server/src/main/resources/`
  to `zone/engine.groovy`. Hot-reload now works from dist installs.
- **`RepelConfig.distancePixels` → `RepelConfig.distanceTiles`**
  (`int` → `double`). `RepelDistance` component component
  `getPixels()` → `getRadiusWorldUnits()`. `RepelAdapter`'s
  `distance` key now consumes tiles (divide your SVS-canon
  pixel value by 16 in your `repel.groovy`).
- **18 factory methods (>5 args) take `*Spec` records** instead of
  positional args. Affected: `createShip`, `createPlayerShip`,
  `createRocketBuff`, all `WeaponFactory.create*` (Bomb, DelayedBomb,
  Bullet, Explosion, Burst, Repel, Thor, Mine), `MapFactory.create*`
  for Wormhole / Door / Over5 / Asteroid{Small,Medium} /
  WarpEffect / TurfStationaryFlag / Prize / Spawner. Below-threshold
  factories (`createLight`, `createBrick`, `createDecoy`,
  `createPortal`) keep positional args.
- **`infinity-server/build.gradle` adds `jme3-desktop`** for the
  headless platform delegate. No rendering — just the AWT-based
  AssetManager factory. Ship size is unaffected (`jme3-lwjgl3` still
  excluded).
- **Modules `*Tester` stubs deleted** (`basicTester`, `doorTester`,
  `lightTester`, `prizeTester`, `wangTester`, `warpTester`).
  `BaseGameModule` + `BaseGameService` abstractions remain in `api/`.

### Acceptance + smoke-test

- `./gradlew build` — BUILD SUCCESSFUL; `:api:test :infinity-server:test
  :infinity-client:test :modules:test` all green.
- `:infinity-client:distZip` and `:infinity-server:distZip` produce
  the two distribution artifacts (618 MB + 324 MB respectively).
- `:infinity-client:runX11` smoke-tested by user across multiple
  bundle merges in this session (Tier 2, Tier 3, megasplit, layout,
  hot-reload-from-dist, backlog-cleanup, backlog-final).
- Hot-reload from dist verified end-to-end: `unzip` server dist,
  `sed`-modify `zone/zone.groovy`, observe `zone.groovy reloaded`
  log within polling window.
