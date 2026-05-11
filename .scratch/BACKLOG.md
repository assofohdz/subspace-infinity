# Refactor backlog

Bag of non-settings cleanup items — architecture splits, library audits, naming notes. Pull from this file when "what's next?" comes up for cleanup work that doesn't belong in the settings pipeline.

**Subspace-settings wiring lives in the pipeline tracker + work queue, not here:**
- [`settings-pipeline.md`](settings-pipeline.md) — what's wired, gate-by-gate
- [`settings-pipeline-slices.md`](settings-pipeline-slices.md) — what to work on next

## How to use this list

When the user asks "what's next?" or starts a new session asking about cleanup / refactoring opportunities, surface relevant items from this file. For Subspace-settings wiring, defer to the pipeline tracker + slices file.

**Keep this file in sync** — when an item lands, **delete** it from this file in the same commit. Do not strikethrough; crossed-out content is context-window clutter for future sessions.

**Not in this file** — Subspace-settings wiring lives in [`settings-pipeline.md`](settings-pipeline.md) + [`settings-pipeline-slices.md`](settings-pipeline-slices.md); pick from the queue file when settings work is the focus. RaM-pattern follow-ons live in [`replacement-as-mutation/PRD.md`](replacement-as-mutation/PRD.md). Spawn-projection harness expansion lives in [`spawn-projection-test-harness/PRD.md`](spawn-projection-test-harness/PRD.md).

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

### RaM single-writer violations

#### C2 — Inventory + status component families have unresolved multi-writer collisions
**L/L.** ~15 prize appliers (`{Brick,Burst,Decoy,Portal,Rocket,Repel,AntiWarp,Cloak,Stealth,XRadar,MultiFire,Energy,Rotation,Thruster,TopSpeed,Recharge}PrizeApplier.java`) write components that `ShipSpawnSystem`/`ShipWeaponsProjector`/`ShipStatusProjector` also write. Pickup two `RepelPrizeApplier` + fire one repel in same tick → final `Repel` count is ordering-dependent. RaM PRD migration backlog #1 is the canonical fix; ready to land now that pilot proved the shape. [spawn #2 + config-2 #1]

### Naming / convention

#### F1 — `*Spec` namespace overlap forces `SpawnerCreateSpec` rename
**M/M.** `api.config.SpawnerSpec` (template tier, arena DSL declaration) collides with `api.sim.specs.SpawnerCreateSpec` (factory-call argument). The 18-record namespace introduced in `backlog-final` overloaded the `Spec` suffix that `*Config` already used. **Rename `*Spec` → `*Args` in `api/sim/specs/`** while it's recent (18 records, mechanical import updates). [config-2 #5]

## Recommended next work

Ranked by impact ÷ effort given the post-arch-review-2 finding set. Items in the same band are roughly interchangeable.

### Tier 3 — focused slices (S–M / M)

1. **F1** — `*Spec` → `*Args` rename (mechanical now, expensive later as the 18 records calcify).

### Tier 4 — bigger refactors (M/L)

2. **C2** — Inventory + status family multi-writer migration (RaM PRD slice 1). ~15 applier sites + new intent components; the largest live RaM cluster. Audit (C4, landed) surfaced 3 fresh multi-writer violations not in this BACKLOG: `Frequency` (4 writers, team-change race), `ShipType` (2 writers, swap+reproject sequencing risk), `ThorFireDelay` (3 writers, applier fallback overwrites spawn-projected value). All documented in `.claude/rules/replacement-as-mutation.md` live snapshot. Consider folding into C2's scope.

### Physics canon gaps (separate pile, see top of section)

S3 (bomb bounce), S9 (wormhole gravity), S10 (afterburner), S11 (rocket canon feel) — gameplay-faithfulness work, not architecture cleanup. Pick when you want to close a player-noticed canon gap rather than a contributor-noticed code smell.
