# ADR 0002 — Config-Component Projection: tuning templates project to ECS components at spawn time

**Status:** Proposed
**Date:** 2026-05-13
**Deciders:** Asser Fahrenholz

## Context

Gameplay tuning (ship stats, weapon parameters, prize tunables, spawn radii) needs to satisfy four constraints simultaneously:

1. **Operator-editable without rebuilding.** Numbers like `MaxEnergy=1000` are tuned by playing the game and watching the feel; rebuilding code for every nudge is a non-starter. This pulls toward storing tuning in editable files (today: Groovy fragments under `zone/conf/<preset>/`).
2. **Per-entity divergence is real gameplay.** A ship's effective `MaxEnergy` is not just the Warbird default — it is the default, plus any prize-acquired upgrades, plus any active buff modifier, minus damage caps. Two Warbirds in the same arena routinely have different effective stats. The data model has to carry "this entity's current value", not just "this ship class's default".
3. **Hot-path code reads tuning every tick.** `PlayerDriver` reads thrust and speed limits 60 times per second per ship; the energy-drain logic for Cloak reads `cloak.energyPerSecond` every tick a cloak is active. Going through a per-arena map lookup keyed on ship-type for every per-tick read is fighting the framework.
4. **The client needs to see the effective value.** A HUD energy bar shows current energy and current max. The max changes when a prize raises it. The wire format has to carry the effective per-entity value, not the class default.

Two single-tier approaches fail:

- **Templates-only** (read tuning records on the hot path each tick). Forces every consumer to do `registry.forArena(arenaId).get(ShipType).maxEnergy()` per read. Solves operator-editability and gives a clean "what are the Warbird defaults?" answer, but cannot model per-entity divergence: there is no place to store *this* Warbird's prize-bumped max. The class default is the only value the system knows.
- **Components-only** (every tuning value is a Zay-ES component). Per-entity divergence works trivially. But the "what are the defaults for ship class X" question now requires walking every entity of that class, live-reload has to push edits onto N live entities, and there is no answer for "what would a freshly-spawned Warbird have?" except by spawning one.

Subspace Infinity has been calling the resolution **"Pattern 4"** for over a year — a name carried over from the slice numbering of the original migration PRD. The name has stopped earning its keep: new contributors do not know what "Pattern 4" refers to without reading three layers of `.scratch/` history. `.claude/rules/config-pattern.md` documents the resolution as **"Template vs Instance"**, which is descriptive but generic. This ADR formalises the decision and renames it.

## Decision

**Tuning data splits into a `*Config` template tier and an ECS component tier; spawn systems are the sole seam that projects template → component.** The pattern's name is **Config-Component Projection** (CCP).

### Naming

- **Config template** — an immutable Java record in `api/src/main/java/infinity/config/` (e.g. `ShipConfig`, `BombConfig`, `PrizeWeightsConfig`). One per type, registry-owned, server-only, read at spawn / reload / admin paths.
- **ECS component** — an immutable Zay-ES `EntityComponent` in `api/src/main/java/infinity/es/` (e.g. `EnergyStats`, `Thrust`, `SpeedStats`). One per entity, per-entity-mutable across lifetime, network-synced to clients.
- **Projection** — the operation that copies effective values from a Config template into per-entity ECS components at spawn time. Spawn systems own this seam.

The three names compose into the pattern name: **Config-Component Projection**.

