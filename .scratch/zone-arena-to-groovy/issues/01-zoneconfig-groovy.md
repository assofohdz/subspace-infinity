# ZoneConfig + GroovyZoneLoader: port zone.conf to Groovy

Status: needs-triage
Parent: [../PRD.md](../PRD.md)
Labels: area:server

## What to build

Smallest end-to-end tracer for the zone-arena-to-groovy migration: replace [`infinity/zone/zone.conf`](../../../infinity/zone/zone.conf) (2 keys, 24 lines) with a typed Groovy DSL. This slice establishes the loader / record / file-name pattern that #2 will mirror for arena scope — keep them symmetric with [`GroovyShipLoader`](../../../infinity/src/main/java/infinity/settings/GroovyShipLoader.java) so #2 can copy-with-confidence.

Deliverables:

- New typed record `api/src/infinity/config/ZoneConfig.java` carrying the two zone-scope settings:
  - `autoLoadArenas` (`List<String>`) — server boots the listed arenas on first tick.
  - `enterSpawnArena` (`String`) — names which loaded arena's `[Spawn]` is used as the world-space spawn for new player sessions on first connect.
- New `infinity/src/main/java/infinity/settings/GroovyZoneLoader.java` mirroring `GroovyShipLoader`'s structure (typed builder DSL, classpath fallback, filesystem-first dev-mode reload).
- New `infinity/zone/zone.groovy` replaces `zone.conf`. Suggested DSL shape (final form decided during implementation):

  ```groovy
  zone {
      autoLoad 'trench', 'deva'
      enterSpawn 'trench'
  }
  ```

- The existing zone-startup consumers (whatever today reads `[Startup] AutoLoad` and `[ZoneEnterSpawn] Arena` via `SettingsSystem`) read from the new `ZoneConfig` instance instead. Per PRD step 3, `SettingsSystem`'s typed-accessor signatures stay the same on the consumer side.
- `zone.conf` deleted (both keys ported, nothing else lives in it).

## Acceptance criteria

- [ ] `ZoneConfig` record exists in `api/src/infinity/config/`, immutable, typed
- [ ] `GroovyZoneLoader` exists in `infinity/src/main/java/infinity/settings/`, mirrors `GroovyShipLoader`'s shape (filesystem-first dev mode, classpath fallback, typed builder)
- [ ] `infinity/zone/zone.groovy` is the canonical zone config; old `zone.conf` is deleted
- [ ] Server startup auto-loads the arenas listed in the new file (verify with `trench` and `deva`)
- [ ] Connect-time zone-enter-spawn resolves to the named arena's `[Spawn]`
- [ ] No INI parser code path remains for zone-scope settings (preset-fragment INI parsing stays — out of scope)
- [ ] Test exercises a `zone.groovy` load → `ZoneConfig` assertions, mirroring the spirit of `GroovyShipLoaderRadarTest`

## Blocked by

None - can start immediately

## Comments
