# Architectural Review — 2026-05-13

**Method:** six specialist agents in parallel (ECS compliance / layer boundaries / settings pipeline / server cohesion / client lifecycle / test coverage), synthesised by hand. Read-only audit; no edits performed.

**Headline:** the codebase is in good architectural shape. ADR-0001 is enforced almost everywhere it claims to be, layer boundaries are clean save for one concrete leak, the settings pipeline is well-bounded, the System/Logic split is coherent across the server, and the client correctly observes-only. The largest gap is **test coverage of two boundaries that are intentionally manual today** (prize appliers translating Subspace canon; settings adapters translating Groovy DSL). The second-largest gap is **mechanised architecture enforcement** — most of the discipline lives in `.claude/rules/*.md` and is audited by hand, not by tests.

---

## Findings, grouped

### A. ECS model (ADR-0001) — strong

| | Result |
|---|---|
| Component immutability (final fields, no setters, no-arg ctor) | **0 violations** across `api/src/main/java/infinity/es/**` |
| `EntitySet` lifecycle (released in `terminate()` / `cleanup()`) | **0 leaks** on server; **1 leak on client** — see E |
| Canonical-writer rule (one writer per component) | **0 violations** — `replacement-as-mutation.md` snapshot confirms 0 remaining multi-writer hot-spots across ~95 component types |
| Decay-TTL parallelism | **0 violations** — `Decay` is the sole entity-lifetime mechanism. `Delay` and `Jitter` are distinct concepts (deferred-action; component-reaper) |
| Hot-path config-import discipline | **2 violations** (CCP leak) — see D2 |

The migration work the team has already invested is real. The audit found exactly the shape ADR-0001 promised. The next-most-valuable investment is a mechanised guard that prevents regressions — see ADR-1 below and item P1-a.

### B. Layer boundaries — one concrete leak, three test gaps

- `modules/` is cleanly excised at the source level (removed from `settings.gradle` in v1.0.17, zero imports referencing it). Only stale `1.0.17` build artifacts remain on disk — harmless but worth a `./gradlew clean` and a `.gitignore` check.
- `api/` is clean. Zero imports of `infinity.client.*`, `infinity.systems.*`, `infinity.server.*`, `infinity.settings.*`.
- Server is clean. Zero imports of `infinity.client.*`.
- ADR-0005's `infinity.sim.internal..` relocation landed: `CubeFactory` moved to `api/`; the 5 server-internal classes (`InfinityDefaultLeafWorld`, `InfinityEntityBodyFactory`, `InfinityPhysicsManager`, `PlayerDriver`, plus `Driver` which was dead code) moved to `infinity.sim.internal..`. `LayerDependencyTest` Rule 3 now forbids `infinity.sim.internal..` on the client. Remaining gap (per P1-c): `infinity.settings..` is not yet in Rule 1's forbidden list.

### C. Settings pipeline — well-architected

