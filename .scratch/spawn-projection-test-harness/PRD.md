# Spawn-projection test harness

Status: ready-for-human (slice 0 done — four pillars wired)

End-to-end ECS flows have zero automated coverage today. Every CCP / spawn / prize / energy refactor is verified by manual game launch — see [`project_spawn_projection_test_gap.md`](../../.claude/projects/-home-assofohdz-github-assofohdz-subspace-infinity/memory/project_spawn_projection_test_gap.md). This PRD closes that gap by introducing a minimal SiO2 `GameSystemManager` test fixture that boots only the systems under test, projects a synthetic `ConfigRegistry` snapshot, and asserts the resulting ECS components match.

## Why

- The conf-fragments-to-groovy work just shipped a hot-reload path that touches `SettingListener` events and the merged `Ini` store; no automated test exercises it. The gate before commit was a manual launch.
- Every CCP cluster still pending (damage / projectile speeds / decays / cooldowns / health / etc.) follows the same shape: template → spawn-time projection → ECS component. Each of those is a candidate for the same kind of programmatic round-trip test.
- The cost of "manual launch is the only verification" compounds — slows iteration, makes refactors riskier, makes regressions invisible until somebody loads the right arena.

## Out of scope

- **Networking, RMI, physics, map loading.** The harness boots systems in-process; nothing crosses a wire.
- **Client-side systems.** Server-side projection only.
- **Behavioural simulation.** The harness ticks once or twice for ECS change-fanout; it does not run gameplay loops.
- **Replacing manual launch entirely.** Some flows (rendering, input, network sync, lvl loading) still need a real launch. The harness covers the projection layer and the ECS contract, not the JME pipeline.

## Architecture

```
JUnit @Test
   │
   ├── builds ShipConfig via existing record constructors / ShipConfigBuilder
   │
   ├── new GameSystemManager
   │     ├── register(EntityData.class, new DefaultEntityData())
   │     ├── addSystem(ConfigRegistrySystem)
   │     └── addSystem(ShipSpawnSystem)
   │
   ├── manager.initialize() + start()
   │
   ├── arrange:
   │     ├── ConfigRegistrySystem.replace(arenaId, snapshot)
   │     └── ed.createEntity() + setComponent(ShipType, ArenaId)
   │
   ├── act: manager.update()  // one tick — ShipSpawnSystem applies changes + projects
   │
   └── assert: ed.getComponent(shipId, Thrust.class).getThrust() == expected, …
```

No `MapSystem`, no `ArenaSystem`, no `PrizeSpawnerSystem`/`PrizeConsumptionSystem`/`DeathPrizeSystem`, no physics, no chat. The ship's `ArenaId` is synthetic — `ConfigRegistrySystem.replace(arenaId, snapshot)` is the entire arena-load substitute.

## Slices

Each slice adds one independently-testable flow. Land them as separate PRs so each one's diff stays reviewable.

