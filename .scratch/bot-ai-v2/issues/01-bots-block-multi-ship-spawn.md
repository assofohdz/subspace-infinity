# `bots { }` block + multi-ship spawn variety

Status: needs-triage
Category: enhancement
Type: HITL

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 1 of 9. Implements [ADR-0010](../../../docs/adr/0010-bot-composition-dsl.md).

## What to build

First slice of v2 — orthogonal to the tactical stack but a prerequisite for
later slices that need specific ship hulls (Leviathan, Shark, Javelin).
Replaces the current hard-coded "every bot is a Javelin" path with an
operator-authored `bots { ship 'X', archetype: 'Y' }` block in `arena.groovy`,
implementing the ADR-0010 reservation. Multi-bot per arena via repeated
declarations (and/or `countPerPlayer` already wired from v1 slice #08).

Demo: KOTH spawns a configurable mix of ship hulls (e.g. 2 Warbirds +
1 Shark + 1 Leviathan), all still using the v1 `CombatantBrain` archetype
since v2 archetypes don't exist yet. Visual: the player sees three
distinct ship shapes in the arena, not three Javelins.

## Acceptance criteria

- [ ] `api/infinity.config.BotsConfig` + `BotShipConfig` records exist
- [ ] `infinity-server/.../settings/BotsAdapter` parses `bots { ... }` block; registered in `ConfigRegistrySystem.FRAGMENT_BINDINGS`
- [ ] `arena.groovy` accepts:
  ```groovy
  bots {
      ship 'warbird', archetype: 'Brawler', count: 2
      ship 'shark',   archetype: 'Brawler', count: 1
      ship 'leviathan', archetype: 'Brawler', count: 1
  }
  ```
- [ ] `FillUpXTeams` (or a successor `BotSpawnerModule`) reads `BotsConfig` and spawns ships per the block; falls back to existing Javelin default when no `bots { }` block present
- [ ] KOTH arena.groovy updated with a multi-ship `bots { }` block as the demo
- [ ] Existing FillUpXTeams tests + arch tests stay green
- [ ] Unit tests for `BotsAdapter` (full / empty / multi-ship cases)
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

None — can start immediately.

## Comments
