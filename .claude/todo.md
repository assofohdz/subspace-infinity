# Project TODO

Open architectural debt, deferred features, and follow-up tunings. Companion to:
- [`.claude/hardcoded-values.md`](hardcoded-values.md) — magic numbers
- [`.claude/config-consumers.md`](config-consumers.md) — config-field → consumer pairs

This file is for **open work** only. Resolved items are removed; rationale that's worth preserving lives in the affected source's javadoc/comments. Open items have a corresponding GitHub issue tagged in the section header where applicable.

---

## 1. Migrate `zone.conf` and `arena.conf` to Groovy — tracked as [GH #101](https://github.com/assofohdz/subspace-infinity/issues/101)

[`infinity/zone/zone.conf`](../infinity/zone/zone.conf) and [`infinity/zone/arenas/<name>/arena.conf`](../infinity/zone/arenas/) are still INI-style with `#include` directives and string-keyed `SettingsSystem` lookups. Migrate both to Groovy to match [`ships.groovy`](../infinity/zone/conf/trench-04-2026/ships.groovy):

- Same per-arena live-reload story (filesystem-first read in dev mode, classpath fallback for packaged jars).
- Type-checked DSL via a typed builder (cf. `GroovyShipLoader.ShipConfigBuilder`) instead of opaque string keys with default values scattered across consumers.
- IDE autocomplete + Groovy compile-error feedback when editing.
- Closes the loop on always-on rule #5: it makes Groovy genuinely the only place tuning knobs live, instead of "Groovy for ships, INI for everything else".

**Approach (sketch):**

1. Decide on the typed binding for each tier — likely a `ZoneConfig` record + `ZoneConfigLoader` for zone scope, and an `ArenaConfig` record + `ArenaConfigLoader` for arena scope (parallel structure to `ShipConfig` / `GroovyShipLoader`).
2. Wire the loaders into the existing arena-load and zone-startup paths so Groovy and INI can coexist while the migration ramps.
3. Port settings one fragment at a time. Keep the existing `SettingsSystem` typed accessors as the consumer-facing API initially — back them with the Groovy-derived registry instead of the INI parser. Once all callers use typed accessors, the INI parser can be deleted.
4. Update the [`arena-settings`](skills/arena-settings/) skill to point at the new authoring surface.

Out of scope: the per-preset `conf/<preset>/*.conf` fragments under [`infinity/zone/conf/`](../infinity/zone/conf/). Larger surface, separate migration; track as a future item if needed.

## 2. Deprecate the Java `AdaptiveLoader` hot-module system in favour of Groovy — tracked as [GH #63](https://github.com/assofohdz/subspace-infinity/issues/63)

The current dynamic-module surface is a custom Java classloader + service stack:

