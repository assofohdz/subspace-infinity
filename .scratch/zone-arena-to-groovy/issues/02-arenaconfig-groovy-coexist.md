# ArenaConfig + GroovyArenaLoader: Groovy path coexisting with INI

Status: done
Parent: [../PRD.md](../PRD.md)
Labels: area:server

## What to build

Build the typed Groovy path for `arena.conf` core fields and prove it works alongside the existing INI loader. **No existing arenas are migrated in this slice** — that's #3. The point here is to land the new path, demonstrate it loads, and let the two formats coexist during the transition so #3 can flip arenas one at a time without breaking the others.

In-scope `arena.conf` keys (the only ones at the top level of `arena.conf` today, per the trench / deva files):

- `[General] Map`
- `[Scripts] Ships`
- `[Spawn] X` / `[Spawn] Z`
- `#include /conf/<preset>/<file>.conf` directive

**Out of scope** (per PRD): the contents of the included [`conf/<preset>/*.conf`](../../../infinity/zone/conf/) preset fragments themselves — those stay INI and continue to flow through `SettingsSystem`'s existing INI parser. Groovy arena files reach them via an `includeFragment` directive.

Deliverables:

- New typed record `api/src/infinity/config/ArenaConfig.java` covering the in-scope fields (`mapFile`, `shipsScript`, `spawnX`, `spawnZ`, `fragmentIncludes`).
- New `infinity/src/main/java/infinity/settings/GroovyArenaLoader.java` mirroring `GroovyZoneLoader` from #1.
- Suggested DSL shape (final form decided during implementation):

  ```groovy
  arena {
      map '04-2026-trench/pub2025.lvl'
      shipsScript '/conf/trench-04-2026/ships.groovy'
      spawn 1000, 20
      includeFragment '/conf/trench-04-2026/trench.conf'  // routed to existing INI parser
  }
  ```

- Arena-loader integration: try `arena.groovy` first, fall back to `arena.conf` if Groovy is absent. Both formats must cleanly coexist.
- A new synthetic test arena under `infinity/zone/arenas/<name>/` with an `arena.groovy` proves the Groovy path loads end-to-end. The existing INI-only arenas (`trench`, `deva`, `default`) keep working unchanged through the fallback.

## Acceptance criteria

- [x] `ArenaConfig` record exists in `api/src/infinity/config/`, immutable, typed
- [x] `GroovyArenaLoader` exists in `infinity/src/main/java/infinity/settings/`, mirrors `GroovyZoneLoader`
- [x] Arena-load path tries `arena.groovy` first, falls back to `arena.conf` if absent (`ArenaSystem.loadArenaConfig` returns `null` from the Groovy path → INI fallback synthesises an `ArenaConfig` so callers read uniformly)
- [x] `includeFragment` directive routes preset fragments through the existing `IniLoader` via the new `SettingsSystem.loadFragments(arenaName, paths)` (each fragment retains its own `#include` support)
- [x] `GroovyArenaLoader.evaluateSourceForTest` exercises in-memory DSL parsing → `ArenaConfig` assertions; covers the synthetic-test-arena criterion without writing to disk
- [x] Existing arenas (`trench`, `deva`, `default`) continue to load via the INI fallback — verified in-game; no `arena.groovy` files added in this slice
- [x] `GroovyArenaLoaderTest`: 7 tests covering builder, end-to-end DSL parse, broken-source-returns-EMPTY (distinguished from missing-script-returns-null), and the EMPTY sentinel

## Blocked by

- [01-zoneconfig-groovy](01-zoneconfig-groovy.md)

## Comments
