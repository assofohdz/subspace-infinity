# Pattern 4 Follow-Ups

Loose ends from the Pattern 4 ship-physics + Groovy hot-reload session.
Companion to:
- [`.claude/hardcoded-values.md`](hardcoded-values.md) — magic numbers
- [`.claude/config-consumers.md`](config-consumers.md) — config-field → consumer pairs

This file is for **architectural debt**, **deferred features**, and **open questions** that don't fit either of those.

---

## 1. Components written but never read

`ShipSpawnSystem.project*` writes these, but no system reads them yet. They're wired for the upgrade system that doesn't exist yet:

- `ThrustMax`, `ThrustUpgrade`
- `SpeedMax`, `SpeedUpgrade`
- `RotationMax`, `RotationUpgrade`
- `RechargeMax`, `RechargeUpgrade`
- `EnergyUpgrade`

When an upgrade pickup system lands, it'll read these to clamp the corresponding "current cap" component (`Thrust`, `Speed`, etc.). Until then they're inert ghost wires.

## 2. Architectural deferrals (TODOs already in code)

| Where | What | Why deferred |
|---|---|---|
| [GameEntities.createShip:504](../api/src/infinity/sim/GameEntities.java#L504) | Ships don't carry their own `ArenaId` ("Option R2" — ambient arena lookup) | Player↔arena association isn't modelled yet. Single-arena works; multi-arena is silently broken. |
| [ShipSpawnSystem.java](../infinity/src/main/java/infinity/settings/ShipSpawnSystem.java) class Javadoc | Ambient arena resolution refuses (with `log.warn`) if >1 arena is loaded | Same root cause as above. |
| [GameEntities.createShip:520-522](../api/src/infinity/sim/GameEntities.java#L520) | Hardcoded `Energy(SHIPHEALTH)`, `EnergyMax(SHIPHEALTH × 2)`, `Recharge(100)` set inline at spawn | Now functionally dead — `ShipSpawnSystem` overwrites them on the next tick. Trivial cleanup. |
| [ShipSpawnSystem.java:212-216](../infinity/src/main/java/infinity/settings/ShipSpawnSystem.java#L212) `projectRecharge` | TODO comment about recharge unit conversion (still present despite the `/10` constant) | Cosmetic — comment outdated, conversion is now verified. |

## 3. Possibly buggy: Energy projection

[ShipSpawnSystem.projectEnergy](../infinity/src/main/java/infinity/settings/ShipSpawnSystem.java) sets `EnergyMax = stat.max()`. Per Subspace semantics, a fresh ship's `EnergyMax` should be `stat.initial()` (since Subspace `InitialEnergy = current max HP at spawn`, growing toward `MaximumEnergy` via upgrades).

Doesn't manifest in `trench-04-2026` because `energy initial: 1500, max: 1500` — they're equal. Would manifest if SVS canonical values (1000/1700) are used.

**Decision needed:** before adding ships beyond Warbird, decide if EnergyMax-at-spawn should use `initial` or `max`.

## 4. ~~Tuning constants that probably belong in `ShipStat` / Groovy~~ — **Resolved**

Promoted from Java globals to per-ship Groovy fields, projected through Pattern 4 (`ShipConfig` → component → consumer):

- `PlayerDriver.DRAG_FACTOR` → `ShipConfig.dragFactor()` → `DragFactor` component
- `PlayerDriver.TURN_RESPONSIVENESS` → `ShipConfig.turnResponsiveness()` → `TurnResponsiveness` component
- `ContactSystem` `contact.restitution` → `ShipConfig.bounceRestitution()` → `BounceRestitution` component (non-ship dynamic bodies still default to `1.0` when the component is absent)

Defaults when omitted from a Groovy script: `0.05 / 8.0 / 1.0` — matches the prior global values, so existing scripts keep the prior feel.

## 5. Coverage gaps in `ships.groovy`

[`conf/trench-04-2026/ships.groovy`](../infinity/zone/conf/trench-04-2026/ships.groovy) has only Warbird configured. Ships 2–8 (Javelin, Spider, Leviathan, Terrier, Weasel, Lancaster, Shark) fall through to `GroovyShipLoader.FALLBACK` which is also Warbird-only — so picking those ships in-game will spawn an unconfigured ship that can't move (zero stats).

**Action:** add `ship(Ship.JAVELIN) { ... }` etc. for the other 7 ship types.

## 6. Server-authoritative vs Subspace client-authoritative

SubspaceServer is **client-authoritative** for ship physics (server just relays position packets). Infinity is **server-authoritative** (MOSS does the physics). Implications:

- Subspace's `MaximumThrust` / `MaximumSpeed` numeric values are sized for Continuum's pixel-based physics — they don't translate 1:1 to MOSS world units.
- Empirical tuning is the only path. The DRAG_FACTOR / TURN_RESPONSIVENESS / car-curve thrust model are good architectural choices regardless of unit scaling.
- A single global "scaling constant" idea was floated as a way to bridge Subspace integers to MOSS units. Not yet built — currently the values flow through unchanged.

## 7. Future feature: live ship-config reload across all ships

Right now pressing 1–8 reloads `ships.groovy` and reprojects to **the caller's ship only**. Other ships in the arena keep their stale stats until they too pick a key. A hot-reload that walks all ship entities in the arena and re-projects each one would be cleaner — but needs care around thread safety (chat thread vs sim update thread).

## 8. Future feature: upgrade pickup system

Not started. Would consume `*Upgrade` and `*Max` components (item 1 above) to mutate the corresponding "current cap" components when a player picks up a thrust/speed/rotation/recharge/energy prize.

## 9. Cosmetic: `ShipSpawnSystem.applyConfigTo` logs at INFO

[ShipSpawnSystem.java:142-153](../infinity/src/main/java/infinity/settings/ShipSpawnSystem.java#L142) — every ship change emits `log.info("Projected ShipConfig for ...")`. Useful while debugging the dev loop, noisy in production. Demote to `log.debug` once the loop is stable.

Same for `PlayerDriver.update()` — the `log.info("Stats refreshed for entity ...")` block at every `applyChanges()` will be very chatty.

## 10. Bug: wall bounce imparts angular velocity

When a ship clips a wall, MOSS's contact resolution applies an angular impulse from the off-center contact point — the ship spins after a glancing hit. We want **velocity-only bounce, no rotational bounce**: the player owns the ship's heading via input, walls should only flip the linear velocity component.

**Hypothesis (need to verify):** the angular kick is coming from MOSS computing the bounce impulse with friction/lever-arm at the contact point. Even with our `BounceRestitution` controlling the linear coefficient, angular response is a separate channel.

**Possible fixes:**
- In `PlayerDriver.update()`, after the existing rotation easing, snap angular velocity back to the input-driven `targetAng` so any contact-induced spin is overwritten the next tick. Cheapest patch — the player's intent already wins one tick later anyway.
- In `ContactSystem.newContact()` for ship-vs-static, zero the contact's tangential/friction term (if MOSS exposes it) so no torque is generated in the first place. Cleaner but needs to dig into MOSS contact API.
- Set the ship body's angular inertia to ~∞ at spawn so collisions can't spin it. Loses any future rotational physics we might want.

Worth a per-ship `angularBounce` knob (`0` = no rotational response, `1` = full physical) if we ever want heavier ships to feel different on impact — but for MVP, just kill it.
