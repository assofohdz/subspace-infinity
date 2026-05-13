# ADR 0007 — Entity TTL: one mechanism, deadline-shaped (`Decay`)

**Status:** Proposed
**Date:** 2026-05-13
**Deciders:** Asser Fahrenholz

## Context

A real-time game spawns and despawns entities constantly: projectiles after their flight time, prizes after their pickup window, sound effects after they play, mines after their lifetime, debug markers after a few frames. Every one of these is a TTL question — *when does this entity stop existing?*

The literature has a clear shape for this: a *Lifetime* or *DespawnAfter* component attached to the entity, plus a reaper system that despawns when the timer expires. Bevy doesn't ship one by default (community implements it; an active core PR proposes `DespawnAfter`); Flecs offers `Disabled`/`Prefab` markers as a softer cousin; older frameworks (Specs, EntityX) leave it to user code. The pattern is unsurprising; the implementation is small.

The risk is **fragmentation**. Once a codebase has two TTL mechanisms — even by accident, even one as a "special case" — every despawn becomes a question of "which reaper owns this?" Multiple reapers iterating over disjoint component sets cannot coordinate; an entity carrying both reaper markers gets reaped twice or never; the bug only appears under specific load. The architectural value of "there is one TTL mechanism" is much higher than the cost of fitting every use case to it.

Subspace Infinity inherits `com.simsilica.es.common.Decay` from Zay-ES as the framework-canonical TTL primitive. The codebase uses it consistently for entity expiry — projectiles, prizes, sound effects, decorations, mines all stamp `Decay` at spawn time and the central reaper removes the entity when the deadline passes. No parallel `*Ttl` / `*Lifetime` / `*ExpiresAt` components exist (architectural review section A — 0 violations). [`.claude/rules/decay-ttl.md`](../../.claude/rules/decay-ttl.md) is the operational rule already; this ADR records the decision shape.

The codebase also has **two adjacent components that look like TTL but aren't**, and the boundary is worth being explicit about: `Jitter` (component-expiry, not entity-expiry) and `Delay` (deferred-action). The architectural review (ECS-agent finding) flagged these as "gray zone"; this ADR puts them on either side of the line.

## Decision

**Entity expiry uses one mechanism: `Decay`. The shape is *deadline*, not *duration*. Templates carry duration; spawn-time projection converts to deadline. `SimTime`, not wall-clock. Component expiry and deferred-action are distinct concerns and do not use `Decay`.**

### Decay is canonical for entity expiry

`Decay(startTime, endTime)` (`com.simsilica.es.common.Decay`) is the only mechanism for "this entity expires at time T." A central reaper system reads `Decay`, compares `endTime` against `SimTime.getTime()`, and removes the entity when the deadline passes. Names like `*Decay`, `*Ttl`, `*Lifetime`, `*ExpiresAt` are forbidden — adding any of them creates a parallel TTL surface that fragments despawn coordination.

### Deadline, not duration

The component carries an absolute `(startTime, endTime)` pair in `SimTime` nanoseconds. Writers stamp the deadline once at spawn; the component never mutates again until the reaper removes the entity. Alternative shapes considered:

- **Duration / countdown** (`Decay(remainingMs)`, decremented each tick). Rejected — every active entity becomes a per-tick component write; multi-writer summing requires read-modify-write; restart / replay loses the absolute reference; SimEthereal sync sees noise on every tick for a value that nobody is querying mid-flight.
- **Tick-count** (`Decay(remainingTicks)`). Same problems as duration; additionally couples lifetime to physics tick rate, which is wrong for fixed-real-time durations (a 3-second sound effect should be 3 real seconds, not 3 × tick-interval).

Deadline wins because (a) the component is *write-once, read-many*, (b) the reaper is the only mutator post-stamp, and (c) absolute timestamps survive snapshots, replays, and pause-aware logic.

### Multi-writer allowed; reaper read-only

`Decay` is a **documented multi-writer exception** to ADR-0001's canonical-writer rule. All writers fire-and-forget — `MapFactory` / `WeaponFactory` / `ShipFactory` / `GameSounds` stamp deadlines at spawn time; `DeathSystem` / `WeaponsReaperSystem` stamp `Decay.duration(now, 0)` to schedule immediate despawn next tick. The central reaper is read-only; it never writes `Decay`, only removes the entity when the deadline passes. Multi-writer is safe because every writer's value is a *replacement* of the deadline, never a delta — last writer wins, and the only "loser" is an unneeded earlier-stamped deadline.