Names rejected: **"Prefab"** (Flecs prefabs are themselves entities inherited from via `INSTANCEOF`; Infinity's templates are records in a registry, not entities); **"Archetype"** (collides with the ECS storage-layout meaning); **"Assemblage"** (technically accurate per ECS-FAQ but obscure); **"Blueprint"** (Unreal-flavored, not Simsilica vocabulary); **"Spec"** (already taken by `SpawnerSpec` / `infinity.sim.specs`); **"Pattern 4"** (opaque without history).

### Tier shapes

**Config template** is:

- A Java `record` (or record-shaped class) in `api/src/main/java/infinity/config/`.
- Immutable — every field final, no setters, no mutating methods.
- Self-contained — composes only api/-side types (other `*Config` / `*Stats` records, primitives, `@Nullable` for "ship cannot carry this gear" semantics).
- One per type — one `ShipConfig` per `Ship` enum entry; one `BombConfig` per arena; etc.
- Server-only — clients never deserialize Config records. They see per-entity effective values via Zay-ES sync.
- Loaded by an adapter under the Settings layer (see `.claude/rules/settings-pipeline.md`) and stored in `ConfigRegistry` keyed by arena.

**ECS component** is:

- A Zay-ES `EntityComponent` in `api/src/main/java/infinity/es/`.
- Immutable per the rules in [`.claude/rules/components.md`](../../.claude/rules/components.md) (final fields, no setters, no-arg constructor for sync).
- Per-entity — one instance per entity that carries it; missing component = "ship does not have this aspect".
- Mutable across the entity's lifetime via canonical writers (see ADR-0001).
- Network-synced — clients see the effective per-entity value automatically.

### The projection seam

**Spawn systems are the only place where template values cross into the live entity world.**

- `ShipSpawnSystem` reads `ShipConfig` from `ConfigRegistry.forArena(arenaId)`, projects its fields onto a freshly-created ship entity (`EnergyStats`, `Thrust`, `SpeedStats`, `RotationStats`, `BombStats`, `BulletStats`, …).
- `ShipStatusProjector` and `ShipWeaponsProjector` are sub-projectors that own the projection of specific Config slices onto the same entity.
- Prize spawners read `PrizeWeightsConfig` to pick a prize type, then `PrizeConfig` for the spawned prize's tunables. The spawned prize entity carries components, not Config refs.
- Live-reload reconcilers (`ArenaSystem.handleShipsScriptReload()` → `ShipSpawnSystem.reprojectAll()`) re-read templates and re-project onto already-live entities. This is the one *post-spawn* path that crosses the seam, by design.

No other server system reads `*Config` types. Hot-path consumers (`PlayerDriver`, `EnergySystem` drain, `WeaponsFireSystem` projectile spawn parameters, ship-tick logic) read components only.

### Who reads what

| Code location | Reads template? | Reads components? |
|---|---|---|
| Hot-path consumers (PlayerDriver, EnergySystem tick, gravity integration) | **No** | Yes |
| Spawn systems / projectors | Yes | Writes |
| Prize appliers | No — they emit `*Change` entities, see ADR-0001 | Reads via Change-entity flow |
| Live-reload reconcilers | Yes | Writes (`reprojectAll`) |
| Admin commands (`~setstat`, `~reload`) | Yes | Writes |
| Clients (HUD, bars, UI) | No (template is server-only) | Yes, via Zay-ES sync |
| `api/` factories (`ShipFactory`, `WeaponFactory`) | No — structural composition only | Writes structural components only |

**Hot-path discipline:** server systems under `infinity-server/src/main/java/infinity/systems/` must not import `infinity.config.*` unless they are a spawn / projector / reload path. The api-side factories in `infinity.sim.*` compose only structural components and let projectors handle the tuned values (see [`api-contracts.md`](../../.claude/rules/api-contracts.md)).

### Live-reload semantics

The projection seam is one-shot at spawn, with two explicit exceptions:

- **Ships re-project on `ships.groovy` edits.** `ArenaReloadWatcher` detects mtime change, calls `ShipSpawnSystem.reprojectAll()`, which walks live ships in the arena and re-applies the Config without resetting Continuous pools (current energy, current ammo). This is the operator's primary tuning loop.
- **Weapon / prize / arena fragment edits do NOT re-flow into live entities.** Editing `bullet.groovy` updates `ConfigRegistry` but does not retroactively change projectiles already in flight, nor re-derive components on already-spawned ships from non-ship fragments. Next-spawned entities pick up the change.

This is deliberate, not an oversight. A bullet in flight that suddenly gets a new `damage` value mid-flight is worse gameplay than a one-spawn delay. A ship's `MaxBombs` should not retroactively raise the live `BombsHeld` cap. The seam is at spawn for a reason; live-reload of in-flight values is not the same operation.

### Boundary with ADR-0001

CCP is the rule for the spawn-time crossing from template to component; [ADR-0001](./0001-ecs-component-model.md) is the rule for how the resulting components are mutated thereafter. Spawn is exempt from ADR-0001's canonical-writer rule precisely because it is CCP's projection seam.

## Consequences

### Positive

- **Per-entity divergence is free.** A prize that raises `MaxEnergy` mutates the component, not the template. Two ships of the same class can have wildly different effective stats; no special-casing needed.
- **Hot path stays hot.** Tick-rate consumers read components only. No registry lookup, no per-tick map traversal, no cross-arena indirection.
- **Network sync is automatic.** Zay-ES syncs components, not templates. Clients see effective per-entity values without ever knowing Config exists.
- **Operator-editable.** Numbers live in Groovy files; live-reload re-projects onto live ships for `ships.groovy`. The "I want to tune this" workflow is `edit-Groovy → save → see effect` with no rebuild.
- **One canonical answer to "what are the defaults?"** The Config record. No walking entities to reconstruct.
- **No duplication on the hot path.** Template is shared (1 per type, ~28 records today); components are per-entity but only carry the fields the entity actually needs.
- **Clear seam.** Spawn systems are the only crossing. Search for `infinity.config` imports in `infinity-server/src/main/java/infinity/systems/` and the result should equal the spawn / projector / reload set.

### Costs (accepted, not avoided)

- **Two tiers to keep in sync.** Adding a new tuning field touches the `*Config` record, the matching component, and the projector. The settings pipeline tracker (`.scratch/settings-pipeline.md`) exists specifically to keep these aligned.
- **Spawn is a load-bearing seam.** A bug in `ShipSpawnSystem` projection silently affects every ship spawned thereafter. Mitigation: the spawn-projection test harness PRD (`.scratch/spawn-projection-test-harness/`) and `ShipSpawnSystemTest` cover this; expanding coverage is on the architectural-review punch list.
- **Live-reload coverage is partial by design.** Editing weapon/prize fragments only affects new spawns. Operators occasionally find this surprising; documented in CONTRIBUTING / runbook.
- **Hot-path-import discipline is currently audited by hand.** Until the ArchUnit guard lands, only code review and grep enforce it. Mitigation: mechanise as a static rule; current specifics tracked in the architectural-review punch list, not here.

### Neutral / deferred

- **Templates as Zay-ES entities** (Flecs-style prefabs) — not adopted. Would require representing each template as a long-lived entity and using `INSTANCEOF`-style sharing. Zay-ES does not have entity inheritance as a first-class primitive; emulating it would fight the framework's grain. The record-in-registry shape is straightforward and the projection cost is bounded.
- **Dangling `ConfigRegistry` slots.** Two records (`ThorConfig`, `GravBombConfig`) have registered slots but no fragment adapter — they fall back to `DEFAULTS` always. Intentional today (Thor is per-ship via `ships.groovy`; GravBomb has not diverged from Bomb). Documented as the third open work item below.

## Alternatives considered

- **Templates-only** (read `*Config` on the hot path each tick). Rejected — cannot model per-entity divergence (prize bumps, buff modifiers, damage caps); forces a per-tick map lookup that the framework grain does not support.
- **Components-only** (no templates; tuning is just a component you write at spawn). Rejected — loses the "what are the defaults" answer, forces live-reload to walk N entities, leaves no place for arena-global tuning that does not need to ride per-entity.
- **Templates as entities** (Flecs `INSTANCEOF` prefab style). Rejected — fights Zay-ES grain; emulating entity-inheritance with EntitySets and Parent components would be more complex than the record-in-registry shape we have.
- **Single tier with copy-on-write divergence** (one map of "current effective values" per entity per stat, no separate template). Rejected — re-invents the component tier with weaker tooling.
- **"Prefab" / "Archetype" naming.** Rejected on terminology-collision grounds; see Naming above.

## Resolved decisions

- **Pattern name:** Config-Component Projection (CCP). Tiers: Config template, ECS component. Operation: projection.
- **Templates:** immutable Java records in `api/src/main/java/infinity/config/`.
- **Components:** Zay-ES `EntityComponent` in `api/src/main/java/infinity/es/`.
- **Projection seam:** spawn systems + `reprojectAll` for ships-tier live-reload only.
- **Hot-path rule:** no `infinity.config.*` imports from `infinity.systems..` except the spawn-tier exempt set.
- **Storage:** per-arena `ConfigRegistry` owned by `ConfigRegistrySystem`.
- **Client visibility:** components only, never Config.

## Open work

The decision is fully described above. What remains for the decision to be *enforced* rather than *audited by hand*:

- **Rename the rule file.** `.claude/rules/config-pattern.md` → `.claude/rules/config-component-projection.md`. The file's header has been updated to reference this ADR but the filename rename is deferred (cascades to many cross-references).

## References

- [`.claude/rules/config-pattern.md`](../../.claude/rules/config-pattern.md) — the rule this ADR formalises (to be renamed; see Open work).
- [`.claude/rules/api-contracts.md`](../../.claude/rules/api-contracts.md) — api/-layer purity; factories in `infinity.sim.*` compose structural components only and let projectors handle tuned values.
- [`.claude/rules/settings-pipeline.md`](../../.claude/rules/settings-pipeline.md) — the Groovy → `*Config` loading pipeline that feeds CCP's templates.
- [`docs/adr/0001-ecs-component-model.md`](./0001-ecs-component-model.md) — Continuous + Stats components are component-tier; ADR-0001 exempts spawn from the canonical-writer rule because spawn is CCP's projection seam.
- [`CLAUDE.md`](../../CLAUDE.md) Rule 3 — tuning knobs in Groovy, not Java. CCP is the mechanism that gets those Groovy-authored values into the live entity world.
- `ShipConfig` / `ShipStat` in `api/src/main/java/infinity/config/` — canonical shape of a Config template.
- `EnergyStats`, `SpeedStats`, `Thrust` in `api/src/main/java/infinity/es/ship/` — canonical shape of a projected component.
- Sander Mertens, "Building an ECS #1: Types, Hierarchies and Prefabs" (Flecs author) — establishes the ECS-community meaning of "prefab" as an entity-shaped template, which is why this ADR does not adopt that term.
- Flecs ECS-FAQ — defines "archetype" as component-composition layout, reinforcing the terminology-collision rejection of that name.
- Wikipedia ECS article — defines "assemblage" as "a template or recipe for building entities"; technically accurate but rejected as obscure.
