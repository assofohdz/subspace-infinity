# ADR 0001 — ECS component model: Continuous + Stats with Change-entity mutation

**Status:** Accepted
**Date:** 2026-05-11
**Deciders:** Asser Fahrenholz

## Context

Ship state in Subspace Infinity is represented as Zay-ES components. Over time, two recurring tensions have shaped how those components are written:

1. **Multi-writer collisions.** Several systems write the same component types — `Health`, `Energy`, `Frequency`, `ThorFireDelay`, the cap fields (`Speed`, `Thrust`, `Recharge`, …). Last-writer-wins is order-dependent and races (rocket cap-swap vs natural cap-drift, prize applier vs spawn projection, status family vs ship-type swap). The `replacement-as-mutation` pattern was introduced to discipline this, but each new violation pulled in a new bespoke shape (per-applier intents, the `Intent` + `CapBump` + `CapField` dispatch wrapper, ad-hoc per-component drains).

2. **Two fundamentally different write rhythms.** Some component fields tick every frame (Health regenerates, Energy drains while Cloak is active, Speed integrates over thrust). Others change rarely and event-driven only (max energy, recharge rate, turn rate, prize-acquired weapon level). Treating these the same on the wire and in storage costs us either:
   - Stat-style fields needlessly re-syncing every tick (waste), or
   - Hot fields gated behind event-style change paths (latency / friction).

The C2a migration (commit `1fb108d4`) shipped a universal `Intent(target, kind, payload)` wrapper with a `CapField` enum dispatch table. It works, and proves the one-canonical-writer rule is achievable. But the dispatch-table approach scales by reflection / registration rather than by the ECS framework's natural grain (component types as identity).

This ADR proposes the model the rest of the ship-state work should snap to.

## Decision

**Ship state splits into two component flavours, and all mutation flows through transient Change entities drained by canonical writer systems.**

### The two flavours

For each gameplay aspect (energy, speed, weapons, …) a ship may carry up to two components:

- **Continuous** — the live, frame-rate-touched scalar.
  Examples: `Energy`, `Speed`, `Health` (Energy and Health are the same mechanic; either name is the canonical one — chosen elsewhere).
  Read on the hot path every tick (HUD, drain logic, physics integration).

- **Stats** — the slowly-changing parameters that govern how the Continuous half behaves.
  Examples: `EnergyStats` (max, recharge rate), `SpeedStats` (max, turn rate, thrust ceiling), `ThorStats` (current count, max held, cooldown).
  Read on the hot path but rarely written. Conceptually the "rules" the Continuous half plays by.

**The split is about write frequency, not semantics.** Continuous lives in its own component so its tick-by-tick churn does not force Stats fields to re-sync on the wire. When *every* field of an aspect is cold (Thor: count, max, cooldown — none change per tick), there is no continuous half to split off; the "current count" lives inside `ThorStats` alongside the caps because co-locating cold fields costs nothing. Conversely, a single `Energy { current, max, recharge }` would be wrong because `current` ticks every frame and would drag `max` / `recharge` through the sync delta every frame.

Either half may therefore be absent. Most ship aspects have both (Energy + EnergyStats, Speed + SpeedStats). Discrete-only aspects collapse to one (`ThorStats`, prize-acquired weapon level slots). A purely cosmetic field might have neither.

`*Stats` replaces what we informally called "caps" or "config" at the component tier. It is **not** the same thing as `*Config` records in `api/src/main/java/infinity/config/**` — those are template-tier, registry-owned, read at spawn. `*Stats` is component-tier, per-entity, mutable.

### How mutation flows

For each pair, a Change variant exists:

- `EnergyChange` (paired with `Energy`)
- `EnergyStatsChange` (paired with `EnergyStats`)
- `SpeedChange` / `SpeedStatsChange`
- `ThorStatsChange` (no continuous twin)
- …

A mutation is not applied directly. Instead, any system wishing to change a ship's energy creates a **transient Change entity** carrying two components:

