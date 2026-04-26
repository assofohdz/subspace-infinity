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
| `ShipConfig.thrust` | `PrizeSystem.handleAcquireThruster()` | `component:Thrust` / `component:ThrustMax` / `component:ThrustUpgrade` | THRUSTER prize bumps `Thrust` by `ThrustUpgrade`, clamped at `ThrustMax`. No-op when upgrade=0 (trench preset). |
| `ShipConfig.speed` | `PlayerDriver.update()` | `component:Speed` | Used as forward-velocity cap. Projected at spawn by `ShipSpawnSystem`. |
| `ShipConfig.speed` | `PrizeSystem.handleAcquireTopSpeed()` | `component:Speed` / `component:SpeedMax` / `component:SpeedUpgrade` | TOPSPEED prize bumps `Speed` by `SpeedUpgrade`, clamped at `SpeedMax`. |
| `ShipConfig.rotation` | `PlayerDriver.update()` | `component:Rotation` | Used as rad/sec scalar for rotation input. Projected at spawn by `ShipSpawnSystem` (int → rad/sec via 2π/400). |
| `ShipConfig.rotation` | `PrizeSystem.handleAcquireRotation()` | `component:Rotation` / `component:RotationMax` / `component:RotationUpgrade` | ROTATION prize bumps `Rotation` by `RotationUpgrade`, clamped at `RotationMax`. All values rad/sec (converted at spawn). |
| `ShipConfig.recharge` | `EnergySystem.update()` | `component:Recharge` | Used as energy/sec regen rate. Projected at spawn by `ShipSpawnSystem`. |
| `ShipConfig.recharge` | `PrizeSystem.handleAcquireRecharge()` | `component:Recharge` / `component:RechargeMax` / `component:RechargeUpgrade` | RECHARGE prize bumps `Recharge` by `RechargeUpgrade`, clamped at `RechargeMax`. All values energy/sec (converted at spawn). |
| `ShipConfig.energy` | `EnergySystem.update()` | `component:Health` / `component:Energy` | `Health` is the live pool (depletes from damage / weapon costs, regens via `Recharge`); `Energy` is the upgradeable cap that `Health` tops out at. Both projected at spawn by `ShipSpawnSystem` (Health = Energy = `stat.initial()`). |
| `ShipConfig.energy` | `PrizeSystem.handleAcquireEnergy()` | `component:Energy` / `component:EnergyMax` / `component:EnergyUpgrade` | ENERGY prize bumps `Energy` (the cap) by `EnergyUpgrade`, clamped at `EnergyMax` (the absolute hard cap). Live pool `Health` is untouched here — that's QUICKCHARGE's job. |
| `ShipConfig.dragFactor` | `PlayerDriver.update()` | `component:DragFactor` | Coast-drag fraction of `Thrust` when no thrust intent (`0` = pure coast, `1` = decelerate as fast as full thrust). Default `0.05` if Groovy omits it. Projected at spawn by `ShipSpawnSystem`. |
| `ShipConfig.turnResponsiveness` | `PlayerDriver.update()` | `component:TurnResponsiveness` | Rate constant (1/sec) for angular-velocity ease-toward-target; `8.0` ≈ 95% of target in ~0.4 sec. Default `8.0` if Groovy omits it. Projected at spawn by `ShipSpawnSystem`. |
| `ShipConfig.bounceRestitution` | `ContactSystem.newContact()` | `component:BounceRestitution` | Wall-bounce energy retention (`1.0` = perfectly elastic, `0` = stick). Read per ship-vs-static contact; non-ship dynamic bodies fall back to `1.0` when the component is absent. Default `1.0` if Groovy omits it. Projected at spawn by `ShipSpawnSystem`. |
| `LargeObject` (marker) | `MPhysSystem.largeEntitySelector` | `component:LargeObject` | Wired in `GameServer.buildSystems()` as `id -> ed.getComponent(id, LargeObject.class) != null`. Partitions ECS entities between the fine `BinEntityManager` (LEAF_GRID, 32-cell) and the coarse one (TILE_GRID, 1024-cell). Set on arena ghost-cubes by `ArenaSystem` so contact-gen sees them from any fine bin within their 1024×1024 bounds. |
| `zone.conf [ZoneEnterSpawn] Arena` | `GameSessionHostedService.GameSessionImpl.resolveInitialSpawn()` | `ini:zone.conf` → `ArenaSystem.getArenaSpawn(arenaName)` | Names the arena whose `[Spawn]` to use as the connect-time spawn point. The arena's arena-local `[Spawn] X/Z` is translated to a world coord by adding `ArenaMap.min`. Falls back to world origin if `zone.conf` is missing, the key is absent, or the named arena isn't loaded. |
| `arena.conf [Spawn] X/Z` (arena-local) | `ArenaSystem.getArenaSpawn(arenaName)` | `ini:arena.conf` via `SettingsSystem.getInt` | Arena-local coords on `[0..TILE_SIZE)`. World coord = `ArenaMap.min + (X, Z)` on `GAMEPLAY_Y`. Single source of truth for both connect-time spawn (selected via `zone.conf [ZoneEnterSpawn] Arena`) and in-arena respawn (selected via the ship's own `ArenaId`). |
| `arena.conf [Spawn] X/Z` (arena-local) | `AvatarSystem.requestShipChange()` | `component:ArenaId` → `ArenaSystem.getArenaSpawn(arenaName)` → `WarpTo(target)` | Ship-change respawn target. Reads the ship's own `ArenaId`, looks up that arena's spawn world coord, and writes a `WarpTo` for `WarpSystem` to consume. Skips the warp if the ship has no `ArenaId` (no-arena void). |

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
