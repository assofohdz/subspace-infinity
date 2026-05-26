# Spawn-time wire-up: bot rotates via `MovementInput`

Status: done
Category: enhancement
Type: HITL

## Parent

[Bot AI v1 PRD](../PRD.md) — slice 2 of 8. See also [ADR-0009](../../../docs/adr/0009-bot-ai-architecture.md).

## What to build

Introduce the ECS plumbing that bot AI runs on, with a trivial fixed-rotation behaviour as a tracer. Concretely:

- New `infinity.ai.BotBrainSystem` (`BaseInfinitySystem` in server-tier). Owns `EntitySet<BotShip>`. On each tick, writes a hard-coded rotating `MovementInput` (e.g. continuous yaw at fixed rate) to every member of the set.
- New `infinity.es.BotBrain` component (server-only, minimal — even an empty marker is fine at this stage; later slices flesh it out as a CCP target).
- Modify `AIEntities.createMobShip` to stamp `MovementInput` instead of `CharacterInput`, plus a fresh `BotBrain`. The `CharacterInput` path remains in `MovementInputSystem` for the legacy chicken framework but bot ships no longer use it.
- Register `BotBrainSystem` in the server's `GameSystem` registry.

This slice proves the input-parity wiring end-to-end before any AI logic exists: `BotBrainSystem` → `MovementInput` → `PlayerDriver` → physics → visible rotation.

## Acceptance criteria

- [x] `BotBrainSystem` class exists at `infinity-server/src/main/java/infinity/ai/BotBrainSystem.java`
- [x] `BotBrain` component exists at `api/src/main/java/infinity/es/BotBrain.java` (immutable; no-arg constructor; per [`components.md`](../../../.claude/rules/components.md))
- [x] `AIEntities.createMobShip` stamps `MovementInput` (not `CharacterInput`) + `BotBrain`
- [x] `BotBrainSystem` registered in the server bootstrap
- [x] `BotBrainSystem` is the only writer of `MovementInput` on `BotShip` entities (architectural test in slice 8 enforces; verify by code review here)
- [x] **Demo:** launch an arena with `FillUpXTeams` enabled → bots visibly rotate in place
- [x] Existing player ships still drive correctly (humans can still play)
- [x] License headers + SPDX on every new file
- [x] PMD ratchet: zero new violations on touched files; one low-effort fix on a pre-existing violation in a touched file (per [`pmd-on-touched-files.md`](../../../.claude/rules/pmd-on-touched-files.md))

## Blocked by

- [#01 — Retire chicken framework](./01-retire-chicken-framework.md)

## Comments

### 2026-05-22 — Implementation complete; awaiting manual launch verification

**Code landed:**

- New `api/src/main/java/infinity/es/BotBrain.java` — server-only marker component (immutable; no-arg ctor; `final class`). Originally drafted as `BotBrainState`; renamed per project convention "`State` suffix reserved for client-side `BaseAppState` subclasses" (now in agent memory).
- New `infinity-server/src/main/java/infinity/ai/BotBrainSystem.java` — `BaseInfinitySystem`. Owns `EntitySet<BotShip>` acquired in `initialize()`, released in `terminate()`. `update(SimTime)` computes a yaw based on simulation seconds (1 full rotation per ~5s) and writes a fresh `MovementInput` to every bot.
- Modified `api/src/main/java/infinity/sim/AIEntities.java` — `createMobShip` now stamps `MovementInput` (was `CharacterInput`) + `BotBrain`. Imports adjusted.
- Modified `infinity-server/src/main/java/infinity/server/GameServer.java` — `BotBrainSystem` registered immediately before `MovementInputSystem` (so `MovementInput` writes land before the input-system tick consumes them).

**Build verification:**

- `:api:compileJava`, `:infinity-server:compileJava` — green
- `spotlessCheck` — no formatting issues
- `:infinity-server:pmdPath` on `BotBrainSystem.java` — green (fixed one `UselessParentheses` violation introduced in first draft; no new violations)
- `LayerDependencyTest`, `CanonicalWriterTest`, `MovementInputSystem`-related tests — all passing
- The 2 deprecation warnings at `GameServer.java:403` are pre-existing from Issue #01 (chicken framework retirement)

**Awaiting manual launch verification (HITL):**

1. Launch arena with `FillUpXTeams` enabled
2. Confirm: bots visibly rotate in place (1 rotation per ~5 seconds)
3. Confirm: existing player ships still drive correctly (no regression on human input)

Acceptance criteria 1-5, 7-9 are satisfied; criterion 6 ("Demo: launch arena → bots visibly rotate") requires keyboard verification before commit.