- [`api/src/infinity/sim/AdaptiveLoader.java`](../api/src/infinity/sim/AdaptiveLoader.java) — interface threaded through `BaseGameService` / `BaseGameModule` constructors and into every `modules/.../*Tester.java` (basicTester, lightTester, prizeTester, doorTester, wangTester, warpTester, ...).
- [`infinity/src/main/java/infinity/util/AdaptiveLoadingService.java`](../infinity/src/main/java/infinity/util/AdaptiveLoadingService.java) — `AbstractHostedService` that loads/instantiates/enables/disables modules at runtime and binds chat commands to do it.
- [`infinity/src/main/java/infinity/util/AdaptiveClassLoader.java`](../infinity/src/main/java/infinity/util/AdaptiveClassLoader.java) — the custom `ClassLoader`.
- Wired in [`GameServer.java:312-314`](../infinity/src/main/java/infinity/server/GameServer.java#L312).

Reasons to migrate to Groovy:

- One reload mechanism, not two. We already have `GroovyShipLoader` + the per-arena file watcher; a Groovy-defined module would slot into the same pattern (filesystem-first read, mtime poll, re-evaluate, re-apply).
- No bespoke `ClassLoader` to maintain — Groovy's `GroovyShell` / `GroovyClassLoader` handles isolation and reload semantics.
- Authoring surface matches always-on rule #5 (Groovy is the configuration / scripting tier).
- Smaller attack surface — modules become scripts read from disk, not arbitrary `.class` files dropped in.

**Approach (sketch — large item, do incrementally):**

1. Pick one of the simpler `*Tester` modules as the migration prototype (e.g. `doorTester` or `warpTester`) and re-express it as a Groovy script under `infinity/zone/conf/<preset>/modules/` (or a dedicated `modules/` tier — TBD alongside #1).
2. Add a `GroovyModuleLoader` parallel to `GroovyShipLoader`: typed DSL for declaring a module's lifecycle hooks (`onLoad`, `onTick`, `onChat`, ...), backed by a `ModuleConfig` record / registry.
3. Wire a per-arena file watcher (extension of `ArenaSystem.pollScriptWatches` or a sibling poll) so a saved Groovy module reloads with the same semantics as `ships.groovy`.
4. Port the remaining `*Tester` modules one at a time. Each port deletes its Java source and the matching `AdaptiveLoader` constructor parameter from the call site.
5. Once `BaseGameService` / `BaseGameModule` no longer need `AdaptiveLoader` injected, delete the three Java files and the `GameServer` wiring.

Cross-link: this item is the larger sibling of #1 — both are about consolidating on Groovy as the single dev-time surface. Ordering: #1 first (config), then #2 (modules), so the module loader can read from the same Groovy infrastructure the config tier already uses.

## 3. Promote `ContactSystem` ship-vs-wall `friction` to a per-ship Groovy knob

[`ContactSystem.newContact`](../infinity/src/main/java/infinity/systems/ContactSystem.java) currently hardcodes `contact.friction = 0.0` for ship-vs-static contacts. Promote to a per-ship `ShipConfig.wallFriction()` → `WallFriction` component, parallel to the existing `BounceRestitution` field:

- New `WallFriction` component in `api/src/infinity/es/ship/`.
- New `wallFriction` field on `ShipConfig` + matching DSL setter on `GroovyShipLoader.ShipConfigBuilder` (default `0.0` to match current behavior; future ships could choose grippy/slidey feel).
- `ShipSpawnSystem.projectFeel` projects it onto the ship.
- `ContactSystem` reads it off `bodyOne.id` in the wall-bounce branch (parallel to the `BounceRestitution` lookup already there); fall back to `0.0` when the component is absent.
- Update `.claude/ship-config-dictionary.md` "Infinity-only Groovy fields" table and `.claude/config-consumers.md` per always-on rules #4 / #6.

Cheap follow-up — pure parallel to existing pattern, no design decisions.

## 4. Multi-arena membership — deferred sub-items

Main work landed 2026-04-26 (cube-sensor + `ContactSystem` fan-out, large-static bin index for full-footprint coverage, ECS-driven reprojection on arena cross, ZoneEnterSpawn pointer to per-arena spawn). These remain:

- **Wire the new mphys large-static collision filter so only ships generate contacts with the coarse pass.** moss-side change has landed in `PhysicsSpace`: a `Predicate<RigidBody>` body filter (`setLargeStaticCollisionFilter` / `getLargeStaticCollisionFilter`) consulted in `generateContacts` *per body per frame* — opted-out bodies skip the entire coarse pass before narrow phase. Today the only `LargeObject` is the arena ghost-cube and the only useful counter-party is a ship (ContactSystem disables sensor pairs anyway, and `ArenaMembershipSystem.newContact` early-returns on bodies without `ShipType`). Every projectile / mob / non-ship dynamic body that overlaps an arena cube footprint right now generates a coarse-pass contact pair we throw away.
  - **Approach.** Add an opt-in marker (e.g. `CollidesWithLargeStatics` in `api/src/infinity/es/`) and set it on ships at spawn (`GameEntities.createPlayerShip` / mob spawn). In `GameServer.buildSystems` after `MPhysSystem` is registered, call:
    ```java
    mphys.getPhysicsSpace().setLargeStaticCollisionFilter(
        body -> ed.getComponent(body.id, CollidesWithLargeStatics.class) != null);
    ```
    Positive marker chosen over a negative `IgnoresLargeStatics` because ships are the minority of dynamic bodies — opt-in keeps the marker count small. Reconsider if a future `LargeObject` (e.g. a solid wall, not a sensor) needs projectiles to participate too; the marker becomes "this body cares about large structures in general", and projectiles get it.
  - **Verify.** `STAT_CONTACTS` (and `STAT_CONTACT_GEN_TIME`) before/after with a populated arena — expect roughly `(non-ship-dynamic-bodies-in-arena-footprint × loaded-arenas)` fewer pairs per frame.
  - **Hot-path note.** Predicate runs per active body per frame; component lookup is HashMap-ish so it's fine at thousands of bodies. If profiling flags it, cache the answer at body-creation time — `InfinityEntityBodyFactory.createRigidBody(id)` reads the marker once and stashes a `boolean` on a `RigidBody` subclass; the predicate becomes `body -> ((MyBody) body).collidesLarge`, a field read.
  - **Side effect.** Closes the "Penetration discriminator / contact-volume tuning" item below for the arena-cube case (per-body opt-out is a stronger throttle than per-pair penetration filtering). The penetration filter remains the obvious next-step throttle for *interactive* large statics where ships still generate the pairs but want to avoid resolving inside-the-cube contacts.
- **Bug 2 — `WarpSystem`-zeros-velocity → ship-sleeps → exit-grace-fires-redundant-leave.** A ship that sits perfectly still inside its own arena cube for 60+ frames (e.g. AFK on spawn) has its bin drop out of `getActiveBins`, contact-gen stops firing for it, and the per-tick exit-grace sweep fires `left arena <name>` even though the ship hasn't moved. `ArenaId` is preserved by the conditional clear in `ArenaMembershipSystem.fireLeftArena`, so functionally correct; just one stray log line. Movement reactivates contact and a fresh `entered arena` fires. Fix path: add `ArenaMembershipSystem.markEntered(ship, arenaEntity)` and have `WarpSystem.update` call it after reconciling `ArenaId` so contact-driven and warp-driven membership stay aligned without depending on the body being awake.
- **Health-on-cross caveat.** `ShipSpawnSystem.applyConfigTo` rewrites `Health = stat.initial()` whenever the watched `(ShipType, ArenaId)` set fires an add/change. Fine for ship-swap (you got a new ship); surprising for arena-cross (your damaged ship just got full health back). Documented in [`ShipSpawnSystem`'s class javadoc](../infinity/src/main/java/infinity/settings/ShipSpawnSystem.java). Split into a "tuning reprojection" (caps + feel only) vs "respawn reprojection" (caps + feel + Health/Energy reset) when the gameplay implication actually matters.
- **Penetration discriminator / contact-volume tuning.** Post-large-static the arena cube generates a contact pair with every ship inside its footprint *every frame* — by design (state-diff handles enter/leave; the contact is sensor-disabled before the resolver), but it does mean per-frame contact-gen scales with `(ships in arena × loaded arenas)`. Acceptable today; revisit if `STAT_CONTACTS` shows pressure or if many sensor zones are added. Paul Speed's "`contact.penetration < shipRadius` filter" remains the obvious throttle.
- **Plumb `ShapeInfo.scale` to client `createModel`.** The client cube edge in [`SISpatialFactory.createArena`](../infinity/src/main/java/infinity/client/states/SISpatialFactory.java) is hardcoded to `1024` to match `TILE_SIZE`. Plumb the scale through `ModelViewState.createModel` if we want the visualization to track server cube changes automatically.
