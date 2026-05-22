# Retire chicken framework to `infinity.ai.legacy.*`

Status: needs-triage
Category: enhancement
Type: AFK

## Parent

[Bot AI v1 PRD](../PRD.md) — slice 1 of 8. See also [ADR-0009](../../../docs/adr/0009-bot-ai-architecture.md).

## What to build

Move the existing `infinity.ai.*` chicken framework (Brain / Goal / Strategy / Actor / MobDriver / MobSystem / Wander / Eat / Say / SeenObject / TouchEvent / AnimPump / BrainScheduler / Sequence / Wait / WalkDir / WalkTo / Flee / Go / TimedGoal / MobStats / MovementSettings / OctBytes / LoopAction / ActionFactory / ActionStatus / Action / AbstractGoal / BlockedListener / MovementListener / TouchListener / BrainConfiguration / BrainConfigurations) to `infinity.ai.legacy.*` with `@Deprecated` annotations on the public surface. Update every import site to the new package path. No behaviour change — the framework still compiles and still drives no `BotShip` entities (it never did; that's the bug downstream slices fix). Clears the `infinity.ai.*` namespace so subsequent slices can land new types without collision.

## Acceptance criteria

- [ ] All current `infinity.ai.*` Java source files moved under `infinity-server/src/main/java/infinity/ai/legacy/`
- [ ] Public types annotated `@Deprecated` (class-level)
- [ ] Every import / FQN reference updated (likely in `AIEntities.java`, `MobSystem` registration in the server bootstrap, anything that references `BrainConfigurations.initialize`, etc.)
- [ ] `./gradlew build` passes
- [ ] `./gradlew :infinity-client:test` passes (`LayerDependencyTest`, `CanonicalWriterTest` still green)
- [ ] License headers + SPDX preserved on every moved file
- [ ] No behaviour change observable: launching an arena with `FillUpXTeams` still spawns idle bots (same as before)

## Blocked by

None — can start immediately.

## Comments
