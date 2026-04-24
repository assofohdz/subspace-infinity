# Config Consumers Registry

Tracks the relationship between **config fields** (what the Groovy script / INI / registry declares) and **consumers** (what system, driver, or class actually reads the value at runtime).

Purpose:

- Spot **orphan config** — fields declared in a script but read by nothing (dead config).
- Spot **orphan consumers** — systems that reach for a field no script populates (silent default).
- Answer "what breaks if I change `MaximumThrust`?" in one lookup.
- Document the **path** a value takes, since Pattern 4 has multiple (template → component → reader).

## Format

One row per `(config field, consumer)` pair. A field with three consumers gets three rows.

| Config field | Consumer | Path | Notes |
|---|---|---|---|
| `ShipConfig.thrust` | `PlayerDriver.update()` | `component:Thrust` | Used as acceleration rate per tick. Projected at spawn by `ShipSpawnSystem`. |
| `ShipConfig.speed` | `PlayerDriver.update()` | `component:Speed` | Used as forward-velocity cap. Projected at spawn by `ShipSpawnSystem`. |
| `ShipConfig.rotation` | `PlayerDriver.update()` | `component:Rotation` | Used as rad/sec scalar for rotation input. Projected at spawn by `ShipSpawnSystem` (int → rad/sec via 2π/400). |
| `ShipConfig.recharge` | `EnergySystem.update()` | `component:Recharge` | Used as energy/sec regen rate. Projected at spawn by `ShipSpawnSystem`. |
| `ShipConfig.energy` | `EnergySystem.update()` | `component:Energy` / `component:EnergyMax` | Current pool + cap for damage/regen math. Projected at spawn by `ShipSpawnSystem`. |

**Column meaning:**

- **Config field** — the canonical typed path, e.g. `ShipConfig.maximumThrust`, `BombConfig.damageLevel`, `FlagConfig.dropDelay`. Match the Groovy DSL name.
- **Consumer** — `ClassName.methodName()` or `ClassName (field)`. Include the method when useful for finding the read site.
- **Path** — how the consumer obtains the value. Typical entries:
  - `template` — direct read from `ConfigRegistry.get(type).field()`
  - `component:ComponentName` — read from a per-entity Zay-ES component derived from the template
  - `setting` — string-keyed `settings.getInt(arena, section, key, default)` (legacy; tag for migration)
- **Notes** — anything special: upgrade semantics, caching, tick frequency, etc.

## When to append

- Adding a new field to any `*Config` record → add rows for each planned consumer (or a row with consumer = `TBD` if wiring is staged).
- Changing a consumer to read a new field → add a row.
- Removing a consumer → delete its row; if field has zero consumers after, flag it as orphan config for review.
- Adding a new consumer class reading existing config → add one row per field it reads.

Don't log per-tick / per-call reads inside one method — one row per `(field, consumer class)` pair is enough granularity.

## What NOT to track here

- Literal magic numbers in code — those go in [`hardcoded-values.md`](hardcoded-values.md).
- Per-entity component values that don't derive from config (e.g. current energy, current velocity).
- INI-only settings nothing reads yet — they'd all appear as "orphan config". Don't preload; only add rows when a consumer is wired.