- The `GroovySettingsHost<T>` + `GroovySettingsAdapter<T>` refactor has landed for **12 fragment adapters**. Two legacy `Groovy*Loader` classes remain (`GroovyShipLoader`, `GroovyArenaLoader`) — both intentional, neither blocking. Two boot-time loaders (`GroovyEngineLoader`, `GroovyZoneLoader`) are outside the per-arena flow by design.
- `ConfigRegistry` ownership is clean: one `ConfigRegistrySystem` per server, `ConcurrentHashMap<arenaId, ConfigRegistry>`. Arena lookups via `forArena()` with `EMPTY` fallback.
- **16 / 18 `*Config` records** are fully wired through the loader → registry → projection chain. Two dangling `ConfigRegistry.SLOTS` entries — `ThorConfig`, `GravBombConfig` — have no fragment adapters and fall back to `DEFAULTS`. Intentional today (Thor is per-ship via `ships.groovy`; GravBomb hasn't diverged from Bomb yet) but undocumented — a future author may waste effort adding an adapter that wouldn't be loaded.
- **32 / 32 prize appliers** emit `ChangeTarget + *Change` entity holders per ADR-0001. None directly mutate components. Spot-checked appliers (`Cloak`, `Thor`, `Shields`) all cite REFERENCE.md or document an Infinity divergence.
- **Live-reload coverage is partial — by design.** Editing `ships.groovy` triggers `ShipSpawnSystem.reprojectAll()` and re-flows into live ships. Editing weapon / prize fragments updates the registry but does not re-flow into existing entities — new spawns / new shots pick up the change. Defensible (template vs instance, CCP) but surprising to operators; merits an operator-runbook note.

### D. Server cohesion — good, three pin-pricks

1. **No god systems by accretion-of-state.** `PrizeSystem` (805 lines), `WeaponsFireSystem` (546), `ConsumableSystem` (557) are domain-coherent — none are accreted state. System/Logic pairs (`MapSystem`/`MapSystemLogic`, `WeaponsFireSystem`/`WeaponsLogic`, `ConsumableSystem`/`ConsumableLogic`) are clean ECS-shell + pure-helper splits. **By Use-Case granularity** (Clean Architecture's Single Responsibility applied to gameplay rules) the answer is different: `PrizeSystem` carries at least three distinct Use Cases — arena prize spawning, prize-on-contact consumption, death-prize spawning — and `WeaponsFireSystem` carries at least three — eligibility check, projectile-spawn dispatch, audio side-effects. Splitting either by Use Case would improve testability and per-rule isolation. `ConsumableSystem` is single-Use-Case ("player consumable actions") despite the line count. Splits tracked as P2-k.
2. **`infinity.config` imports outside the spawn-tier exempt set** (per ADR-0002 Config-Component Projection). Two hot-path leaks plus four edge cases the ADR's rule-as-written does not unambiguously cover:
   - **Hot-path violations** (project the read off the hot path):
     - `infinity-server/src/main/java/infinity/systems/ship/WeaponsEligibility.java:17` imports `infinity.config.BombConfig` and reads `cfg.bomb()` on every shot-eligibility check. Should project `BombSafetyRadius` + `ProximityDistance` into per-ship components at spawn time.
     - `infinity-server/src/main/java/infinity/systems/ship/WeaponsDamageLogic.java:18-19` imports `infinity.config.ArenaConfig` + `infinity.config.EngineConfig` and reads them per detonation. Lower per-tick frequency than the WeaponsEligibility case but the same shape.
   - **Spawn-adjacent reads not named in the ADR's exempt set** (settings agent classified as "creation-time / acceptable"; consistent with CCP intent but would fail a strict ArchUnit guard as written today):
     - `WeaponsFireSystem.createProjectileBullet()` reads `BulletConfig` / `BombConfig` at projectile spawn.
     - `PrizeSystem.spawnBounty()` reads `PrizeWeightsConfig` and `PrizeConfig` at prize spawn.
     - `ArenaSpatialIndex.getArenaSpawn()` reads `SpawnConfig` at arena-entity lookup (cold path).
   - **Sentinel-constant lookup**:
     - `ContactSystem` imports `ArenaConfig` solely to call `ArenaConfig.EMPTY.wallFriction()` as a fallback constant — neither a hot read nor a spawn projection.
   - Resolving these three boundary cases is a prerequisite for P1-d's static guard; see P0-c and P1-d for the design notes.
3. **Channel-discipline findings (per ADR-0003) plus one dead field:**
   - **Direct `getSystem` calls bypassing both intent-component (Channel A) and bus (Channel B) channels:**
     - `EnergySystem.getSystem(PrizeSystem.class)` on death-edge (line ~164) silently no-ops if `PrizeSystem` is absent at registration — implicit ordering contract. A `PrizeSpawnIntent` component drained by `PrizeSystem` (Channel A) removes it.
     - `WeaponsImpactSystem.requireSystem(WeaponsReaperSystem.class).detonate(...)` — in-tick synchronous call; could be intentional (immediate detonation; no queryability needed), but the channel choice is undocumented. Decide and document.
   - **`ContactSystem` is a third communication channel in practice** — seven systems register listeners against it (`WeaponsImpactSystem`, `ConsumableSystem`, `WarpSystem`, `PrizeSystem`, `FrequencySystem`, `ArenaMembershipSystem`, `GravitySystem`). ADR-0003 names this Channel C (domain-specific, narrow allowance). Registration order in `GameServer.java` is the implicit listener-ordering contract (e.g. `WeaponsImpactSystem` must process a contact before `PrizeSystem` for kill-credit) and is undocumented in `ContactSystem`'s own Javadoc per the Channel-C bar.
   - **Dormant bus surface.** `ShipEvent.shipDestroyed`, `weaponFired`, `weaponFiring`, `shipChangeAllowed/Denied`, `PlayerEvent.playerBanned` are declared `EventType` constants but never published — `DeathSystem` calls no `EventBus.publish`, `WeaponsFireSystem` none. Sibling: `api/src/main/java/infinity/sim/Events.java` defines an orphan enum (`DEATHEVENT`, `WEAPONFIRED`) that parallels the bus surface — an early prototype that did not retire. Per ADR-0003, either wire to natural publishers (`DeathSystem` publishes `shipDestroyed`, `WeaponsFireSystem` publishes `weaponFired`) or retire both surfaces.
   - **Dead field:** `WeaponsFireSystem.energySystem` (~line 74) acquired in `initialize()` but never read.
4. **Misnamed system:** `StatsSystem` is a 75-line server-telemetry logger (fires `GameServer.logStats()` every 10s). It is **not** an ADR-0001 `*Stats` system. Trip-hazard for future contributors — rename to `ServerTelemetrySystem` or `LogStatsSystem`.
5. Registration ordering rules are documented inline in `GameServer.java:255–261, 365–368, 375–376` — three explicit dependencies. Healthy.

### E. Client — clean

- 32 `BaseAppState` classes. Zero direct ECS writes (`ed.setComponent` / `createEntity` / `removeEntity`). Two justified one-shot `ed.getComponent` sites (`Model`, `ModelContainer`); all other reads go through `ed.watchEntity`, per the rule.
- `GameSessionState` is the entry-point wiring 18 child states. 165 lines — well under the smell threshold.
- RMI surface (`api/src/main/java/infinity/net/GameSession.java`) covers move / attack / action / avatar / toggle / map. No write paths bypass it.
- `BodyPosition` is used correctly for the two cases that need it (`AvatarMovementState`, `InfinityCameraState`). No position polling.
- Release-in-`cleanup()` audit (2026-05-13) covered all 32 `BaseAppState` classes; 7 leak / risk sites found and fixed in this iteration (`InfinityCameraState`, `MobDebugState`, `AudioState`, `HudLabelState`, `MapState`, `SpeechViewState`, `PlayerListState`). Lifecycle test infra is now P1-i.

### F. Test coverage + CI signal — biggest single gap in the repo

- **58 tests** total: 12 api, 43 server, 3 client.
- **Strongest asset:** ADR-0001 fixtures. `CanonicalWriterDrainTest` (`infinity-server/src/test/java/infinity/systems/CanonicalWriterDrainTest.java:69`, 456 lines) is a generalised 4-scenario property test (one-shot / decay-bound / multi-source sum / no-op) using a synthetic `TestStat`. **18 `*ChangeDrainTest` files** pair real systems against the property test. `CanonicalWriterTest` (architecture test) enforces the one-writer rule across 24 component types.
- **Largest gap: prize appliers.** Four of 33 have direct tests (`Cloak`, `MultiFire`, `Repel`, `XRadar`). 29 appliers translating Subspace canon (REFERENCE.md mechanics: tri-state status, centisecond conversions, energy drain rates) have no test that pins the canonical behaviour. The "simplify this applier" refactor cannot be done safely.
- **Second gap: settings adapters.** Zero dedicated tests for the 13 adapter classes (`BombAdapter`, `BulletAdapter`, …, `SingleClosureAdapter`, `Validators`). They are exercised only transitively through `GroovyArenaLoaderTest` / `GroovyZoneLoaderTest`. A grammar break in `SingleClosureAdapter` passes `./gradlew build` and only shows up at gameplay launch.
- **Spawn-projection harness PRD** (`.scratch/spawn-projection-test-harness/`) is mature (ready-for-human, slice 0 done — `ShipSpawnSystemTest`, `RepelPrizeApplierTest`, `BulletFactoryTest`, `EnergySystemIntentTest`, plus slice-3 hot-reload). Not blocked, just not pulled.
- **CI signal is thin.** One job in `.github/workflows/gradle.yml`: `./gradlew build`. PMD is `ignoreFailures = true` (warning-only by design, with a 600-violation baseline and a "ratchet on touched files" rule — `pmd-on-touched-files.md`). No JaCoCo, no integration runner, no separate static-analysis gate.
- **Cheap architecture tests that would pay off** (none of these exist yet, all are easy ArchUnit rules):
  - Every `*Change` class implements `EntityComponent` and is constructed alongside a `ChangeTarget` at every emit site.
  - Every class in `infinity.systems.*` (excluding the spawn-tier exempt set) does not depend on `infinity.config..`.
  - Every class in `infinity.systems.ship.applier..` has Javadoc containing `REFERENCE.md` or `Infinity divergence`.
  - The `Section B` immutability rule turned into ArchUnit (final fields, no setters, no-arg constructor).
  - `LayerDependencyTest` Rule 3 extended with `infinity.sim..` once CubeFactory is moved or re-packaged (per finding B).

---

## Proposed prioritization

### P0 — fix soon, low effort, real bug or load-bearing leak

| # | Item | Owner suggestion | Effort |
|---|---|---|---|
| P0-c | Convert `WeaponsEligibility` per-shot config read into a per-ship component projection (`BombSafetyRadius`, `ProximityDistance`) at spawn time (`ShipSpawnSystem`). CCP (ADR-0002) leak on the hot path. **Design note:** decide live-reload semantics for the new components before landing — either `ArenaReloadWatcher` re-projects on `bomb.groovy` edits, or accept new-spawn-only semantics (consistent with the existing weapon/prize fragment story; simpler). Make the decision in the PR, not after | server / CCP | 1-2 hrs |

### P1 — high-leverage architectural ratchets

| # | Item | Why | Effort |
|---|---|---|---|
| P1-a | **Mechanised canonical-writer guard** (the `TBD-3` gap called out in `replacement-as-mutation.md`). One ArchUnit / custom test that asserts, for every component type registered in the canonical-writer registry, exactly one writer system exists. Existing `CanonicalWriterTest` is the seed | Prevents silent regression of the 0-violation state the team has invested heavily in | half day |
| P1-b | **Component immutability + no-arg-ctor ArchUnit rule** — encodes `.claude/rules/components.md` as a test | The rule is currently audited by hand; a five-line ArchUnit rule guards it forever | 1 hr |
| P1-c | **Extend `LayerDependencyTest`** with the remaining gap from the 2026-05-13 audit: add `infinity.settings..` to Rule 1 forbidden list (api/ must not depend on server-side settings impls). The `infinity.sim.internal..` extension landed with the P0-b sweep | Closes the remaining layer-rule gap | 15 min |
| P1-d | **Hot-path `infinity.config` import guard** — ArchUnit rule: no class under `infinity.systems..` imports `infinity.config..` except the spawn-tier exempt set. Encodes ADR-0002. **Design questions to settle before mechanising** (see D2): (a) how is "spawn-tier exempt" expressed — named allowlist (brittle), package convention (`infinity.systems.spawn..`?), or marker annotation (`@ConfigProjector`); (b) how are non-ship entity-spawn sites (`WeaponsFireSystem`, `PrizeSystem`) included — same predicate or separate exception; (c) is `*.EMPTY.*` sentinel-constant lookup allowed, or does `ContactSystem` need to refactor `ArenaConfig.EMPTY.wallFriction()` out (move constant to a `Defaults` class, or read off a component) | Locks in CCP once P0-c lands; the boundary decisions become the de-facto refinement of ADR-0002's exempt-set scope | 1 hr predicate + ~1 hr refactors |
| P1-e | **Pull spawn-projection harness PRD** (slices 2, 4, 5×N). Already designed; the PRD says it's ready-for-human. Closes the documented "manual launch is the only verification" risk | Memory note: this is a known scar | half-week |
| P1-f | **Prize-applier subspace-canon tests** — one per applier, pinning REFERENCE.md semantics. 29 missing; the 4 existing ones (`Cloak`, `Repel`, `MultiFire`, `XRadar`) show the cheap shape | Refactor risk: today the appliers cannot be safely simplified | 1-2 days, parallelisable |
| P1-g | **Per-adapter settings tests** — one per `*Adapter` class. Currently 0; pipeline tracker is the only enforcement | Same risk shape as P1-f but for the DSL ↔ `*Config` boundary | 1 day |
| P1-h | **Design gameplay-interface contracts in api/ before the module loader lands its first consumer** (per ADR-0004). Interfaces like `GameMode`, `ScoringRule`, `RespawnPolicy`, `RoundLifecycle`, `KillFeed` — to be defined in api/, with the core providing a default implementation and modules supplying alternates. Clean Architecture's "Use Cases as ports, frameworks at the edge" applied to gameplay extensibility. Cost paid once per gameplay aspect; benefit compounds with every module that ships against the interface (vs. the default "modules attach arbitrary systems" path, which produces a sprawling ecosystem expensive to unwind once contributors have shipped). The first module that ships is the moment to design the first interface | Asymmetric long-run payoff: locks in composable mods + a small clear API surface for community contributions before the module ecosystem ossifies | 1 day per gameplay aspect, paid lazily as motivated |
| P1-i | **Build client-lifecycle test infrastructure.** Today there are 3 client tests total; all client `BaseAppState` lifecycle correctness is verified by manual launch (per the spawn-projection-test-harness PRD's stated scar). The 7-leak sweep done in this iteration (former P0-a + P2-i) shipped without test coverage because the infra doesn't exist. A minimal harness — synthetic `Application` + `EntityData` + `AppStateManager`, fixture for `initialize() → onEnable() → onDisable() → cleanup()` cycle assertions, mock `WatchedEntity` / `EntityContainer` to verify `release()` / `stop()` calls — would catch regressions in the same shape forever | Closes the "manual launch is the only verification" gap for client-state lifecycle correctness; complements P1-e's spawn-projection harness with client-side equivalent | 2-3 days |

### P2 — cleanups, judgment calls, documentation

| # | Item | Note |
|---|---|---|
| P2-a | Remove dead `WeaponsFireSystem.energySystem` field | Refactor leftover |
| P2-b | Rename `StatsSystem` → `ServerTelemetrySystem` (or `LogStatsSystem`) | Naming trip-hazard against ADR-0001 `*Stats` |
| P2-c | **Migrate cross-system direct calls** to one of three patterns: **(A)** intent component (ADR-0001/0003 Channel A — right when the call is a mutation request that can lag a tick: `EnergySystem→PrizeSystem` death-edge → `PrizeSpawnIntent` drained by `PrizeSystem`); **(B)** Dependency Inversion via api/-defined interface (right when the call is synchronous coordination that must complete in-tick: `WeaponsImpactSystem→WeaponsReaperSystem.detonate()` → define `Detonator` interface in api/, reaper implements, impact depends on interface); **(C)** document the deliberate direct call. Removes implicit registration-order contracts; makes cross-system contracts compile-time-checked rather than reflective | Removes implicit ordering contracts; aligns the two cases with the right channel; testable against mock collaborators |
| P2-d | **Document `ContactSystem` as ADR-0003 Channel C** in its class Javadoc — listener ordering guarantees, filter semantics, dispatch timing — and document the registration order in `GameServer.java` (kill-credit before prize consumption). Per ADR-0003 Channel-C bar item #4 (listener contract documented in dispatcher Javadoc) | Order is correct today, but undocumented; makes Channel-C status explicit |
| P2-e | Document the dangling `ThorConfig` / `GravBombConfig` slots — explain the per-ship-only rationale so future authors don't add unused adapters | One-line javadoc per slot in `ConfigRegistry.SLOTS` |
| P2-f | `./gradlew clean` + `.gitignore` audit of stale `modules/build/*-1.0.17.*` artifacts | Cosmetic |
| P2-g | Operator-runbook note: "editing weapon/prize fragments applies to new spawns only; ship-stat edits re-flow via `reprojectAll`" | Memory + CONTRIBUTING.md or BUILDING.md |
| P2-h | Convert `WeaponsDamageLogic` arena/engine config reads to spawn-projected components or per-detonation context object | Lower urgency than P0-c |
| P2-j | **Audit dormant `EventType` declarations and retire the orphan `infinity.sim.Events` enum.** Either wire `ShipEvent.shipDestroyed` / `weaponFired` / etc. to natural publishers (`DeathSystem`, `WeaponsFireSystem`) or remove the declarations. Same decision on `PlayerEvent.playerBanned`. The `Events` enum (`DEATHEVENT`, `WEAPONFIRED`) parallels the bus surface — early prototype that should retire entirely (or, if kept, unify with the bus types). Per ADR-0003 open work | Closes ADR-0003's dormant-surface item; one source of truth for announcements |
| P2-k | **Split `PrizeSystem` by Use Case** — extract `PrizeSpawnerSystem` (arena prize spawning + cap-scaling), `PrizeConsumptionSystem` (prize-on-contact application via the existing applier table), and `DeathPrizeSystem` (death-edge prize drop, decoupled from `EnergySystem` per P2-c). `WeaponsFireSystem` split (eligibility / spawn / audio) is a similar candidate but lower-priority. Per D1 Use-Case-granularity finding | Per-Use-Case testability; each split is independently mockable; the canonical-writer rule per ADR-0001 stays intact (each Use Case writes a disjoint set of components) | 1-2 days |
| P2-l | **`*Spec` → `*Args` rename** in `api/src/main/java/infinity/sim/specs/`. `api.config.SpawnerSpec` (template tier, arena DSL declaration) and `api.sim.specs.SpawnerCreateSpec` (factory-call argument) both end in `Spec`; the suffix collides because `*Config` migration introduced the template-tier `*Spec` naming alongside the existing factory-arg `*Spec`. Rename the factory-arg side to `*Args` while the surface is small (18 records, mechanical import updates). Calcifies as more modules ship against the api surface | Naming hazard against ADR-0002 `*Config` records; mechanical now, expensive later | half-day |

---

## Proposed ADR candidates

The codebase has exactly **one ADR today** (`docs/adr/0001-ecs-component-model.md`). Several other architectural decisions are equally load-bearing but live as informal rules, code conventions, or CONTEXT.md prose. Formalising them as ADRs would (a) give future contributors a single linked source for the *why*, (b) make superseding decisions explicit when they happen, and (c) give the agent rule files (`.claude/rules/*.md`) a stable target to cross-link.

Candidates, in order of ratchet-leverage:

### ADR-0002 candidate — CCP: template-vs-instance config split

The split between immutable `*Config` records in `api/src/main/java/infinity/config/` (one per type, registry-owned, read at spawn) and per-entity components (one per entity, mutable, read on the hot path) is the foundational decision that makes the settings pipeline tractable. Today it's documented in `.claude/rules/config-pattern.md` and CONTEXT.md prose. The decision *and the alternatives rejected* (single-tier config; components-only; hot-path registry lookup) deserve an ADR — especially because the audit found two live Pattern-4 leaks (D2) that would have been visible at code-review time against an ADR with a clear forbidden-import list.

### ADR-0003 candidate — Three event planes

CONTEXT.md defines three event shapes (ECS transient component / arena `EventBus` event / zone `EventBus` event) and names them, with a worked example of when to pick which. This is a high-traffic decision: every cross-system communication channel is one of these three. The CONTEXT.md "Flagged ambiguities" note explicitly says "event" was an overloaded term — that resolution belongs in an ADR. Includes the open question of whether arena scoping needs runtime split into per-arena bus instances.

### ADR-0004 candidate — Settings pipeline architecture (host / adapter / registry / reload)

The four-layer pipeline — Groovy fragment file → `GroovySettingsHost<T>` (I/O + eval + security) → `GroovySettingsAdapter<T>` (DSL semantics) → `ConfigRegistry` (per-arena snapshot) → `ArenaReloadWatcher` + `ShipSpawnSystem.reprojectAll()` — was decided over multiple slices, and the resolution of host-vs-adapter (CONTEXT.md "Flagged ambiguities") is non-trivial. An ADR locks in the boundary, names the legacy loaders that are not (yet) thin facades (`GroovyShipLoader`, `GroovyArenaLoader`), and documents the live-reload coverage decision (ships re-flow; fragments apply to new spawns only).

### ADR-0005 candidate — Layer dependency model + client read-only

Two rules today: `.claude/rules/api-contracts.md` ("api is data + interfaces only; no deps on server/client") and `.claude/rules/client-read-only.md` ("client observes; server owns; writes via RMI; `BodyPosition` not polling"). Both are enforced by `LayerDependencyTest` (with the three coverage gaps in B). An ADR would name the four layers (api / server / client / future-modules), document the api-side vs server-side `infinity.sim` ambiguity, and pin the test as the mechanism. The CubeFactory leak makes this concrete — the *next* such leak should be impossible.

### ADR-0006 candidate — Tuning-knob locale (Groovy, not Java)

CLAUDE.md Rule 3 ("Tuning knobs go in Groovy, not Java") is a strong opinionated policy with three tiers (preset / arena / zone) and an explicit exception list (math identities, protocol constants). The policy interacts with CCP (Groovy populates `*Config` templates; templates project to components). The decision rationale ("easier to demote a knob back to a constant than to flush a magic number") deserves to be captured once instead of restated in every PR review. Operator-facing.

### ADR-0007 candidate — Single TTL mechanism: `Decay`

`.claude/rules/decay-ttl.md` mandates that `Decay` is the only entity-TTL mechanism and that templates project to `Decay`, not to parallel `*Time` / `*Ttl` / `*Lifetime` components. The audit found this is currently enforced (0 violations) and that two components which *look* like parallels (`Delay`, `Jitter`) serve different semantic roles. An ADR formalises the distinction (entity lifetime vs deferred action vs component-reaper) so a future author doesn't accidentally re-introduce `*Lifetime`.

### Lower-priority candidates (could be inline rules or short ADRs)

- **Replacement-as-mutation rule** — currently `.claude/rules/replacement-as-mutation.md`. ADR-0001 already references it as the rule it generalises; could be folded into ADR-0001 as a "consequence" section rather than a separate ADR.
- **PMD ratchet discipline** (`.claude/rules/pmd-on-touched-files.md`) — operational policy more than architecture. Stays a rule.
- **Javadoc discipline** (`.claude/rules/javadoc-discipline.md`) — style rule, not architecture.
- **Player-count scaling** (`.claude/rules/player-scaling.md`) — design checklist, not architecture.

### Recommended sequencing for ADRs

Pulling ADRs in this order maximises ratchet leverage: each closes a class of recurring review comment, and each gives the agent rule files a cross-link target.

1. **ADR-0002 (CCP)** — closes the two live leaks in D2 against a named source.
2. **ADR-0003 (Three event planes)** — closes the most overloaded vocabulary problem in the codebase.
3. **ADR-0005 (Layers + client read-only)** — pairs with extending `LayerDependencyTest` (P1-c) and the CubeFactory move (P0-b).
4. **ADR-0004 (Settings pipeline)** — captures three months of slice work in one document.
5. **ADR-0006 (Tuning-knob locale)** — operator-facing; least technical.
6. **ADR-0007 (Decay)** — short; cleanup.

---

## Cross-cutting observations

- **The "0 violations" findings are real, but earned by hand.** ADR-0001 conformance, immutability, EntitySet release, prize-applier discipline — all of these came back clean. Every one of them was achieved by manual auditing across multiple migration waves. Each is one PR away from quiet regression. The single highest-leverage investment from this review is **mechanising the audits as cheap ArchUnit rules** (P1-a, P1-b, P1-c, P1-d). The agents found these in minutes; tests would find them at every build.

- **The two coverage gaps that matter** (prize appliers translating REFERENCE.md; settings adapters translating Groovy DSL) sit at the **boundary between Subspace canon and Infinity code**. Both are "translation" boundaries — the kind of code that drifts silently against an external spec. Both deserve their own tier of tests that pin canonical behaviour. The spawn-projection harness PRD already exists and is ready-for-human; the analogue for prize-canon and DSL-canon would complete the test triad.

- **Naming hazards.** Three flagged: `StatsSystem` (telemetry, not ADR-0001 `*Stats`), api-side `infinity.sim` vs server-side `infinity.sim` (same FQN, different layers), and "config" pre-disambiguation between CCP templates and the settings pipeline (resolved in CONTEXT.md but not yet in code). All three are real cognitive load and one of them (the `sim` collision) is a load-bearing test gap.

- **The bus is severely underused.** Three `EventBus.publish` sites in the whole codebase (one for ship-spawn, two for account login/logout); six declared `EventType` constants never published; an orphan `infinity.sim.Events` enum paralleling the bus surface from an earlier design that did not retire. Direct `getSystem` method calls fill the announcement gap and accumulate implicit registration-order contracts (D3). ADR-0003 names the discipline; P2-c / P2-d / P2-j are the cleanup. The risk shape is the opposite of "bus spaghetti" — it's "bus disuse + direct-call spaghetti".

- **Clean Architecture lens identifies three positive-ROI divergences.** Most CA divergences (no Use Cases layer, frameworks not at the edge) are deliberate and not worth converting — ECS is the right style for real-time games, and abstracting Zay-ES behind api/ interfaces would fight the framework's grain. But three CA-flavored moves do pay off: **(a)** Dependency Inversion for cross-system collaboration via api/-defined interfaces (folded into P2-c as Option B); **(b)** designing gameplay-interface contracts in api/ before the module loader's first consumer ships (P1-h — single highest-leverage long-run move per ADR-0004); **(c)** splitting domain-coherent god systems by Use Case granularity (P2-k for PrizeSystem). The remaining CA gaps (Use Cases layer, framework abstraction at the edge) stay deliberate non-goals.

- **No major refactor needed.** This is a healthy codebase. The review surfaced ~18 concrete code-architecture items, none of which require an architectural rewrite. Most are 1-2 hour cleanups or test additions. The largest single item (P1-e, pulling the spawn-projection harness slices) is already designed; it just needs execution time.
