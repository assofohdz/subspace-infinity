# Hot-path config-template read — project `perceptionRadius` to a component

Status: done (perceptionRadius projected onto BrainWiring at refreshDerivation boundary; secondary redundant ArenaId/BotRole/ShipType reads deferred — not a rule violation, just a perf nit)
Category: maintenance
Type: AFK

## Parent

[Bot AI v3 PRD](../PRD.md) — slice 3. ADR-0002 (Config-Component Projection)
violation: a hot-path consumer reads the config template every tick.

## Finding

`infinity-server/src/main/java/infinity/ai/BotBrainSystem.java:738-742`
(`perceptionRadius()`, called every tick per bot)

```
configRegistrySystem.forArena(arenaId).botBrain().perceptionRadius()
```

is a direct hot-path template read plus two `ed.getComponent()` calls. ADR-0002
/ [config-pattern.md](../../.claude/rules/config-pattern.md): "hot-path
consumers must not read templates — spawn systems are the only projection
boundary." `RadarRange` already demonstrates the correct pattern (projected
per-ship component, fast-pathed first); this is the fallback path that breaks
the rule.

Related per-tick redundant reads flagged in the same review (fold in if cheap):
- `BotRole` + `ShipType` read twice per tick (`BotBrainSystem:419,636` and `:537,629`) — cache in the `BrainWiring` sidecar.
- `ArenaId` read independently in `perceptionRadius()` (`:738`) and `refreshDerivation()` (`:477`) — cache in `BrainWiring`, update on the existing re-derivation signal.

## Fix

Project `BotBrainConfig.perceptionRadius()` into a server-only component
(e.g. `BotPerceptionRadius`) at arena load / bot spawn, as `RadarRange`
already carries its per-ship value. Hot path reads the component; remove the
`forArena` call from `perceptionRadius()`.

## Acceptance criteria

- [x] `perceptionRadius` projected onto `BrainWiring.arenaPerceptionRadius` at the `refreshDerivation()` registry-change boundary (per-bot, not a separate ECS component — matches the existing wiring sidecar pattern). Hot path: RadarRange check (still per-tick — it can prize-mutate) → `wiring.arenaPerceptionRadius` → no `ConfigRegistry.forArena()` lookup. `refreshDerivation()` moved before perception in `tickBot()` so the fallback is current on the first registry-attach tick.
- [~] `BotRole`/`ShipType`/`ArenaId` redundant reads — deferred. These are per-tick component reads (cheap O(1) hash lookups), not template reads — they don't violate ADR-0002. The redundancy is a perf nit, not the load-bearing concern of this issue. Promote to a v3 follow-up if it shows up in a profile.
- [x] No behaviour change (same effective radius); PMD on touched file shows only the pre-existing high-tier class-complexity (102, down from 103); layer test green.

## Comments
