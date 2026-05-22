# Flocking: `Separation` / `Cohesion` / `Alignment` + `BlendedSteering`

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot AI v1 PRD](../PRD.md) — slice 7 of 8. See also [ADR-0009](../../../docs/adr/0009-bot-ai-architecture.md).

## What to build

Multi-bot polish. Allied bots maintain spacing while pursuing the same target; don't dogpile / collide. Adds the Reynolds boids triad + a weighted-sum composite to blend flocking with combat steering.

- **api/** — `infinity.ai.steer.Separation` — steers away from allies within a configured radius (force magnitude inversely proportional to distance). `Cohesion` — steers toward the centroid of nearby allies. `Alignment` — steers to match the average heading of nearby allies. All three filter by team (`Frequency` match) and operate on `PerceptionSnapshot.allies`.
- **api/** — `infinity.ai.steer.BlendedSteering` — weighted-sum composite (`Vec3d output = Σ weight_i × child_i.steer(mover)`). Sibling to `PrioritySteering` which is first-non-null-wins; `BlendedSteering` is "all children contribute proportionally."
- **Brawler BT** — movement leaves (`SteerPursue`, `SteerOrbitTarget`, `SteerEvade`) now produce a steering result that is **blended with flocking** by wrapping in `BlendedSteering([AvoidObstacles × highW, primary-behaviour × mediumW, Separation × lowW, Cohesion × lowW, Alignment × lowW])` before translating to `MovementInput`.
- Unit tests for the three boids primitives (force directions correct given mock neighbours) and `BlendedSteering` (weighted-sum correctness across mock children).

## Acceptance criteria

- [ ] `infinity.ai.steer.Separation`, `Cohesion`, `Alignment`, `BlendedSteering` exist in api/
- [ ] Brawler BT uses `BlendedSteering` to compose flocking with combat behaviour
- [ ] Unit tests for the boids triad + BlendedSteering
- [ ] **Demo:** launch arena with 4+ allied bots on one team → bots maintain spacing while collectively pursuing the same player; don't dogpile into a single point or collide with each other
- [ ] License headers + SPDX on every new file
- [ ] PMD ratchet
- [ ] Layer test passes

## Blocked by

- [#06 — Evade + low energy](./06-evade-low-energy.md)

## Comments
