# ArenaConfig + GroovyArenaLoader: Groovy path coexisting with INI

Status: needs-triage
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

- [ ] `ArenaConfig` record exists in `api/src/infinity/config/`, immutable, typed
- [ ] `GroovyArenaLoader` exists in `infinity/src/main/java/infinity/settings/`, mirrors `GroovyZoneLoader`
- [ ] Arena-load path tries `arena.groovy` first, falls back to `arena.conf` if absent
- [ ] `includeFragment` directive routes preset fragments through the existing `SettingsSystem` INI parser unchanged
- [ ] Synthetic test arena with `arena.groovy` loads correctly
- [ ] Existing arenas (`trench`, `deva`, `default`) continue to load via the INI fallback (regression check — no arena.groovy files added in this slice)
- [ ] Test exercises a Groovy arena load → `ArenaConfig` assertions

## Blocked by

- [01-zoneconfig-groovy](01-zoneconfig-groovy.md)

## Comments
