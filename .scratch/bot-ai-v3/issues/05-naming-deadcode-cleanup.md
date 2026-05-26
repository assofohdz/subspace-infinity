# Naming + dead-component cleanup — `MoverState`, `Steering*`

Status: ready-for-agent
Category: maintenance
Type: AFK

## Parent

[Bot AI v3 PRD](../PRD.md) — slice 5. A `State`-suffix naming violation and
four dead pre-ADR components in the shared api/ contract.

## Findings

### 1. `MoverState` violates the `State`-suffix rule (HIGH)

`api/src/main/java/infinity/ai/MoverState.java`

Per the project rule, the `State` suffix is reserved for `BaseAppState`
subclasses only; ECS components / value objects must not use it. `MoverState`
is a server-side transient value object (record). User has corrected this
class of violation twice before.

**Fix:** rename → `MoverSnapshot` (or `KinematicFrame`). Update ~15 call sites
in `infinity.ai.steer.*` / `infinity.ai.brain.*`. Prefer
`mcp__language-server__rename_symbol`.

### 2. Dead `Steering*` components (HIGH)

`api/src/main/java/infinity/es/Steerable.java`, `SteeringPath.java`,
`SteeringSeek.java`, `SteeringSeekable.java`

Zero callers across server + client. Pre-ADR-0009 chicken-era stubs.
`SteeringPath` is entirely empty; the rest are bare markers / one JavaBean
getter (`SteeringSeek.getTarget()`). All are `public class` (not `final`),
carry `@author` tags absent from current components.

**Fix:** delete all four. (If a future steering slice is known to need one,
say which and keep only that — but the review found no live or planned use.)

## Acceptance criteria

- [ ] `MoverState` renamed; all call sites updated; no `*State` ECS/value-object names remain in `infinity.ai.*`
- [ ] `Steerable`/`SteeringPath`/`SteeringSeek`/`SteeringSeekable` deleted; build confirms no references
- [ ] PMD ratchet on touched files; layer test passes

## Comments
