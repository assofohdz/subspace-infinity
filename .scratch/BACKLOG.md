# Refactor backlog

Bag of non-settings cleanup items — architecture splits, library audits, naming notes. Pull from this file when "what's next?" comes up for cleanup work that doesn't belong in the settings pipeline.

**Subspace-settings wiring lives in the pipeline tracker + work queue, not here:**
- [`settings-pipeline.md`](settings-pipeline.md) — what's wired, gate-by-gate
- [`settings-pipeline-slices.md`](settings-pipeline-slices.md) — what to work on next

## How to use this list

When the user asks "what's next?" or starts a new session asking about cleanup / refactoring opportunities, surface relevant items from this file. For Subspace-settings wiring, defer to the pipeline tracker + slices file.

**Keep this file in sync** — when an item lands, **delete** it from this file in the same commit. Do not strikethrough; crossed-out content is context-window clutter for future sessions.

**Not in this file** — Subspace-settings wiring lives in [`settings-pipeline.md`](settings-pipeline.md) + [`settings-pipeline-slices.md`](settings-pipeline-slices.md); pick from the queue file when settings work is the focus. Spawn-projection harness expansion lives in [`spawn-projection-test-harness/PRD.md`](spawn-projection-test-harness/PRD.md). RaM-pattern follow-ons (residual C2 sub-slices) will be replanned under [ADR 0001](../docs/adr/0001-ecs-component-model.md) when its implementation PRD lands.

## ADRs

ADR = one architectural decision, captured shape + alternatives + consequences. Lives in [`docs/adr/`](../docs/adr/). PRDs (see next section) consume ADRs and add slice plans, todo trackers, migration gates. Rules in [`.claude/rules/`](../.claude/rules/) are the **enforcement** of decisions ADRs **record**.

When an ADR lands, the matching rule should reference it; the rule remains the path-scoped contract for tools that auto-load on file match.

### Filed

- [`0001-ecs-component-model.md`](../docs/adr/0001-ecs-component-model.md) — Continuous + Stats with Change-entity mutation. **Accepted** 2026-05-11. Supersedes universal `Intent` wrapper as the foundation.

### Candidates (not yet written, ordered by foundational weight)

Most candidates are "promote existing rule" — the decision is enforced in code or a rule, but the rationale, alternatives, and consequences were never recorded as a standalone ADR. A few are "capture decision" where the decision is implicit in code only.

1. **API / server / client layering** *(promote rule)*. What `api/` is for, why the wall exists, what `infinity.sim.*` ABI is and isn't. Rule: [`api-contracts.md`](../.claude/rules/api-contracts.md). Enforced by [`LayerDependencyTest`](../infinity-client/src/test/java/infinity/architecture/LayerDependencyTest.java).

2. **Component discipline: immutability + no-arg ctor + serializer registration** *(promote rule)*. Why components are records, why the no-arg ctor exists, registration policy for wire-crossing types, failure mode. Rule: [`components.md`](../.claude/rules/components.md).

3. **Decay as the only TTL mechanism** *(promote rule)*. Duration on templates, deadlines on components, single reaper, no parallel `*Decay` / `*Ttl` markers. Rule: [`decay-ttl.md`](../.claude/rules/decay-ttl.md).

4. **Config tier: template (`*Config` records) vs instance (components); spawn projection at the boundary** *(promote rule)*. Pattern 4 formalised — who reads what, why both exist, what doesn't belong in this pattern. Rule: [`config-pattern.md`](../.claude/rules/config-pattern.md).

5. **Settings pipeline: Groovy → adapter → `*Config` → consumer (+ spawn projection)** *(promote rule + capture decision)*. Five-gate model, why operator-facing Groovy uses Subspace-canonical keys, where unit conversion happens, REFERENCE.md as source of truth. Rules: [`settings-pipeline.md`](../.claude/rules/settings-pipeline.md), [`prize-applier.md`](../.claude/rules/prize-applier.md).

6. **Server-authoritative client; commands via RMI; observation via `BodyPosition` + Zay-ES sync** *(promote rule)*. Why client never writes ECS components, why RMI for commands and not state mutation, why polling is forbidden. Rule: [`client-read-only.md`](../.claude/rules/client-read-only.md).

7. **World coordinates: `TileId` API and `GRID_CELL_SIZE` source of truth** *(promote rule)*. Why pixel maths goes through `TileId`, why magic `* 1024` is banned, what the single constant guards against. Rule: [`world-coordinates.md`](../.claude/rules/world-coordinates.md).

8. **Module-facing entity-construction ABI: factories in `api/sim/`** *(promote rule)*. What constitutes the module ABI, why factories are an exception to "data + interfaces only," signature stability contract. Rule: [`api-contracts.md`](../.claude/rules/api-contracts.md) (sub-section).

9. **Phased tick model** *(capture decision)*. When systems run within a tick, what's visible to whom, ordering guarantees, where the Change-entity drain phase sits (depends on ADR 0001). No existing rule — decision is implicit in `update()` call order and ad-hoc system-graph documentation.

10. **EntitySet lifecycle: declare in `initialize()`, release in `terminate()`** *(promote rule)*. Leak failure mode, why EntitySets are stateful, what the audit subagent looks for. Rule: [`entity-sets.md`](../.claude/rules/entity-sets.md).

11. **Wire compatibility / component-shape migration policy** *(capture decision)*. When component shapes can change without coordination, when they need staged rollout, how serializer registration interacts with hot-reload. No existing rule — currently negotiated per change.

### How to work through this

