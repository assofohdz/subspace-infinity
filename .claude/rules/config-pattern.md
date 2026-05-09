# Config Pattern — Template vs Instance

Gameplay tuning (ship stats, weapon parameters, etc.) splits into two tiers. Keep them separate.

## The two tiers

**Template** — `api/src/infinity/config/*Config`
- Immutable Java records, **one per type** (e.g. one `ShipConfig` per entry in `Ships` enum).
- Produced by the config layer (Groovy script today, possibly INI import later).
- Stored in a per-arena `ConfigRegistry`.
- Never read on the hot path.

**Instance** — components in `api/src/infinity/es/...`
- Zay-ES components, **one per entity**, mutable across the entity's lifetime.
- Seeded from the template at ship spawn.
- Mutated by gameplay: upgrades raise `Thrust`, damage drops `Energy`, power-ups can raise `ThrustMax`, etc.

## Who reads what

| Code location | Reads template? | Reads components? |
|---|---|---|
| Hot-path consumers (PlayerDriver, weapons firing) | **No** | Yes |
| Spawn systems | Yes | Writes |
| Admin / live-reload reconcilers | Yes | Writes |
| Clients (HUD, bars) | No (template is server-only) | Yes (via Zay-ES sync) |

Hot-path consumers must not import from `infinity.config`. Spawn systems are the **only** boundary where template → component projection happens.

## Why both — why not just one?

- Per-entity divergence is real gameplay (upgrades, buffs, damage). Template alone can't model it.
- Template alone would force hot-path code to do `registry.get(arena).get(type).stat()` every tick.
- Components alone would force live-reload to walk all entities, and lose the "what are a Warbird's defaults?" answer.
- Template is shared (1 per type); components are per-entity (N). No duplication on the hot path.
- Network sync is automatic via Zay-ES — clients see effective instance values without any template awareness.

## What does NOT belong in this pattern

- **Ephemeral runtime state** (current velocity, cooldowns, last-shot time) → component only, no template.
- **Arena-global state** (map file, arena name, zone config) → read from `SettingsSystem` directly.
- **Per-player / per-client state** (keybinds, HUD preferences) → not arena-scoped; client-side.

## Adding a new config field — checklist

1. Add the field to the appropriate `*Config` record in `api/src/infinity/config/`.
2. Confirm (or add) the per-entity component in `api/src/infinity/es/...`.
3. Have the spawn system project template → component.
4. Consumer reads the component (never the template).

## Reference

See `ShipConfig` / `ShipStat` in `api/src/infinity/config/` for the canonical shape, and the Pattern 4 discussion in project history for the rationale this page summarizes.
