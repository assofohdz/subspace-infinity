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

Open follow-ups relocated from [`physics-audit.md`](physics-audit.md) (Slice P2 deliverable). Each is independently shippable; gameplay-impact roughly bomb-bounce > afterburner > wormhole-gravity.

### S3 — Bomb bounce mechanic (`BombBounceCount`)
**Effort:** medium. **Impact:** large (most-noticed canon gap). **Status:** open design question.

Per-ship `BombBounceCount` (already authored in `ships.groovy`) needs a consumer. Bombs would survive wall contact for N bounces before impact-explode, instead of detonating or decaying on first contact. Open: where does the bounce decrement live — `ContactSystem`, a new `BombBounceSystem`, or via a `Bounces(int)` component? Subspace players notice immediately when wall-glance bombs don't bounce. See REFERENCE.md `## Bomb` for canonical semantics.

### S9 — Wormhole `Gravity` per-ship
**Effort:** medium. **Impact:** medium (canon-faithful wormholes).

Per-ship `Gravity(int)` and `GravityTopSpeed` components projected from `ShipConfig`. `GravityWellSystem` reads each ship's per-ship gravity to compute pull radius `R = 1.325 × g^0.507`. Replace the hardcoded `5000` in `LegacyMapProjector` with a per-wormhole value (probably zone-tier).

**Risk:** Subspace's gravity is per-ship-experiences-pull, not per-wormhole-emits-pull, which inverts the natural ECS shape (today: gravity-well entity has the force; canon: ship has the susceptibility). Needs a design pass before sliceification.

### S10 — Afterburner mechanic (`AfterburnerEnergy`)
**Effort:** medium. **Impact:** medium (canon mechanic).

Self-contained slice once the input-binding queue catches up. `AfterburnerEnergy` per-ship (already authored in `ships.groovy`) + new client input + temporary `Speed`/`Thrust` boost while held. Sits with the per-ship-input-mechanic queue (multifire firing modes, etc.).