### Templates carry duration; spawn projects to deadline

Per-spawner / per-template tuning lives in [ADR-0002](./0002-config-component-projection.md) Config records as a duration field — `BombConfig.decayMs`, `DecoyConfig.aliveTimeMs`, `Spawner.spawnedDecayMillis`. The spawn system reads the duration and computes `new Decay(now, now + TimeUnit.NANOSECONDS.convert(durationMs, MILLISECONDS))`. Templates never store deadlines (deadline is per-instance state); components never store durations on the entity (the entity carries the deadline; the duration was the spawner's parameter).

### `SimTime`, not wall-clock

All deadlines are in `SimTime.getTime()` nanoseconds — the simulation clock controlled by the server's tick driver. Wall-clock timestamps (`System.nanoTime()`, `System.currentTimeMillis()`) are **forbidden** in `Decay` (and in any TTL-shaped component). Wall-clock breaks pause semantics, deterministic replay, and unit-test fixtures that drive `SimTime` manually. The architectural review flagged `Delay` (a different component, see below) as using wall-clock in violation; that's a real bug, not a deliberate choice.

### Three TTL-shaped concerns, distinct mechanisms

`Decay` covers entity expiry only. Two adjacent shapes look similar but are not `Decay`:

| Concern | Mechanism | Owner | Example |
|---|---|---|---|
| **Entity expiry** — "remove this entity at time T" | `Decay` + central reaper | Zay-ES `com.simsilica.es.common.Decay` | Bombs, prizes, sound effects, decoy entities, sound markers |
| **Component expiry** — "remove this component from a still-living entity at time T" | Per-component `(startTime, endTime)` + per-component reaper | Bespoke per case (`JitterReaperSystem`) | `Jitter` (bomb-hit screen shake — ship persists; shake ends) |
| **Deferred action** — "execute this `setComponent` / `removeComponent` at time T" | `Delay` component + `DelaySystem` | `infinity.es.Delay` + `DelaySystem` | Delayed bomb arming, time-bombs |

Component-expiry components carry their own end-time fields by design — `Decay` would be wrong because the *entity* is not expiring. The number of component-expiry shapes is small today (one: `Jitter`); they are intentional carve-outs, not violations.

Deferred-action is its own pattern — closer to a scheduled task queue than a TTL. `Delay` deserves its own thinking (currently uses wall-clock per the architectural review's P0-d finding; should move to `SimTime`); that work is outside this ADR's scope.

## Consequences

### Positive

- **One reaper, one truth, no race.** Entity expiry has exactly one mechanism; "who removes this entity?" has one answer everywhere.
- **Write-once component.** `Decay` doesn't mutate after spawn; no per-tick churn, no SimEthereal sync noise, no read-modify-write contention.
- **Multi-writer is free.** All stamp-sites are fire-and-forget; the reaper is read-only; the framework grain absorbs the multi-writer cleanly.
- **Templates and instances stay distinct.** Duration in the Config; deadline on the entity; the spawn boundary is the conversion (consistent with ADR-0002 Config-Component Projection).
- **`SimTime` discipline is uniform.** Pause, replay, and test fixtures all work correctly for any `Decay`-carrying entity.
- **No `*Lifetime` zoo.** Naming convention is enforced; future contributors do not invent parallel TTL components.

### Costs

- **Component-expiry needs a bespoke reaper per case.** `JitterReaperSystem` is hand-written and small; if the codebase grows ten of these, the pattern starts to repeat. Mitigation: at that point, a generic *Expiring Component* abstraction is worth designing — but the current count is one, so YAGNI.
- **`Delay` is a real wall-clock bug today.** `infinity.es.Delay` uses `System.nanoTime()` in its constructor. Per the architectural-review P0-d finding, the fix is to store an absolute `SimTime` deadline and have `DelaySystem` compare against `SimTime.getTime()`. Tracked outside this ADR.
- **The line between "entity expiry" and "component expiry" is sometimes a design choice, not a fact.** A power-up could be modeled either way: (a) entity carrying `Decay` and a per-frame visual; (b) component on the ship with its own end-time. Pick by asking "does the *entity* end, or does an *effect on a still-living entity* end?" Usually obvious; sometimes a judgment call.

### Neutral / deferred

- **Generic component-expiry abstraction.** If the count of `Jitter`-shaped components grows past ~3, design a generic `ExpiringComponent<T>` + reaper. Today: one case, not motivating.
- **Pause-aware `Decay`** — `SimTime` already supports pause via the tick driver; the implications for in-flight `Decay`-carrying entities are correct by construction. No further work needed unless the pause semantics evolve.

## Alternatives considered

- **One mechanism for both entity and component expiry.** Rejected — a power-up effect on a ship is fundamentally not the same lifetime as the ship itself; conflating them either over-removes (despawn the ship when the buff ends) or breaks the rule by stamping `Decay` on a component, which has no mapping in Zay-ES.
- **Duration / countdown shape for `Decay`.** Rejected for reasons above (per-tick write, sync noise, replay-unsafe, tick-rate coupling).
- **Wall-clock deadlines.** Rejected — incompatible with pause, replay, and deterministic test fixtures.
- **Per-component bespoke TTL components** (`BombDecay`, `PrizeDecay`, `SoundDecay`) instead of one shared `Decay`. Rejected — fragments the reaper surface, reinvents the wheel per component type, breaks the framework's grain (Zay-ES ships `Decay` precisely so projects don't do this).
- **No central reaper; each system that creates an entity also removes it on a per-system timer.** Rejected — pushes despawn-coordination into N systems; an entity that needs to be removed by a system different from its creator has no clean shape; the framework gives us a single reaper for free.

## Resolved decisions

- **Single mechanism for entity TTL:** `com.simsilica.es.common.Decay`. No parallels.
- **Shape:** deadline `(startTime, endTime)` in `SimTime` nanos. Write-once; never updated post-stamp.
- **Clock:** `SimTime.getTime()`. Wall-clock forbidden.
- **Multi-writer:** allowed; documented exception to ADR-0001's canonical-writer rule. Reaper is read-only.
- **Tier convention:** templates carry **duration**; the spawn system computes `endTime = now + duration` and projects to `Decay`.
- **Forbidden names:** `*Decay`, `*Ttl`, `*Lifetime`, `*ExpiresAt`.
- **Component-expiry and deferred-action** are distinct mechanisms (`Jitter`+`JitterReaperSystem`; `Delay`+`DelaySystem`); they do not use `Decay`.

## Open work

- **Fix `Delay` to use `SimTime`** (architectural review P0-d). Currently uses `System.nanoTime()` in the constructor — wall-clock violation. Same shape fix as `Decay` already has.
- **Mechanise the "no `*Ttl` / `*Lifetime` parallel" rule** as a cheap ArchUnit check (architectural review P1-b family). Currently audited by hand; 0 violations today but a one-line rule guards forever.
- **Generic `ExpiringComponent<T>` abstraction** is deferred until the count of `Jitter`-shaped components grows past ~3. Today: one case.

## References

- [`.claude/rules/decay-ttl.md`](../../.claude/rules/decay-ttl.md) — operational rule this ADR formalises.
- [`docs/adr/0001-ecs-component-model.md`](./0001-ecs-component-model.md) — `Decay` is the documented multi-writer exception; this ADR explains why.
- [`docs/adr/0002-config-component-projection.md`](./0002-config-component-projection.md) — templates carry duration; spawn projects to deadline.
- `com.simsilica.es.common.Decay` (Zay-ES) — the framework-canonical component this ADR pins as project-wide.
- `api/src/main/java/infinity/es/Jitter.java`, `api/src/main/java/infinity/es/Delay.java` — the two adjacent shapes this ADR puts on the other side of the line.
- Bevy issue [#20244 "Add DespawnAfter mechanism"](https://github.com/bevyengine/bevy/issues/20244) — the analogous community pattern in another ECS; informs the "TTL-as-component is standard" framing.
- Sander Mertens / Flecs ECS-FAQ — discusses `Disabled` and component-lifecycle patterns; informs the entity-vs-component-expiry distinction this ADR makes explicit.
