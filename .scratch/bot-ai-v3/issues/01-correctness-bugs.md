# Correctness bugs — NPE, target-thrash, stale role

Status: done
Category: bug
Type: HITL

## Parent

[Bot AI v3 PRD](../PRD.md) — slice 1. Three real runtime/behaviour bugs found
in the 2026-05-26 review. The NPE and target-thrash are clear fixes; the
role-refresh item carries a design decision (flagged below).

## Findings

### 1. NPE in `FollowTrafficBehaviour.enumerate()` (CRITICAL)

`infinity-server/src/main/java/infinity/ai/tactical/FollowTrafficBehaviour.java:57`

`best` is initialised `null` and only assigned when `score > -1.0`. If any
contributing field (`CombatDensityField`, `ArenaDensity`) returns `NaN`,
`NaN > -1.0` is false for every candidate, so `best` stays null and
`best.x()` throws. Runtime invariant (scores ≥ 1.0) makes it unlikely today,
but a broken/future field returning NaN or negative density triggers it
silently.

**Fix:** add `if (best == null) return List.of();` after the loop.

### 2. Target-thrash: stickiness bypassed for near-equidistant threats (HIGH)

`infinity-server/src/main/java/infinity/ai/tactical/TacticalPlannerImpl.java:66`
+ `EngageBehaviour.enumerate()`

`scored.currentScore` stays `Double.NEGATIVE_INFINITY` whenever the running
goal (`Engage(id=5)`) is not re-enumerated this cycle. `EngageBehaviour`
enumerates only the *nearest* threat, so if threat id=7 becomes nearest by a
hair, `Engage(id=5)` is never re-enumerated, `currentScore` stays
`NEGATIVE_INFINITY`, and the `withinMargin` stickiness guard
(`currentScore > NEGATIVE_INFINITY`) is bypassed. Two near-equidistant
threats oscillate goal selection every ~150ms (planner cadence). ADR-0013
says "an invalidated goal loses its protection" but defines invalidation as
"target despawned", not "not-nearest-this-cycle".

**Fix (preferred):** `EngageBehaviour.enumerate()` offers ALL alive threats
(not just nearest), so stickiness protects the chosen target across a
nearest-swap. **Alternative:** a separate target-change debounce with its own
hysteresis threshold.

### 3. `BotRole` never refreshed after spawn (HIGH — needs decision)

`infinity-server/src/main/java/infinity/ai/BotBrainSystem.java:419`
(`ensureRoleBias`)

Once a `BotRole` is stamped (non-null), `ensureRoleBias` reads it once and
sets the blackboard bias without rechecking the objective. If the objective
transitions mid-round (e.g. KOTH tile ownership flips the role split) and the
bot entity *persists* (not respawned), the role is never updated. ADR-0015
intends roles assigned once at spawn and held for the round — but "round"
isn't a reset signal the code observes.

**Decision needed:** is the invariant (a) "role is fixed for the entity's
life; a new role requires death+respawn" — then just document it in the
`ensureRoleBias`/`BotRole` Javadoc; or (b) "role must track objective changes"
— then add a forced re-assign on a round/objective-change event. ADR-0015's
"event-driven reassignment deferred to v2.x" note suggests (a) is acceptable
for now. Lean (a) + document unless the user wants the event hook.

## Acceptance criteria

- [x] `FollowTrafficBehaviour.enumerate()` returns `List.of()` when `best == null`; unit test `nanScoresReturnEmptyInsteadOfNpe` covers NaN-returning stub fields
- [x] `EngageBehaviour.enumerate()` offers all alive threats nearest-first; new test `engageStickinessHoldsAcrossEquidistantThreats` proves stickiness holds across cadences when nearest swaps by epsilon
- [x] `BotRole` refresh: **option (a)** — fixed for entity lifetime documented in `BotRole` class Javadoc + `BotBrainSystem.ensureRoleBias` Javadoc with ADR-0015 cross-ref; event-driven reassignment stays deferred to v2.x
- [x] `BotInputCanonicalityTest` + `CanonicalWriterTest` + `ComponentImmutabilityTest` green
- [x] PMD on touched files clean except a pre-existing class-level `BotBrainSystem` cyclomatic complexity (103, High tier — deferred per ratchet rule)

## Comments