Pick a candidate when "what's next?" surfaces and the slot is bigger than a slice but smaller than a feature. Typical ADR = 30–90 minutes: a draft, one grill loop with the user on alternatives, commit. Then either pair the ADR with a tiny rule-update PR (ADR + rule cross-reference) or leave the rule unchanged and let the ADR be the rationale anchor it points to.

Candidates 1–4 are the highest-value formalizations — they show up daily in agent briefs and PR reviews; making the decisions citable rather than oral-tradition pays off the fastest.

## PRDs

PRD = build plan for one feature / refactor. Lives in `.scratch/<feature>/PRD.md`. Each PRD has its own todo tracker, slice list, migration gates. ADRs are referenced by PRDs ("this PRD implements ADR 0001") but live separately.

### Live (active or partially landed)

- [`spawn-projection-test-harness/PRD.md`](spawn-projection-test-harness/PRD.md) — spawn-projection test scaffolding.
- [`adr-0001-implementation/PRD.md`](adr-0001-implementation/PRD.md) — ADR 0001 migration: aspect body complete (all 18 ship-state + 4 fresh-find + 5 non-ship rows ✅). One item remains: TBD-3 architectural test.

## Architecture refactors

Two sources:
- **Physics gaps** (S3 / S9 / S10) — relocated from [`physics-audit.md`](physics-audit.md) (Slice P2 deliverable).
- **Arch-review-2 findings** — fresh post-refactor sanity check by 5-reviewer team (planner / config-2 / spawn / cleanup / client lenses) after this session's heavy refactor run (Tier 2/3, megasplit, hot-reload-from-dist, backlog-cleanup, backlog-final).

Items grouped by category, not lens. Effort/impact tags are S/M/L. See "Recommended next work" at the bottom for impact ÷ effort ranking.

### Physics canon gaps

#### S11 — Rocket canon feel (velocity-lock override during `RocketActive`)
**M/M. Real gap surfaced by C1 smoke-test (batch-4 R2a).** Current rocket implementation: `RocketBuffIntent` swaps `Thrust` + `Speed` *caps* (per-arena `rocket.groovy thrust/speed`) for the buff duration. That's a "raise the cap" semantic — player still has to press UP to feel anything; if they don't, the rocket has no observable effect. **Subspace canon is different:** when rocket fires, ship velocity is **locked** to `RocketSpeed × shipFacing` for the buff duration; thrust/reverse input is **ignored**; only turn input has effect. Constant motion, not raised caps. **Implementation shape:** per-tick override in `PlayerDriver` (or equivalent movement system): when entity has `RocketActive`, set linear velocity to `rocketSpeed × shipFacingVector`; suppress thrust/reverse input; permit turn input. Once implemented, delete the cap-swap path in `RocketBuffIntent` drain — the intent component stays as a marker but no longer needs to carry thrust/speed (RocketConfig is read directly by the override). The C1 RaM race fix still holds for any future cap-affecting buffs; the intent shape is reusable. See REFERENCE.md `## Rocket` for canon knobs. [from C1 smoke-test, batch-4 R2a]

#### S3 — Bomb bounce mechanic (`BombBounceCount`)
**M/L. Open design question.** Per-ship `BombBounceCount` (already authored in `ships.groovy`) needs a consumer. Bombs would survive wall contact for N bounces before impact-explode, instead of detonating or decaying on first contact. Open: where does the bounce decrement live — `ContactSystem`, a new `BombBounceSystem`, or via a `Bounces(int)` component? Subspace players notice immediately when wall-glance bombs don't bounce. See REFERENCE.md `## Bomb`.

#### S9 — Wormhole `Gravity` per-ship
**M/M.** Per-ship `Gravity(int)` and `GravityTopSpeed` components projected from `ShipConfig`. `GravityWellSystem` reads each ship's per-ship gravity to compute pull radius `R = 1.325 × g^0.507`. Replace the hardcoded `5000` in `LegacyMapProjector` with a per-wormhole value. **Risk:** Subspace's gravity is per-ship-experiences-pull, not per-wormhole-emits-pull, which inverts the natural ECS shape — needs a design pass before sliceification.

#### S10 — Afterburner mechanic (`AfterburnerEnergy`)
**M/M.** Self-contained slice once the input-binding queue catches up. `AfterburnerEnergy` per-ship (already authored) + new client input + temporary `Speed`/`Thrust` boost while held. Sits with the per-ship-input-mechanic queue.

### Naming / convention

#### F1 — `*Spec` namespace overlap forces `SpawnerCreateSpec` rename
**M/M.** `api.config.SpawnerSpec` (template tier, arena DSL declaration) collides with `api.sim.specs.SpawnerCreateSpec` (factory-call argument). The 18-record namespace introduced in `backlog-final` overloaded the `Spec` suffix that `*Config` already used. **Rename `*Spec` → `*Args` in `api/sim/specs/`** while it's recent (18 records, mechanical import updates). [config-2 #5]

## Recommended next work

Ranked by impact ÷ effort given the post-arch-review-2 finding set. Items in the same band are roughly interchangeable.

### Tier 3 — focused slices (S–M / M)

1. **F1** — `*Spec` → `*Args` rename (mechanical now, expensive later as the 18 records calcify).

### Physics canon gaps (separate pile, see top of section)

S3 (bomb bounce), S9 (wormhole gravity), S10 (afterburner), S11 (rocket canon feel) — gameplay-faithfulness work, not architecture cleanup. Pick when you want to close a player-noticed canon gap rather than a contributor-noticed code smell.
