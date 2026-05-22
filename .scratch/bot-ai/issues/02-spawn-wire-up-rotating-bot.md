# Spawn-time wire-up: bot rotates via `MovementInput`

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot AI v1 PRD](../PRD.md) — slice 2 of 8. See also [ADR-0009](../../../docs/adr/0009-bot-ai-architecture.md).

## What to build

Introduce the ECS plumbing that bot AI runs on, with a trivial fixed-rotation behaviour as a tracer. Concretely:

- New `infinity.ai.BotBrainSystem` (`BaseInfinitySystem` in server-tier). Owns `EntitySet<BotShip>`. On each tick, writes a hard-coded rotating `MovementInput` (e.g. continuous yaw at fixed rate) to every member of the set.
- New `infinity.es.BotBrainState` component (server-only, minimal — even an empty marker is fine at this stage; later slices flesh it out as a CCP target).
- Modify `AIEntities.createMobShip` to stamp `MovementInput` instead of `CharacterInput`, plus a fresh `BotBrainState`. The `CharacterInput` path remains in `MovementInputSystem` for the legacy chicken framework but bot ships no longer use it.
- Register `BotBrainSystem` in the server's `GameSystem` registry.

This slice proves the input-parity wiring end-to-end before any AI logic exists: `BotBrainSystem` → `MovementInput` → `PlayerDriver` → physics → visible rotation.

## Acceptance criteria

- [ ] `BotBrainSystem` class exists at `infinity-server/src/main/java/infinity/ai/BotBrainSystem.java`
- [ ] `BotBrainState` component exists at `api/src/main/java/infinity/es/BotBrainState.java` (immutable; no-arg constructor; per [`components.md`](../../../.claude/rules/components.md))
- [ ] `AIEntities.createMobShip` stamps `MovementInput` (not `CharacterInput`) + `BotBrainState`
- [ ] `BotBrainSystem` registered in the server bootstrap
- [ ] `BotBrainSystem` is the only writer of `MovementInput` on `BotShip` entities (architectural test in slice 8 enforces; verify by code review here)
- [ ] **Demo:** launch an arena with `FillUpXTeams` enabled → bots visibly rotate in place
- [ ] Existing player ships still drive correctly (humans can still play)
- [ ] License headers + SPDX on every new file
- [ ] PMD ratchet: zero new violations on touched files; one low-effort fix on a pre-existing violation in a touched file (per [`pmd-on-touched-files.md`](../../../.claude/rules/pmd-on-touched-files.md))

## Blocked by

- [#01 — Retire chicken framework](./01-retire-chicken-framework.md)

## Comments
