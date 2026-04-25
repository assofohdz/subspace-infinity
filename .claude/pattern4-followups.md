# Pattern 4 Follow-Ups

Loose ends from the Pattern 4 ship-physics + Groovy hot-reload session.
Companion to:
- [`.claude/hardcoded-values.md`](hardcoded-values.md) — magic numbers
- [`.claude/config-consumers.md`](config-consumers.md) — config-field → consumer pairs

This file is for **architectural debt**, **deferred features**, and **open questions** that don't fit either of those.

---

## 1. ~~Components written but never read~~ — **Resolved**

[`PrizeSystem`](../infinity/src/main/java/infinity/systems/PrizeSystem.java) now consumes all five upgrade triples. Each prize bumps the current-cap component by the upgrade increment, clamped at the hard-cap component:

- `ThrustMax` + `ThrustUpgrade` → THRUSTER prize → `handleAcquireThruster`
- `SpeedMax` + `SpeedUpgrade` → TOPSPEED prize → `handleAcquireTopSpeed`
- `RotationMax` + `RotationUpgrade` → ROTATION prize → `handleAcquireRotation`
- `RechargeMax` + `RechargeUpgrade` → RECHARGE prize → `handleAcquireRecharge`
- `EnergyMax` + `EnergyUpgrade` → ENERGY prize → `handleAcquireEnergy` (resolved alongside #3)

Upgrades are silent no-ops when `*Upgrade=0` (trench preset's "no upgrades" design) or when `current = max` already.

## 2. Architectural deferrals (TODOs already in code)

| Where | What | Why deferred |
|---|---|---|
| **Pattern 4 — arena resolution (`Option R2`):** [GameEntities.createShip:501](../api/src/infinity/sim/GameEntities.java#L501) (`TODO(pattern4-arena)`) · [ShipSpawnSystem class Javadoc + resolveAmbientArena](../infinity/src/main/java/infinity/settings/ShipSpawnSystem.java#L75) · [AvatarSystem:147,223](../infinity/src/main/java/infinity/systems/AvatarSystem.java#L147) | Ships don't carry their own `ArenaId` component. Three sites resolve the arena ambiently: `ShipSpawnSystem` refuses (with `log.warn`) when >1 arena is loaded; `AvatarSystem` live-config-reload picks "first loaded arena wins". | Player↔arena association isn't modelled yet. Single-arena works; multi-arena is silently wrong (AvatarSystem) or skipped entirely (ShipSpawnSystem). Fix: add an `ArenaId` component on ship creation and read it directly. |
| ~~GameEntities.createShip:520-522~~ | ~~Hardcoded `Energy/EnergyMax/Recharge` inline~~ | **Resolved** — removed alongside #3 cleanup. `ShipSpawnSystem` is now the sole projector. |
| ~~ShipSpawnSystem.projectRecharge~~ | ~~TODO comment about recharge unit conversion~~ | **Resolved** — comment is gone; the `RECHARGE_UNITS_TO_PER_SEC = 1.0/10.0` constant ([ShipSpawnSystem.java:101](../infinity/src/main/java/infinity/settings/ShipSpawnSystem.java#L101)) carries the conversion rationale in its Javadoc. |

## 3. ~~Possibly buggy: Energy projection~~ — **Resolved**

Energy now follows the same Pattern 4 split as the other stats:

- `Health` (new component) — live energy pool. Depletes from damage and weapon costs, regens via `Recharge` up to `Energy`.
- `Energy` (repurposed) — current effective cap; the value `Health` tops out at. Grown by the ENERGY prize.
- `EnergyMax` (semantic shift) — absolute hard cap on `Energy`. Read only by `PrizeSystem.handleAcquireEnergy`.
- `EnergyUpgrade` — increment for ENERGY prize.

`ShipSpawnSystem.projectEnergy` projects `Health = Energy = stat.initial()` and `EnergyMax = stat.max()` so the ENERGY prize has headroom (`Energy` grows from `initial` toward `max`). `EnergySystem` was repointed at `Health` for the live pool; `WeaponsSystem` and `WarpSystem` damage-target / has-pool filters were repointed too.

Cleaned up alongside this:

- Removed dead inline `new Energy / new EnergyMax / new Recharge` setters in `GameEntities.createShip` (sub-bullet of #2) — `ShipSpawnSystem` is now the sole projector for these stats.
- Renamed `EnergySystem.setHealthToMax` → `refillHealth` (semantic + bug fix: old impl added max to current via `newAdjusted`, would overshoot).

## 4. ~~Tuning constants that probably belong in `ShipStat` / Groovy~~ — **Resolved**

Promoted from Java globals to per-ship Groovy fields, projected through Pattern 4 (`ShipConfig` → component → consumer):

- `PlayerDriver.DRAG_FACTOR` → `ShipConfig.dragFactor()` → `DragFactor` component
- `PlayerDriver.TURN_RESPONSIVENESS` → `ShipConfig.turnResponsiveness()` → `TurnResponsiveness` component
- `ContactSystem` `contact.restitution` → `ShipConfig.bounceRestitution()` → `BounceRestitution` component (non-ship dynamic bodies still default to `1.0` when the component is absent)

Defaults when omitted from a Groovy script: `0.05 / 8.0 / 1.0` — matches the prior global values, so existing scripts keep the prior feel.

## 5. ~~Coverage gaps in `ships.groovy`~~ — **Resolved**

All 8 ships now spawn working in trench-04-2026:

- [`conf/trench-04-2026/ships.groovy`](../infinity/zone/conf/trench-04-2026/ships.groovy) declares all 8 ship blocks. Numeric stats mirror the per-ship `ship-<name>` INI fragments in the same directory.
- `GroovyShipLoader.FALLBACK` now covers all 8 ships with SVS-canonical stats so any unconfigured arena still spawns working ships.
- Feel knobs (`dragFactor`, `turnResponsiveness`, `bounceRestitution`) are uniform across ships in trench. Per-ship feel differentiation is left as future tuning work.

## 6. ~~Server-authoritative vs Subspace client-authoritative~~ — **Documentation, no action**

Background note rather than a follow-up. Captured here because it explains why the per-ship tuning numbers are what they are; not actionable on its own.

SubspaceServer is **client-authoritative** for ship physics (server just relays position packets). Infinity is **server-authoritative** (MOSS does the physics). Implications:

- Subspace's `MaximumThrust` / `MaximumSpeed` numeric values are sized for Continuum's pixel-based physics — they don't translate 1:1 to MOSS world units.
- Empirical per-ship tuning is the only path. The `dragFactor` / `turnResponsiveness` / car-curve thrust model are good architectural choices regardless of unit scaling — and per #4 they're now per-ship Groovy fields, so empirical tuning has a home.
- The "single global scaling constant" idea (bridge Subspace integers → MOSS units) was floated but not built. Per-ship tuning supersedes it: a single multiplier can't capture the fact that different ships need different feel curves under MOSS's force-based physics, which is exactly what #4's Pattern-4 promotion already enables.

If a concrete physics-tuning task ever needs to be tracked, file it as a new numbered item — this section is just orientation.

## 7. ~~Future feature: live ship-config reload across all ships~~ — **Resolved**

Live reload is now triggered by a per-arena file watcher in [`ArenaSystem`](../infinity/src/main/java/infinity/systems/ArenaSystem.java) — no ship-change action required.

- On arena load, `ArenaSystem.registerScriptWatch` resolves the arena's `ships.groovy` to its on-disk path (via the new `GroovyShipLoader.resolveOnDisk` helper) and snapshots the file's mtime. No-op when only the classpath copy is reachable (production / packaged jar).
- Each tick, `ArenaSystem.pollScriptWatches` stats every watched file. If the mtime changed, it re-evaluates the Groovy via `shipLoader.apply(...)` and calls [`ShipSpawnSystem.reprojectAll()`](../infinity/src/main/java/infinity/settings/ShipSpawnSystem.java) which writes the latest `ShipConfig`-derived components directly via `ed.setComponent` for every ship in scope. Cost: one stat() syscall per loaded arena per tick — cheap enough to skip throttling.
- On arena unload, `unregisterScriptWatch` drops the entry.
- The previous workaround in `AvatarSystem.requestShipChange` (reload + reproject on key 1–8) was removed; the file watcher subsumes it. `AvatarSystem` no longer depends on `GroovyShipLoader`.

Live updates are pure ECS component writes, thread-safe in Zay-ES, so the sim-thread poll can push new tuning out without any extra synchronisation.

## 8. ~~Future feature: upgrade pickup system~~ — **Resolved (closed by #1)**

`PrizeSystem` now handles all 5 stat-upgrade prizes (Thruster / TopSpeed / Rotation / Recharge / Energy). Each handler reads the `*Upgrade` and `*Max` components projected by `ShipSpawnSystem` and bumps the matching current-cap component, clamped at `*Max`.

**By design, prizes never mutate the `*Max` components.** Hard caps are read-only at runtime — the only way to change them is to edit the per-ship Groovy block and live-reload (which re-projects the whole ShipConfig at spawn). This was the explicit intent: Groovy is the live-tuning surface for the absolute ceilings; in-game prizes only push the current cap toward those ceilings.

## 9. ~~Cosmetic: `ShipSpawnSystem.applyConfigTo` logs at INFO~~ — **Resolved**

Both spammy lines demoted to `log.debug` and gated behind `isDebugEnabled()` so the formatter doesn't run when logging at INFO:

- `ShipSpawnSystem.applyConfigTo` — "Projected ShipConfig for ..."
- `PlayerDriver.update` — "Stats refreshed for entity ..."

Re-enable per-package via `LOG_LEVEL` / logback when debugging the dev loop.

## 10. Bug: wall bounce imparts angular velocity

When a ship clips a wall, MOSS's contact resolution applies an angular impulse from the off-center contact point — the ship spins after a glancing hit. We want **velocity-only bounce, no rotational bounce**: the player owns the ship's heading via input, walls should only flip the linear velocity component.

**Hypothesis (need to verify):** the angular kick is coming from MOSS computing the bounce impulse with friction/lever-arm at the contact point. Even with our `BounceRestitution` controlling the linear coefficient, angular response is a separate channel.

**Possible fixes:**
- In `PlayerDriver.update()`, after the existing rotation easing, snap angular velocity back to the input-driven `targetAng` so any contact-induced spin is overwritten the next tick. Cheapest patch — the player's intent already wins one tick later anyway.
- In `ContactSystem.newContact()` for ship-vs-static, zero the contact's tangential/friction term (if MOSS exposes it) so no torque is generated in the first place. Cleaner but needs to dig into MOSS contact API.
- Set the ship body's angular inertia to ~∞ at spawn so collisions can't spin it. Loses any future rotational physics we might want.

Worth a per-ship `angularBounce` knob (`0` = no rotational response, `1` = full physical) if we ever want heavier ships to feel different on impact — but for MVP, just kill it.

## 11. Migrate `zone.conf` and `arena.conf` to Groovy

[`infinity/zone/zone.conf`](../infinity/zone/zone.conf) and [`infinity/zone/arenas/<name>/arena.conf`](../infinity/zone/arenas/) are still INI-style with `#include` directives and string-keyed `SettingsSystem` lookups. Migrate both to Groovy to match [`ships.groovy`](../infinity/zone/conf/trench-04-2026/ships.groovy):

- Same per-arena live-reload story (filesystem-first read in dev mode, classpath fallback for packaged jars).
- Type-checked DSL via a typed builder (cf. `GroovyShipLoader.ShipConfigBuilder`) instead of opaque string keys with default values scattered across consumers.
- IDE autocomplete + Groovy compile-error feedback when editing.
- Closes the loop on always-on rule #5: it makes Groovy genuinely the only place tuning knobs live, instead of "Groovy for ships, INI for everything else".

**Approach (sketch):**

1. Decide on the typed binding for each tier — likely a `ZoneConfig` record + `ZoneConfigLoader` for zone scope, and an `ArenaConfig` record + `ArenaConfigLoader` for arena scope (parallel structure to `ShipConfig` / `GroovyShipLoader`).
2. Wire the loaders into the existing arena-load and zone-startup paths so Groovy and INI can coexist while the migration ramps.
3. Port settings one fragment at a time. Keep the existing `SettingsSystem` typed accessors as the consumer-facing API initially — back them with the Groovy-derived registry instead of the INI parser. Once all callers use typed accessors, the INI parser can be deleted.
4. Update the [`arena-settings`](../.claude/skills/arena-settings/) skill to point at the new authoring surface.

Out of scope for this item: the per-preset `conf/<preset>/*.conf` fragments under [`infinity/zone/conf/`](../infinity/zone/conf/). Larger surface, separate migration; track as a future item if needed.

## 12. Deprecate the Java `AdaptiveLoader` hot-module system in favour of Groovy

The current dynamic-module surface is a custom Java classloader + service stack:

- [`api/src/infinity/sim/AdaptiveLoader.java`](../api/src/infinity/sim/AdaptiveLoader.java) — interface threaded through `BaseGameService` / `BaseGameModule` constructors and into every `modules/.../*Tester.java` (basicTester, lightTester, prizeTester, doorTester, wangTester, warpTester, ...).
- [`infinity/src/main/java/infinity/util/AdaptiveLoadingService.java`](../infinity/src/main/java/infinity/util/AdaptiveLoadingService.java) — `AbstractHostedService` that loads/instantiates/enables/disables modules at runtime and binds chat commands to do it.
- [`infinity/src/main/java/infinity/util/AdaptiveClassLoader.java`](../infinity/src/main/java/infinity/util/AdaptiveClassLoader.java) — the custom `ClassLoader`.
- Wired in [`GameServer.java:312-314`](../infinity/src/main/java/infinity/server/GameServer.java#L312).

Reasons to migrate to Groovy:

- One reload mechanism, not two. We already have `GroovyShipLoader` + the per-arena file watcher (#7); a Groovy-defined module would slot into the same pattern (filesystem-first read, mtime poll, re-evaluate, re-apply).
- No bespoke `ClassLoader` to maintain — Groovy's `GroovyShell` / `GroovyClassLoader` handles isolation and reload semantics.
- Authoring surface matches always-on rule #5 (Groovy is the configuration / scripting tier).
- Smaller attack surface — modules become scripts read from disk, not arbitrary `.class` files dropped in.

**Approach (sketch — large item, do incrementally):**

1. Pick one of the simpler `*Tester` modules as the migration prototype (e.g. `doorTester` or `warpTester`) and re-express it as a Groovy script under `infinity/zone/conf/<preset>/modules/` (or a dedicated `modules/` tier — TBD alongside #11).
2. Add a `GroovyModuleLoader` parallel to `GroovyShipLoader`: typed DSL for declaring a module's lifecycle hooks (`onLoad`, `onTick`, `onChat`, ...), backed by a `ModuleConfig` record / registry.
3. Wire a per-arena file watcher (extension of `ArenaSystem.pollScriptWatches` or a sibling poll) so a saved Groovy module reloads with the same semantics as `ships.groovy`.
4. Port the remaining `*Tester` modules one at a time. Each port deletes its Java source and the matching `AdaptiveLoader` constructor parameter from the call site.
5. Once `BaseGameService` / `BaseGameModule` no longer need `AdaptiveLoader` injected, delete the three Java files and the `GameServer` wiring.

Cross-link: this item is the larger sibling of #11 — both are about consolidating on Groovy as the single dev-time surface. Ordering: #11 first (config), then #12 (modules), so the module loader can read from the same Groovy infrastructure the config tier already uses.