| # | Slice | Asserts |
|---|---|---|
| **1** | **`ShipSpawnSystem` respawn projection** ✅ | A fully-specified WARBIRD `ShipConfig` projected onto a fresh ship entity produces the documented components — Thrust / Speed / Rotation / Recharge / Energy triples, Health, feel knobs, weapons, inventory. Establishes the harness pattern. |
| **1b** | **Prize-pickup pillar** ✅ | `RepelPrizeApplierTest` covers below-cap, at-cap, and disallowed branches by invoking the applier directly against `DefaultEntityData`. Establishes the no-`GameSystemManager` shape future Count-family applier tests follow. |
| **1c** | **Projectile-spawn pillar** ✅ | `BulletFactoryTest` constructs a real `PhysicsSpace` from a single-cell `Grid` and asserts `WeaponFactory.createBullet` projects `BulletConfig.decayMs()` into a `Decay` deadline + Parent ownership; the per-level damage formula on `BulletConfig` is asserted at the seam where `WeaponsSystem` reads it. |
| **1d** | **RaM intent-drain pillar** ✅ | `EnergySystemIntentTest` boots `GameSystemManager` + `EnergySystem`, emits `(Buff + HealthChange)` intent entities, ticks once, and asserts the canonical writer folds them into a single `Health` replacement and reaps the intent entity. Pins the intent-emission → drain → final-state round-trip that `replacement-as-mutation.md` formalizes. |
| 2 | **Tuning projection (no live-pool reset)** ✅ | `ShipSpawnSystemTuningProjectionTest` damages + ammo-burns a ship, then triggers `reprojectAll` (and separately, an `ArenaId` rewrite without `ResetLivePool`). Asserts Energy + Bomb/Bullet/Mine/Burst/Thor/Repel current counts survive while `ThrustStats`, `EnergyStats.hardMax`, and every `*Stats.max` pick up the new snapshot. |
| 4 | **No-config fallback** ✅ | `ShipSpawnSystemNoConfigFallbackTest` covers three angles: fresh ship in an unregistered arena (no projection at all), ship moved from a configured arena to an unregistered one (prior components retained), and a ship-type missing from the snapshot (`getShip()` returns null → same fallback). All three log "leaving defaults" and skip projection. |
| 5 | **Pattern-4 candidate slices** | One slice per cluster as those CCP migrations land — Damage, Projectile speeds, Decays, etc. Each is a carbon copy of slice 1 but for a different `*Config` record + spawn system. |

## Locked-in design decisions

1. **One file per system under test.** `ShipSpawnSystemTest.java` for slice 1; future spawn systems (PrizeSpawnerSystem, projectile spawns when those land) get their own files. Avoids one giant "harness" class that grows unbounded.
2. **Fixtures inline, not shared.** Each test builds its own `ShipConfig` so the assertions are local. If duplication gets painful, factor a `ShipConfigFixtures` helper later — not preemptively.
3. **`DefaultEntityData` is the ECS backend.** Same class `GameServer` uses in production. No mock entity data — that drift would be the bug the harness should catch, not the bug the harness introduces.
4. **No `@Before` / `@After` factory methods until repetition justifies it.** Each test stands up its own `GameSystemManager`, calls `terminate()` in a `try/finally`. JUnit 4 still — matches `MapSwapReproducerTest` (`infinity-server/src/test/`) and the Groovy loader tests there; the factory tests themselves live in `api/src/test/java/infinity/sim/`.
5. **Live-tune fields are not magic.** Memory says only `ShipSpawnSystem` writes the `*Max` components. The harness preserves that — tests only mutate components the production code mutates.

## Comments

### Test harness for spawn / projection / prize flows — slice 0 done

Tracked in [`spawn-projection-test-harness/PRD.md`](../spawn-projection-test-harness/PRD.md). Four pillars shipped: ship-spawn projection (`ShipSpawnSystemTest`), prize pickup (`RepelPrizeApplierTest`), projectile spawn (`BulletFactoryTest`), and RaM intent-drain (`EnergySystemIntentTest`). Slice 3 also landed (`ShipSpawnSystemHotReloadTest`) — pins the `ConfigRegistrySystem.replace` → `ShipSpawnSystem.reprojectAll` seam the Groovy hot-reload path depends on (capability stats track new snapshot, live pools preserved, per-arena isolation, observer EntitySets surface the changed ship). The `GameSystemManager` + `DefaultEntityData` + `ConfigRegistrySystem` fixture, the no-system applier fixture, the `PhysicsSpace`-from-`Grid` factory fixture, and the GSM-with-intent-emission fixture are all callable templates for the remaining slices. Remaining: tuning projection (no live-pool reset), no-config fallback, and one-per-cluster CCP candidates as those migrations land.

The pipeline tracker's Test column can now honestly flip to ✅ for rows whose mechanics are exercised by these four pillars.