- `ChangeTarget(EntityId target, EntityId source)` — which entity to mutate, and which entity is causing the change. `source` is required; when a ship buffs/changes itself, `source == target`. Useful for audit ("this energy drain came from the cloak component on ship X"), for self-cancellation (a later change from the same source replaces the previous one), and for visual / audio attribution (damage numbers point back at the attacker).
- A specific `*Change` component — what to apply (delta, replacement, multiplier — shape decided per `*Change` type).

**Per-component canonical writer.** One system per component type. `EnergySystem` is the only writer of `Energy`; it reads `EntitySet(EnergyChange.class, ChangeTarget.class)`. `EnergyStatsSystem` is the only writer of `EnergyStats`; it reads `EntitySet(EnergyStatsChange.class, ChangeTarget.class)`. Multiple systems are free to *create* `EnergyChange` entities (any number of damage sources, recharge, drain, prize pickup) — only the canonical writer applies them. This is the **single discipline this ADR enforces**, restated for clarity:

> Many writers to the transient `*Change` entity. Exactly one writer to the long-lived `*` / `*Stats` component.

Because Zay-ES filters EntitySets by component type natively, no dispatch table or discriminator field is needed — the framework's grain is the dispatch. `EnergyChange` and `SpeedChange` are different types; they land in different EntitySets; they wake different systems.

This formalizes and generalises the existing [`Buff`](../../api/src/main/java/infinity/es/Buff.java) component, which already carries `(target, startTime)` and is paired with payload components like `HealthChange`. `ChangeTarget` absorbs `Buff`; the model extends the same shape to every ship aspect.

### One-shot vs temporary changes

Change entities come in two lifetimes, distinguished by whether they carry a [`Decay`](../../.claude/rules/decay-ttl.md) component. **Decay presence is the marker** — no parallel flag, no separate type tree, no marker component.

**One-shot changes** (no `Decay`) — a permanent mutation of the target:
- Examples: prize pickup raises max energy by +50; damage drains current energy by 30; recharge tops energy by +1 this tick.
- The writer reads them on the `addedEntities` signal of its EntitySet, applies the sum of their deltas to the target component, and **destroys the Change entities itself** after applying.
- After application the effect is part of the persisted component state forever (until another change moves it again).

