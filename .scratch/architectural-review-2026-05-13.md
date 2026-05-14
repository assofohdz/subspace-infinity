# Architectural Review — 2026-05-13

**Status (2026-05-14):** audit closed. All P0 / P1 (except P1-h, deferred) / P2 items shipped across commits `3cbe191a`, `09295c8b`, `71cc91b7`, `47c5ea13`, `74e8722b`, `ff760003`, `fbf5abc8`, `3c5e870a`. New findings surfaced during execution are tracked in [§ Follow-ups](#follow-ups-from-execution) below.

**Method:** six specialist agents in parallel (ECS compliance / layer boundaries / settings pipeline / server cohesion / client lifecycle / test coverage), synthesised by hand. Read-only audit; no edits performed.

**Headline:** the codebase is in good architectural shape. ADR-0001 is enforced almost everywhere it claims to be, layer boundaries are clean save for one concrete leak, the settings pipeline is well-bounded, the System/Logic split is coherent across the server, and the client correctly observes-only. The largest gap is **test coverage of two boundaries that are intentionally manual today** (prize appliers translating Subspace canon; settings adapters translating Groovy DSL). The second-largest gap is **mechanised architecture enforcement** — most of the discipline lives in `.claude/rules/*.md` and is audited by hand, not by tests.

---

## Findings, grouped

### A. ECS model (ADR-0001) — strong

| | Result |
|---|---|
| Component immutability (final fields, no setters, no-arg ctor) | **0 violations** across `api/src/main/java/infinity/es/**` — now guarded by `ComponentImmutabilityTest` (P1-b) |
| `EntitySet` lifecycle (released in `terminate()` / `cleanup()`) | **0 leaks** on server; 1 client leak was the 2026-05-13 sweep (closed) — now covered by `BaseAppStateLifecycleHarness` (P1-i) |
| Canonical-writer rule (one writer per component) | **0 violations** — guarded by `CanonicalWriterTest` across 32 component types |
| Decay-TTL parallelism | **0 violations** — `Decay` is the sole entity-lifetime mechanism. `Delay` and `Jitter` are distinct concepts (deferred-action; component-reaper) |
| Hot-path config-import discipline | **0 violations** post-P2-h (WeaponsDamageLogic refactor) — guarded by `LayerDependencyTest` |

### B. Layer boundaries — clean

- `modules/` source-level excised (v1.0.17); stale build artifacts removed (P2-f).
- `api/` clean: zero imports of `infinity.client.*`, `infinity.systems.*`, `infinity.server.*`, `infinity.settings.*`. `LayerDependencyTest` Rule 1 enforces.
- Server clean: zero imports of `infinity.client.*`.
- ADR-0005's `infinity.sim.internal..` relocation done. `LayerDependencyTest` Rule 3 forbids client access.

### C. Settings pipeline — well-architected

- `GroovySettingsHost<T>` + `GroovySettingsAdapter<T>` refactor landed for 12 fragment adapters. Two legacy `Groovy*Loader` classes remain (`GroovyShipLoader`, `GroovyArenaLoader`) — both intentional. Two boot-time loaders (`GroovyEngineLoader`, `GroovyZoneLoader`) are outside the per-arena flow by design.
- `ConfigRegistry` ownership clean: one `ConfigRegistrySystem` per server, `ConcurrentHashMap<arenaId, ConfigRegistry>`. Arena lookups via `forArena()` with `EMPTY` fallback.
- 18/18 `*Config` records wired through the loader → registry → projection chain. `ThorConfig` + `GravBombConfig` slots are explicitly documented as per-ship / not-yet-diverged (P2-e).
- 33/33 prize appliers test-pinned (P1-f) against REFERENCE.md canonical Subspace semantics; 8 stub appliers explicitly throw `UnsupportedOperationException`.
- Live-reload coverage documented in `BUILDING.md` operator-runbook section (P2-g): ship-stat fragments re-flow via `reprojectAll`; weapon/prize fragments apply to next-spawn only.

### D. Server cohesion — split landed

- `PrizeSystem` (805 LOC) split into `PrizeSpawnerSystem` + `PrizeConsumptionSystem` + `DeathPrizeSystem` (P2-k).
- `WeaponsFireSystem` (546 LOC) split into `WeaponsFireEligibilitySystem` + `WeaponsProjectileSpawnSystem` + `WeaponsFireAudioSystem` (P2-k).
- `ConsumableSystem` left single-Use-Case as the audit recommended.
- Channel discipline (ADR-0003): Channel A (intent components) used for EnergySystem→DeathPrizeSystem death-edge + eligibility→spawn/audio. Channel B (`Detonator` interface) for WeaponsImpact→Reaper. Channel C (`ContactSystem`) documented (P2-d).
- Dormant bus surface retired (P2-j): 6 unpublished `EventType` constants deleted + orphan `infinity.sim.Events` enum file removed.
- `StatsSystem` renamed to `ServerTelemetrySystem` and registered (was dead code).

### E. Client — clean

- 32 `BaseAppState` classes. Zero direct ECS writes. `BodyPosition` used correctly for the two cases that need it.
- `BaseAppStateLifecycleHarness` (P1-i) + 3 sample tests landed; 4 remaining leak-fixed states need a `GuiGlobals` test fixture (follow-up).

### F. Test coverage + CI signal

- **~210 tests** (was 58 at audit time): 12 api, ~178 server, ~17 client. Most growth from P1-f (27 prize-applier tests) + P1-g (13 adapter tests) + P1-i (lifecycle harness + 7 sample tests) + P1-e (spawn-projection slices 2 + 4).
- ArchUnit guards live: component immutability (P1-b), `LayerDependencyTest` Rules 1/3, `CanonicalWriterTest` 32-type registry, hot-path `infinity.config` import guard (P1-d).
- Spawn-projection harness PRD: slices 1, 1b, 1c, 1d, 2, 3, 4 ✅. Slice 5×N (Pattern-4 cluster carbon-copies) remains as new CCP migrations land.
- Client-lifecycle harness: `BaseAppStateLifecycleHarness` + `SyntheticApplication` + `RecordingEntityFixtures` + `GuiGlobalsTestFixture` covers all 7 leak-fixed `BaseAppState` classes (`InfinityCameraState`, `MobDebugState`, `AudioState`, `HudLabelState`, `MapState`, `SpeechViewState`, `PlayerListState`).
- **JaCoCo coverage gate wired** (commit `01cae8f3`): line + branch ratchet in `gradle.properties` per module; `./gradlew check` blocks regression. SonarCloud coverage panel now populated (was 0% — no reports were being generated).
- Baselines as of 2026-05-14: api 16.59% line / 22.50% branch; infinity-server 31.20% / 26.05%; infinity-client 5.82% / 2.65% (post-P1-i finish).
- PMD ratchet 0/0/0/0 across modules; Checkstyle ratchets dropped sharply post-sweep (api 12→2, server-main 79→55, client-main 27→21).

---

## Remaining / open

| Item | Status | Note |
|---|---|---|
| **P1-h** — Gameplay-interface contracts in api/ (`GameMode`, `ScoringRule`, `RespawnPolicy`, `RoundLifecycle`, `KillFeed`) | **Deferred** | Per `create-module` skill: module loader is paused; no live consumer for the API yet. Design when first module ships. |
| **Spawn-projection harness Slice 5×N** | Open | One slice per Pattern-4 CCP migration as those land. PRD ready. |

---

## Follow-ups from execution

Items surfaced during P1/P2 execution, not part of the original audit:

- **Asymmetric `ContactSystem` listener lifecycle (latent leak).** `WarpSystem` + `GravitySystem` call `addListener` but never `removeListener` in `terminate()` — risk if `ContactSystem` outlives them or the systems are re-registered. Other five contact-listener systems handle this correctly. *Source: Delta sweep, ContactSystem doc agent.*

- **Kill-credit attribution gap.** `EnergySystem` death-edge emits `ChangeTarget.self(target)` (matching legacy behaviour) — the `source` slot for killer attribution is unset. Could be threaded by inspecting `EnergyChange` siblings carrying a `DamageSource`. *Source: Alpha PrizeSystem-split agent.*

- **Three inconsistent `ContactSystem` lookup idioms** across the seven listener-registering systems: `getSystem(ContactSystem.class)`, `getSystem(ContactSystem.class, true)`, `requireSystem(ContactSystem.class)`. Cosmetic but worth unifying. *Source: Delta.*

- **`PlayerEvent` class is empty** after P2-j retired `playerBanned`. Class file kept; decide whether to delete entirely or wait for a new player event. *Source: Epsilon event-audit agent.*

- **`DefaultColumnDb` real concurrency bugs** (not just style TODOs): hard-sync write bottleneck (every `writeColumn` serialises through a class-wide lock) + DataVersion read-after-write race. Promoted to `.scratch/code-todos-backlog.md` with full context. *Source: TODO triage agent.*

- **AI files perf TODOs** in `BrainConfigurations` / `MobDriver` / `MobSystem` (brute-force spatial scans from the Simsilica demo origin). Promoted to backlog; not blocking. *Source: TODO triage agent.*

- **Checkstyle remaining 78 violations** (post-sweep): 52 `RedundantModifierCheck` + 25 `HiddenFieldCheck` + 1 `ArrayTypeStyleCheck`. All deferred per-site-judgment rules. PMD remains 0/0/0/0.

- **Sonar long-tail** clusters that need refactor-scale work: `S6548` Singleton review (~14), `S107` >7-params (~13), `S135` multi-break/continue (~11). All explicitly deferred from previous tier triage.

---

## Cross-cutting observations (durable)

- **The "0 violations" findings are real, but earned by hand.** ADR-0001 conformance, immutability, EntitySet release, prize-applier discipline — all clean. Each was achieved by manual auditing across multiple migration waves. Each was one PR away from quiet regression until the P1-a/b/c/d ArchUnit guards landed. **Lesson:** mechanise audits as cheap ArchUnit rules when they catch a class of bug worth catching.

- **The two coverage gaps that matter** (prize appliers translating REFERENCE.md; settings adapters translating Groovy DSL) sit at the **boundary between Subspace canon and Infinity code**. Both are "translation" boundaries — the kind of code that drifts silently against an external spec. Both now have dedicated tier-tests pinning canonical behaviour (P1-f, P1-g).

- **Naming hazards** flagged in the audit were three: `StatsSystem` (telemetry, not ADR-0001 `*Stats`), api-side `infinity.sim` vs server-side `infinity.sim`, and "config" pre-disambiguation between CCP templates and the settings pipeline. The first is resolved (P2-b rename). The second is a layer-test gap; the third is documented in CONTEXT.md.

- **The bus was severely underused.** Three `EventBus.publish` sites in the whole codebase pre-P2-j; six declared `EventType` constants never published; an orphan `infinity.sim.Events` enum paralleling the bus surface. Post-P2-j, the bus surface is lean: orphans retired, `shipSpawned` is the canonical live event, and any new event must ship with a publisher in the same PR.

- **Clean Architecture lens identified three positive-ROI divergences.** (a) Dependency Inversion via api/-defined interfaces for cross-system collaboration (P2-c Channel B — landed: `Detonator`). (b) Designing gameplay-interface contracts in api/ before module loader (P1-h — deferred until module loader returns). (c) Splitting domain-coherent god systems by Use Case granularity (P2-k — landed: PrizeSystem + WeaponsFireSystem).

- **No major refactor needed.** The audit surfaced ~18 concrete items, none requiring architectural rewrite. Almost all closed in a single multi-day pass. The single deferred item (P1-h) is lazy by design.
