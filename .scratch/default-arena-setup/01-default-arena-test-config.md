# Default arena setup — test config + spawners

Status: needs-info

The `(default)` arena is the fallback used when an arena is loaded by name but
has no folder of its own. Today its `arena.groovy` declares only the map and a
list of legacy INI fragments. It has:

- no `shipsScript` — falls back to `GroovyShipLoader.FALLBACK` defaults
- no `spawn` directive — players spawn at the arena's centre tile (512, 512)
- no `prizeSpawners` — there is nothing producing prizes inside the arena
  (the only prize spawner today is the hardcoded one at world coord
  `(-512, 1, -512)` in [`BasicEnvironment.java`](../../infinity-server/src/main/java/infinity/server/BasicEnvironment.java),
  which sits inside the trench arena, not the default).

For now we use **trench** as the testing playground. This issue tracks the
remaining work to wire `(default)` up properly once the trench testbed has
shaken out the design.

## What lands first (separate work, see commit log around 2026-04-30)

The trench testbed introduces:

- `PrizeSpawnerSpec` record + `prizeSpawners { … }` DSL block in
  `arena.groovy`, materialized at arena-load time by `ArenaSystem`
- new optional ECS component `PrizeDecayMillis` so per-spawner TTL flows
  through to each prize
- non-breaking `MapFactory.createWeightedPrizeSpawner` /
  `MapFactory.createPrize` overloads that accept `maxCount` /
  `prizeDecayMillis`; existing callers keep the old signatures
- trench testbed: two `prizeSpawners` (centre + NE corner), warbird upgrades
  enabled in `ships.groovy`, override fragment loaded after the base
  ship-warbird / prizeweights so weapon prizes drop and the ship can use
  them

## What's still pending for `(default)`

1. Decide what prize spawners (if any) `(default)` should declare. Two
   competing reads of "default":
   - **Quiet sandbox** — no spawners, no upgrades; players land on a clean
     map and can shoot at each other but nothing else happens. Today's
     behaviour minus the misplaced trench-overlapping spawner in
     `BasicEnvironment`.
   - **Demo arena** — a small set of spawners around the centre so any
     fresh server has visible prize gameplay without configuring an arena.
     Closer to the "out-of-the-box" experience a new operator expects.

2. Decide whether to migrate the `BasicEnvironment.createWeightedPrizeSpawner`
   call (world coord `(-512, 1, -512)`) into a per-arena spawner declaration,
   delete it, or leave it as a developer-only kludge. As of now it spawns
   inside trench's bounds — coincidentally near players, but not wired to
   trench's arena.

3. Confirm `(default)`'s `shipsScript` story. Either:
   - leave it unconfigured (current behaviour: install
     `GroovyShipLoader.FALLBACK` per-arena snapshot), or
   - point it at `/conf/base/ships.groovy` (does not exist today; legacy
     `/conf/base/ship-<name>.groovy` INI fragments exist instead and run
     through the SettingsSystem ini path, not Pattern 4).

## Suggested order

- Land the trench testbed (DSL, ABI overloads, component, materialization).
- Use it for a few days to validate per-spawner TTL + max-count behaviour.
- Come back here, pick option 1 / 2 / 3 above, and write the (default)
  arena.groovy + (optionally) /conf/base/ships.groovy.

## Related

- [BACKLOG.md](../BACKLOG.md) — the spawner-test-harness
  entry covers programmatic verification of these spawn flows.
- [hardcoded-values.md](../hardcoded-values.md) — the BasicEnvironment
  spawner coord should be logged here once we touch it.
