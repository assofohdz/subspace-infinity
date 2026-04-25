# Hardcoded Values Registry

Running ledger of literal numbers and strings in Java code that look like configuration or magic constants — candidates to move into `arena.conf`, `InfinityConstants`, a Groovy script, or a named constant.

Append new entries as you spot them during normal work. This is an inbox, not a triage queue — don't block a task to fix them, just log and move on. Batch-address periodically.

## Format

One row per occurrence. If the same value appears in multiple files, log each.

| File:Line | Symbol / Context | Value | Notes |
|---|---|---|---|
| ~~PlayerDriver.java:57~~ | ~~`pickup` acceleration slope~~ | ~~`3`~~ | **Resolved** — now reads per-ship `Thrust.getThrust()` (Pattern 4, step 5e). |
| ~~PlayerDriver.java:70~~ | ~~`FORCE_MULTIPLIER`~~ | ~~`20.0`~~ | **Resolved** — removed. PlayerDriver now uses `setLinearVelocity` directly instead of `addForce`, so no force-scaling constant is needed. Our computed velocity is authoritative. |
| ~~PlayerDriver.java:70~~ | ~~`DRAG_FACTOR`~~ | ~~`0.05`~~ | **Resolved** — promoted to per-ship `ShipConfig.dragFactor()` → `DragFactor` component (Pattern 4 #4). PlayerDriver now reads from the watched entity. |
| ~~PlayerDriver.java:80~~ | ~~`TURN_RESPONSIVENESS`~~ | ~~`8.0`~~ | **Resolved** — promoted to per-ship `ShipConfig.turnResponsiveness()` → `TurnResponsiveness` component (Pattern 4 #4). |
| ~~ContactSystem.java:92~~ | ~~`contact.restitution = 1`~~ | ~~`1`~~ | **Resolved** — promoted to per-ship `ShipConfig.bounceRestitution()` → `BounceRestitution` component (Pattern 4 #4). Non-ship dynamic bodies still default to perfectly-elastic (`1.0`) when the component is absent. |

## Guidance on what counts

**Log it:**
- Numeric literals used as thresholds, limits, timings, speeds, distances
- String literals naming sections/keys/components that are repeated across files
- Default-value arguments to `getInt`/`getString` that should match a canonical default
- Magic numbers in physics, rendering, or gameplay math

**Skip it:**
- Obvious identity values (`0`, `1`, `-1`, `null`)
- Loop indices, array sizes derived from input
- Test fixture values
- Values already named via a `final static` constant (those *are* the named constant)
