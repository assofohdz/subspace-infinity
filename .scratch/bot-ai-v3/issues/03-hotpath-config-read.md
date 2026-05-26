# Hot-path config-template read — project `perceptionRadius` to a component

Status: ready-for-agent
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

- [ ] `perceptionRadius` projected to a component at the spawn/arena-load boundary; no `ConfigRegistry` read on the per-tick path
- [ ] `BotRole`/`ShipType`/`ArenaId` cached in `BrainWiring`, refreshed via the existing derivation-change signal (no per-tick double reads)
- [ ] No behaviour change (same effective radius); PMD ratchet; layer test passes

## Comments
