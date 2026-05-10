---
paths:
  - "api/src/infinity/es/**/*.java"
  - "api/src/infinity/sim/**/*.java"
  - "infinity/src/main/java/infinity/systems/**/*.java"
  - "modules/src/main/java/**/*.java"
---
# Entity TTL — `Decay` is canonical

`com.simsilica.es.common.Decay` is the **only** mechanism for "this entity
expires at time T". A central system reads it and removes the entity when
the deadline passes. Adding a parallel TTL marker (e.g. a custom
`*Decay` / `*Lifetime` / `*ExpiresAt` component) creates two systems racing
to despawn the same entity and breaks the rest of the codebase that already
trusts `Decay` as the single source of truth.

## When you have a TTL

- **Per-instance lifetime** (this prize / projectile / effect dies in N ms):
  set a `Decay` component at creation time. Look at how
  `MapFactory.createPrize`, `WeaponFactory.createBurst`, `WeaponFactory.createMine`, etc. compute the
  duration and use `Decay.duration(...)` or
  `new Decay(now, now + TimeUnit.NANOSECONDS.convert(ms, MILLISECONDS))`.

- **Per-spawner / per-template tuning** (prizes from THIS spawner live N ms,
  bombs of THIS level live M ms): store the duration as a numeric field on
  the relevant template — typically the existing `Spawner` / `*Config`
  record, never on a parallel ECS marker. The spawn system reads the field
  at spawn time and projects it into the spawned entity's `Decay`. See
  `Spawner.getSpawnedDecayMillis()` for the canonical pattern: the template
  carries the duration, `Decay` carries the deadline.

## What NOT to do

- Do not introduce new components named `*Decay`, `*Ttl`, `*Lifetime`,
  `*ExpiresAt`. Pattern-match the names already in the tree to avoid
  reinventing what `Decay` already does.
- Do not add a second decay system. The single decay reaper that consumes
  `Decay` already runs; piggy-back on it.
- Do not store the deadline (an absolute timestamp) on the template. That's
  per-instance state and belongs on `Decay`. Templates carry **duration**;
  the spawn system computes deadline at projection time.

## Why

The same architectural reason as Pattern 4 (`*Config` → spawn projection →
component): "duration is config, deadline is per-instance state, the spawn
system is the boundary." Drift between those layers is where the bugs live.
