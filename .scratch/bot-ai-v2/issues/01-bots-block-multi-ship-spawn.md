# `bots { }` block + multi-ship spawn variety

Status: done
Landed: e9861a40 (2026-05-25) — also carried the BotDebug HUD cleanup
Category: enhancement
Type: HITL

## Parent

[Bot AI v2 PRD](../PRD.md) — slice 1 of 12. Implements [ADR-0010](../../../docs/adr/0010-bot-composition-dsl.md) as amended by [ADR-0014](../../../docs/adr/0014-capability-derived-bot-composition.md).

## What to build

Orthogonal to the tactical stack but a prerequisite for later slices that need
specific ship hulls (Leviathan, Shark, Javelin). Replaces the current
hard-coded "every bot is a Javelin" path with an operator-authored
`bots { ship 'X', count: N }` block in `arena.groovy`.

**Per ADR-0014, the block is a per-ship *tweak overlay*, not a from-scratch
weight authoring surface.** The `weights: [...]` set-value semantics from the
original ADR-0010 draft are gone — a bot's behaviour weights are derived from
its `CapabilityProfile` (slice #04); arena.groovy only carries optional
`tweak: [...]` additive/multiplicative deltas on top of the derived vector.
`archetype:` as a from-scratch name is dropped; v2.0 has no named archetypes
(deferred to v2.1 per ADR-0014).

Demo: KOTH spawns a configurable mix of ship hulls (e.g. 2 Warbirds +
1 Shark + 1 Leviathan), all still using the v1 `CombatantBrain` until the
tactical stack lands. Visual: the player sees three distinct ship shapes in
the arena, not three Javelins.

## Acceptance criteria

- [ ] `api/infinity.config.BotsConfig` + `BotShipConfig` records exist; `BotShipConfig` carries `ship`, `count`, optional `tweak` (list of `(behaviour, op, value)` deltas) — **no `weights` field**
- [ ] `infinity-server/.../settings/BotsAdapter` parses `bots { ... }` block; registered in `ConfigRegistrySystem.FRAGMENT_BINDINGS`
- [ ] `arena.groovy` accepts:
  ```groovy
  bots {
      ship 'warbird',   count: 2
      ship 'shark',     count: 1
      ship 'leviathan', count: 1, tweak: [['anchor', '*', 1.3]]
  }
  ```
- [ ] `FillUpXTeams` (or a successor `BotSpawnerModule`) reads `BotsConfig` and spawns ships per the block; falls back to existing Javelin default when no `bots { }` block present
- [ ] `countPerPlayer` scaling (from v1 slice #08) still applies on top of per-hull `count`
- [ ] KOTH arena.groovy updated with a multi-ship `bots { }` block as the demo
- [ ] Existing FillUpXTeams tests + arch tests stay green
- [ ] Unit tests for `BotsAdapter` (full / empty / multi-ship / with-tweak cases)
- [ ] PMD ratchet on touched files
- [ ] Layer test passes

## Blocked by

None — can start immediately. Independent of the nav/tactical track.

## Comments
