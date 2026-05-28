# Legacy chicken-framework deletion (~5,200 LOC dead code)

Status: done
Category: maintenance
Type: AFK

## Parent

[Bot AI v3 PRD](../PRD.md) — slice 4. Completes the ADR-0009 open-work
"retire legacy" item and supersedes
[bot-ai/issues/01-retire-chicken-framework.md](../../bot-ai/issues/01-retire-chicken-framework.md)
(which only did the package *move*, not the deletion, and is stuck at
`needs-triage`).

## Finding

`infinity-server/src/main/java/infinity/ai/legacy/` — 35 files, ~5,089 LOC,
`@Deprecated`, structurally dead: `MobSystem` is not registered in
`GameServer`; no `BotShip` ever receives a `MobDriver`/`CharacterInput` from
the v1/v2 stack. The only live references are:

- `infinity-client/src/main/java/infinity/client/states/MobDebugState.java:60-61,132` — imports `legacy.MobStats`/`legacy.MobSystem`, calls `systems.get(MobSystem.class)`.
- `infinity-client/src/test/java/infinity/client/states/MobDebugStateLifecycleTest.java:11,76` — registers `new MobSystem()`.
- `infinity-server/src/main/java/infinity/systems/MovementInputSystem.java:48-50,159-209` — live `CharacterInput`/`UprightDriver`/`MobContainer` path; ADR-0009 says `MovementInputSystem` collapses to one `PlayerContainer` when legacy goes.

Secondary: 34 of 35 legacy files lack the SPDX header (CLAUDE.md #2) — moot
once deleted, do not patch files marked for deletion.

## Fix (clean deletion slice — no design decisions)

1. Delete `infinity-server/src/main/java/infinity/ai/legacy/` (35 files).
2. Delete `infinity-client/.../states/MobDebugState.java` + `MobDebugStateLifecycleTest.java`.
3. Remove the `CharacterInput.class` container, `UprightDriver` routing, and `MobContainer` from `MovementInputSystem`; collapse to `PlayerContainer`.
4. Delete `api/src/main/java/infinity/es/input/CharacterInput.java` (now unused — verify no other callers first).
5. Remove any `Serializer.registerClass(CharacterInput.class)` wire registration.

Net ≈ −5,200 LOC, one client state, one test. No behaviour change.

## Acceptance criteria

- [x] `infinity.ai.legacy` package gone (35 files); `MobDebugState` + its test gone; `LayerDependencyTest` `MobDebugState` exclusion removed
- [x] `MovementInputSystem` collapsed to `PlayerContainer`; `CharacterInput`/`UprightDriver`/`MobContainer` + `MovementBodyInitializer` charDriver branch removed
- [x] `CharacterInput` component deleted; verified no `Serializer.registerClass(CharacterInput.class)` existed (so no registration to remove). `BotInputCanonicalityTest`'s vacuous `characterInput_is_never_constructed_in_ai_or_modules_packages` method deleted.
- [x] Full server + client build + tests green; `LayerDependencyTest` + `BotInputCanonicalityTest` green
- [x] [bot-ai/issues/01](../../bot-ai/issues/01-retire-chicken-framework.md) already at Status: done — no further tracker work

## Comments
