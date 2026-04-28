# Migrate `zone.conf` and `arena.conf` to Groovy

Status: ready-for-human
Cross-ref: [GH #101](https://github.com/assofohdz/subspace-infinity/issues/101)

[`infinity/zone/zone.conf`](../../infinity/zone/zone.conf) and [`infinity/zone/arenas/<name>/arena.conf`](../../infinity/zone/arenas/) are still INI-style with `#include` directives and string-keyed `SettingsSystem` lookups. Migrate both to Groovy to match [`ships.groovy`](../../infinity/zone/conf/trench-04-2026/ships.groovy):

- Same per-arena live-reload story (filesystem-first read in dev mode, classpath fallback for packaged jars).
- Type-checked DSL via a typed builder (cf. `GroovyShipLoader.ShipConfigBuilder`) instead of opaque string keys with default values scattered across consumers.
- IDE autocomplete + Groovy compile-error feedback when editing.
- Closes the loop on always-on rule #5: it makes Groovy genuinely the only place tuning knobs live, instead of "Groovy for ships, INI for everything else".

## Approach (sketch)

1. Decide on the typed binding for each tier — likely a `ZoneConfig` record + `ZoneConfigLoader` for zone scope, and an `ArenaConfig` record + `ArenaConfigLoader` for arena scope (parallel structure to `ShipConfig` / `GroovyShipLoader`).
2. Wire the loaders into the existing arena-load and zone-startup paths so Groovy and INI can coexist while the migration ramps.
3. Port settings one fragment at a time. Keep the existing `SettingsSystem` typed accessors as the consumer-facing API initially — back them with the Groovy-derived registry instead of the INI parser. Once all callers use typed accessors, the INI parser can be deleted.
4. Update the [`arena-settings`](../../.claude/skills/arena-settings/) skill to point at the new authoring surface.

Out of scope: the per-preset `conf/<preset>/*.conf` fragments under [`infinity/zone/conf/`](../../infinity/zone/conf/). Larger surface, separate migration; track as a future item if needed.

## Comments