**Temporary changes** (with `Decay`) — a buff or debuff with a duration:
- Examples: a ship slowed to half speed by an enemy effect for 2 seconds; a shield raising max-energy by +100 for 5 seconds; multiple stacked debuffs from different sources.
- The writer reads them on the `addedEntities` signal, applies their delta to the target component, and **leaves the entities alive**. The standard `Decay` reaper destroys them when their deadline passes; no parallel TTL machinery.
- When `Decay` reaps a temporary, the writer sees it on the `removedEntities` signal of its EntitySet (Zay-ES preserves the entity's component values across the removal). The writer **reads the delta off the removed entity and reverses it** on the target component.
- Net effect: while a temporary lives, its delta is part of the target's component value; when it expires, the delta unwinds cleanly.

**No baseline storage needed.** The Change entity itself is the record of "what was applied" for the duration of its life. The writer doesn't track baselines or sums externally; it applies on add, reverses on remove. The target component (`*Stats` / Continuous) always reflects the current effective value.

**The `Decay`-presence rule lets the writer distinguish removals it caused from removals the reaper caused:**

- One-shot removal: the entity had no `Decay`; the writer destroyed it itself after applying; do not reverse (already accounted for in the application step).
- Temporary removal: the entity had `Decay`; the reaper destroyed it; reverse the delta.

This gives a clean four-line state machine:

```
on EnergyChange added:
    target.energy += change.delta
    if change.entity has no Decay:
        destroy change.entity

on EnergyChange removed:
    if removed.entity had Decay:
        target.energy -= change.delta
    # else: we destroyed it ourselves, nothing to do
```

Multiple sources slowing the same ship is the trivial case: each emits its own `SpeedStatsChange` Change entity with its own `Decay`. Each shows up as added → writer subtracts from `SpeedStats.max`. Each expires independently → writer adds back. The component value at any tick is `(persisted value) + (sum of currently-applied temporary deltas)`, maintained by the add/remove signals without any separate bookkeeping.

### Consequences for existing code

- The universal `Intent(target, kind, payload)` + `CapBump` + `CapField` introduced in C2a is **superseded** by this model. Migration sequence is a separate PRD; the wrapper persists meanwhile.
- The `replacement-as-mutation` rule (`.claude/rules/replacement-as-mutation.md`) gets restated in terms of Change entities, not in terms of writer-of-the-component. Same intent, cleaner mechanism.
- The existing `Buff` component (`api/src/main/java/infinity/es/Buff.java`) is absorbed by `ChangeTarget(target, source)`. Existing payloads like `HealthChange` slot into the model as-is. Distinguishing temporary from permanent changes is the `Decay`-presence rule, not a separate marker.
- Spawn-time projection (`ShipSpawnSystem`, `ShipWeaponsProjector`, …) continues to write `*Stats` directly at entity creation — the canonical-writer rule applies to **post-creation** mutation. Spawn is the construction phase, not a mutation; treating it otherwise forces every spawned ship to round-trip through a `*Change` entity for no benefit.

## Consequences

### Positive

- **One writer per component type, enforced by framework grain.** No reflection, no enum, no central dispatch. Zay-ES EntitySets are the dispatch.
- **Multi-writer becomes a feature, not a hazard.** Many systems can emit `EnergyChange` against the same ship in the same tick; the canonical writer **sums** them. Damage from N sources, recharge, drain — all stack additively without coordination between emitters. This is the core motivation, not a tradeoff.
- **Temporary buffs are first-class.** A `*Change` entity with `Decay` is a buff. Multiple sources slow the same ship for overlapping durations: each emits its own Change entity with its own Decay; the writer sums their deltas while alive; the standard decay reaper handles cleanup. No buff stack, no expiry list, no parallel TTL machinery.
- **Hot path stays hot.** Continuous components are read directly every tick by their consumers (PlayerDriver, HUD via Zay-ES sync). Reading is never gated through Change entities; the writer publishes the effective value into the stable `*Stats` / `*Continuous` components.
- **Stats sync to the client only when they actually change.** SimEthereal / Zay-ES sync semantics already do this — by separating Stats from Continuous, stat-style fields stop riding the continuous-sync delta every frame.
- **Net component-count reduction.** `*Stats` collapses many of today's singular per-aspect components (`MaxEnergy`, `RechargeRate`, `MaxSpeed`, `TurnRate`, …) into a handful of bundled records. Two-to-four types per aspect is fewer than today's scattering.
- **New gameplay aspects follow the same template** — a new mechanic adds at most four types (`Foo`, `FooStats`, `FooChange`, `FooStatsChange`) and one canonical writer. The shape is rehearsed.
- **Multi-writer races become impossible by construction.** A `Frequency` change can only happen via a `FrequencyChange` entity; only `FrequencySystem` drains them; ordering is folded inside that one writer.
- **Debugger / log-tap is uniform.** Every mutation is a Change entity, visible in entity dumps. "Who tried to change X and when" is a query.

### Costs (accepted, not avoided)

- **Next-tick visibility is intentional, not a defect.** A system that emits `EnergyChange(-5)` does not see the new Energy until the canonical writer runs. Cross-system code that needs "drain then check" in the same tick either depends on a known phase order or reads the Change entity it just emitted. Both are explicit; both are local.
- **Allocation per mutation.** Each mutation = one entity + two components + (eventually) destroy. For Continuous components like Energy this is a per-tick cost. The Continuous boundary exists regardless of design (something has to mutate Energy every tick); routing it through a Change entity adds the entity overhead but enables the multi-writer summing that motivates the whole model. Accept the cost; optimise later only if profiles say so.
- **Migration from the C2a `Intent` wrapper is real work.** ~10 prize appliers + the cap-bump drain in `ShipSpawnSystem` currently go through `Intent` + `CapBump` + `CapField`; they need to be rewritten to emit `*Change` entities and have the cap-bump drain split per stat type. PRD will scope this.

### Neutral / deferred

- **Scope beyond ship.** The ADR is written in terms of ship state because that's where the multi-writer collisions live. The same shape extends naturally to bombs, prizes, doors, asteroids — but extending it is not part of this decision. Apply where motivated, leave alone where not.
- **Same-tick visibility.** Whether the canonical writer drains before or after consumers each tick is a phase-ordering question handled per-writer. Pattern is "drain at the start of the writer's tick"; ordering between writer and consumer is decided when two specific systems collide.

## Alternatives considered

- **Direct mutation (status quo before RaM)** — any system writes any component. Rejected: race-prone, live bugs already in flight.
- **Universal `Intent(target, kind, payload)` wrapper (C2a shape)** — one Intent type, central discriminator dispatch. Rejected: fights ECS grain, central choke point, loses compile-time type safety on payload. Survives as transitional shape until migration lands.
- **Mutating-component-on-the-target (pre-RaM intent shape)** — pending buffs attached to the target entity itself. Rejected: conflates target state with mutation state, leaks into the target's component lifecycle, accumulates N components on the same entity.
- **Generic `ChangeFlushSystem` with registered handlers** — single system, reflection / registry dispatch over all `*Change` types. Rejected in favour of per-component writers: less mechanical clarity, hides "who writes X?" behind a registration table.
- **Collapsed system per aspect (`EnergySystem` writes both `Energy` and `EnergyStats`)** — locality over rule. Rejected: judgment-call boundary ("are these coupled enough?") drifts across aspects; one-per-component keeps the grep answer mechanical.

## Resolved decisions

- **Writer topology:** per-component canonical writer. One system per component type — `EnergySystem` writes `Energy`, `EnergyStatsSystem` writes `EnergyStats`. Many systems may *create* `*Change` entities; only the canonical writer applies them. Rationale: "who writes X?" has one grep answer by rule, the pattern scales without judgment ("are these two coupled enough to fold?"), and cross-stat coordination works through the same mechanism as any two systems — e.g. `EnergyStatsSystem` lowering `max` from 1000 to 500 below the current `Energy` of 800 emits an `EnergyChange(-300)` to clamp, rather than touching `Energy` directly.
- **Conflict resolution policy:** fold-additive. Multiple `*Change` entities targeting the same component sum.
- **One-shot vs temporary marker:** presence/absence of `Decay`. No additional flag.
- **Baseline storage:** none. Writer uses EntitySet `addedEntities` / `removedEntities` signals — apply on add, reverse on remove for `Decay`-bound entities; apply-then-destroy for one-shot. Target component holds the current effective value at all times.
- **Provenance / source field:** `ChangeTarget` carries `(target, source)` as required fields. `source == target` is valid for self-changes.
- **`Buff` absorption:** `Buff` is replaced by `ChangeTarget`. Existing payloads like `HealthChange` slot into the model unchanged.
- **Allocation:** accepted as cost-of-doing-business; the Continuous boundary exists with or without this design.
- **Cross-tick visibility:** next tick is intentional, not a problem to solve.

## Open work (PRD-scope)

- **Migration tracker.** A todo-list living in the PRD enumerates each component-type / writer migration: existing `*` types that need a `*Change` + canonical writer, existing direct-write sites that need to emit Change entities instead, `Intent` / `CapBump` call sites to rewrite. Maintained in the same file as the PRD; each row flips ✅ when its writer is landed and the existing direct-write sites are converted.
- **Scope-of-pattern policy.** Ship state is the motivating case; whether to extend the pattern to bombs / prizes / doors / asteroids in the same PRD or as a follow-on is a PRD decision.

The ADR records the **decision shape**; the PRD records **how we land it**.

## References

- `.claude/rules/replacement-as-mutation.md` — the existing one-writer rule this ADR generalises.
- `.claude/rules/config-pattern.md` — template (`*Config`) vs instance (component) tier; `*Stats` is component-tier and distinct from `*Config`.
- C2a migration commit `1fb108d4` — the universal `Intent` wrapper this ADR replaces. Prior PRDs (`.scratch/replacement-as-mutation/`, `.scratch/universal-flush-system/`) were removed when this ADR landed; their content is captured in the Context + Alternatives sections above.
