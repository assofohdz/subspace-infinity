# Migrate trench / deva / default to arena.groovy; retire INI-arena fallback

Status: needs-triage
Parent: [../PRD.md](../PRD.md)
Labels: area:server, area:docs

## What to build

Convert each of the three active arenas from `arena.conf` to `arena.groovy`, then delete the INI-arena fallback path established in #2 since no arena uses it anymore. Update the `arena-settings` skill so future authoring lands on the new surface.

Per arena (`trench`, `deva`, `default`):

- Author `infinity/zone/arenas/<name>/arena.groovy` using the DSL from #2, preserving the existing `includeFragment` reference to the preset fragment.
- Delete the corresponding `arena.conf`.

Cleanup:

- Remove the INI-arena fallback path from `GroovyArenaLoader` — no arena uses it anymore.
- Update [`.claude/skills/arena-settings/`](../../../.claude/skills/arena-settings/) to point at `arena.groovy` authoring instead of `arena.conf`. **Keep** the fragment-INI guidance — `conf/<preset>/*.conf` is still INI and authored through the existing path.

After this slice, the only INI files remaining in the in-scope migration surface are the preset fragments (out-of-scope per the PRD). `SettingsSystem`'s INI parser is still alive for those fragments and not touched.

## Acceptance criteria

- [ ] `infinity/zone/arenas/trench/arena.groovy` exists; `arena.conf` deleted
- [ ] `infinity/zone/arenas/deva/arena.groovy` exists; `arena.conf` deleted
- [ ] `infinity/zone/arenas/default/arena.groovy` exists; `arena.conf` deleted
- [ ] Each arena loads identically to before (same map, same spawn, same ships script, same preset fragment merged in)
- [ ] INI-arena fallback path removed from `GroovyArenaLoader`
- [ ] `arena-settings` skill updated to point at `arena.groovy` authoring; fragment-INI guidance preserved
- [ ] Manual verification: server starts, both `trench` and `deva` are reachable, ships spawn correctly with the same stats as before

## Blocked by

- [02-arenaconfig-groovy-coexist](02-arenaconfig-groovy-coexist.md)

## Comments
