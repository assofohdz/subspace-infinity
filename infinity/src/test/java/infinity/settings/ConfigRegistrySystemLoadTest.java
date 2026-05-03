// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.Ship;
import infinity.config.ArenaConfig;
import infinity.es.arena.ArenaId;
import infinity.systems.SettingsSystem;
import java.util.List;
import org.junit.Test;

/**
 * Orchestration smoke for slice B0 — boots a minimal {@link GameSystemManager}
 * with the systems {@link ConfigRegistrySystem#load} depends on
 * ({@link SettingsSystem}, {@link GroovyShipLoader}, {@link GroovyWeaponsLoader}),
 * builds an {@link ArenaConfig} pointing at the real trench-04-2026 preset
 * fragments, and asserts the resulting snapshot has all three phases populated:
 * ships from {@code ships.groovy}, weapons from {@code misc.groovy} via the
 * compat shim, prize from {@code misc.groovy}.
 *
 * <p>Pre-B0, this orchestration was spread across {@code ArenaSystem.bootstrap}
 * + {@code applyWeaponsConfig}. The test pins the new entry point at
 * {@link ConfigRegistrySystem#load} so a regression in the seam (missing
 * collaborator wiring, wrong phase order) shows up immediately.
 */
public class ConfigRegistrySystemLoadTest {

  @Test
  public void load_realTrenchPreset_populatesShipsWeaponsAndPrize() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);

    // Registration order matches GameServer: ConfigRegistrySystem first (no
    // deps), then its collaborators. ConfigRegistrySystem.initialize() pulls
    // all three via getSystem(), so they must all be registered before the
    // manager initializes.
    final ConfigRegistrySystem registry =
        systems.register(ConfigRegistrySystem.class, new ConfigRegistrySystem());
    systems.register(GroovyShipLoader.class, new GroovyShipLoader(registry));
    systems.register(SettingsSystem.class, new SettingsSystem());
    systems.initialize();
    systems.start();

    try {
      final ArenaId arenaId = new ArenaId("trench", EntityId.NULL_ID);
      final ArenaConfig arenaConfig =
          new ArenaConfig(
              "trench.lvl",
              "/conf/trench-04-2026/ships.groovy",
              512,
              512,
              List.of(
                  "/conf/trench-04-2026/misc.groovy",
                  "/conf/trench-04-2026/bullet.groovy",
                  "/conf/trench-04-2026/bomb.groovy",
                  "/conf/trench-04-2026/mine.groovy",
                  "/conf/trench-04-2026/burst.groovy",
                  "/conf/trench-04-2026/repel.groovy",
                  "/conf/trench-04-2026/rocket.groovy",
                  "/conf/trench-04-2026/brick.groovy",
                  "/conf/trench-04-2026/decoy.groovy",
                  "/conf/trench-04-2026/portal.groovy",
                  "/conf/trench-04-2026/prize.groovy",
                  "/conf/trench-04-2026/prize-weights.groovy"),
              0.0,
              List.of());

      registry.load(arenaId, arenaConfig);

      final ConfigRegistry snapshot = registry.forArena(arenaId);
      assertNotNull("snapshot must be installed", snapshot);

      // Phase 2 — ships.groovy populated trench's per-ship templates.
      assertTrue(
          "trench preset configures every Subspace ship",
          snapshot.configuredShips().size() >= 8);
      final var warbird = snapshot.getShip(Ship.WARBIRD);
      assertNotNull("WARBIRD config from ships.groovy", warbird);
      assertEquals(
          "WARBIRD initial thrust from trench ships.groovy",
          16,
          warbird.thrust().initial());

      // Per-ship inventory (post-B2-Migration): trench/warbird is a
      // gun-only build — no bombs/mines/repels/bursts/bricks/rockets/portals
      // (Q6 maps Subspace `*Max 0` to null = disallow). Has guns, thors,
      // decoys per ship-warbird.groovy's authored values.
      assertNotNull("WARBIRD has guns", warbird.guns());
      assertEquals("WARBIRD MaxGuns = 3 (LEVEL_3)",
          infinity.GunLevel.LEVEL_3, warbird.guns().max());
      assertNotNull("WARBIRD has thors", warbird.thors());
      assertEquals("WARBIRD ThorMax = 3", 3, warbird.thors().max());
      assertNotNull("WARBIRD has decoys (B2-activated)", warbird.decoys());
      assertEquals("WARBIRD DecoyMax = 1", 1, warbird.decoys().max());
      // Disallow checks — verifies Q6 null mapping fixed the dual-pipeline
      // drift (was: silently 10 repels via DEFAULT_REPELS; now: null per
      // operator's authored RepelMax 0).
      assertEquals("WARBIRD bombs disallowed (MaxBombs 0)",
          null, warbird.bombs());
      assertEquals("WARBIRD repels disallowed (RepelMax 0)",
          null, warbird.repels());
      assertEquals("WARBIRD bursts disallowed (BurstMax 0)",
          null, warbird.bursts());
      assertEquals("WARBIRD bricks disallowed (BrickMax 0)",
          null, warbird.bricks());
      assertEquals("WARBIRD rockets disallowed (omitted block in trench/ships.groovy)",
          null, warbird.rockets());

      // trench/javelin gets rockets (start: 1, max: 3, activeTimeCs: 400) per
      // ship-javelin.groovy's `RocketTime 400`.
      final var javelin = snapshot.getShip(Ship.JAVELIN);
      assertNotNull("JAVELIN config", javelin);
      assertNotNull("JAVELIN has rockets (RocketMax > 0)", javelin.rockets());
      assertEquals("JAVELIN rocket start = 1", 1, javelin.rockets().start());
      assertEquals("JAVELIN rocket max = 3", 3, javelin.rockets().max());
      assertEquals("JAVELIN rocket activeTimeCs = 400 (per-ship RocketTime)",
          400L, javelin.rockets().activeTimeCs());

      // trench/leviathan is a heavy build with bombs + mines + portals.
      final var leviathan = snapshot.getShip(Ship.LEVIATHAN);
      assertNotNull("LEVIATHAN config", leviathan);
      assertNotNull("LEVIATHAN has bombs (MaxBombs 3)", leviathan.bombs());
      assertEquals("LEVIATHAN bomb max = BOMB_3",
          infinity.BombLevel.BOMB_3, leviathan.bombs().max());
      assertNotNull("LEVIATHAN has mines (MaxMines > 0)", leviathan.mines());
      assertEquals("LEVIATHAN mine cost (LandmineFireEnergy 800)",
          800, leviathan.mines().cost());
      assertNotNull("LEVIATHAN has portals (PortalMax 1, B2-activated)",
          leviathan.portals());
      assertEquals("LEVIATHAN PortalMax = 1", 1, leviathan.portals().max());

      // Slice 6a: trench/leviathan has stealth status 2 (start active) per
      // ship-leviathan.groovy's StealthStatus 2 / StealthEnergy 1000.
      // Cloak is forbidden (omitted block → null).
      assertNull("LEVIATHAN has no cloak block (forbidden, omitted)",
          leviathan.cloak());
      assertNotNull("LEVIATHAN has stealth (StealthStatus 2)",
          leviathan.stealth());
      assertEquals("LEVIATHAN StealthStatus = 2 (start active)",
          2, leviathan.stealth().status());
      assertEquals("LEVIATHAN StealthEnergy = 1000",
          1000, leviathan.stealth().energyDrainPer1000Cs());

      // Slice 6a: trench/weasel has both cloak + stealth at status 2 per
      // ship-weasel.groovy (CloakStatus 2 / CloakEnergy 1250 + StealthStatus
      // 2 / StealthEnergy 400).
      final var weasel = snapshot.getShip(Ship.WEASEL);
      assertNotNull("WEASEL config", weasel);
      assertNotNull("WEASEL has cloak (CloakStatus 2)", weasel.cloak());
      assertEquals("WEASEL CloakStatus = 2", 2, weasel.cloak().status());
      assertEquals("WEASEL CloakEnergy = 1250",
          1250, weasel.cloak().energyDrainPer1000Cs());
      assertNotNull("WEASEL has stealth (StealthStatus 2)", weasel.stealth());
      assertEquals("WEASEL StealthStatus = 2", 2, weasel.stealth().status());
      assertEquals("WEASEL StealthEnergy = 400",
          400, weasel.stealth().energyDrainPer1000Cs());

      // Slice 6a: trench/warbird has neither cloak nor stealth (both
      // omitted = forbidden in trench).
      assertNull("WARBIRD has no cloak block (forbidden, omitted)",
          warbird.cloak());
      assertNull("WARBIRD has no stealth block (forbidden, omitted)",
          warbird.stealth());

      // Phase 3 — weapons compat shim pulled trench's misc.groovy values
      // into ConfigRegistry's flat weapon-projectile slots (post-B1a flatten).
      assertEquals(
          "trench's [Bullet] BulletDamageLevel = 520",
          520,
          snapshot.bullet().damage());
      assertEquals(
          "trench's [Bomb] BombDamageLevel = 2650",
          2650,
          snapshot.bomb().damage());

      // [Rocket] arena-global tuning from rocket.groovy.
      assertEquals(
          "trench's [Rocket] RocketThrust = 100",
          100,
          snapshot.rocket().thrust());
      assertEquals(
          "trench's [Rocket] RocketSpeed = 3000",
          3000,
          snapshot.rocket().speed());

      // [Brick] arena-global tuning from brick.groovy. trench's
      // pre-migration misc.groovy authored only BrickTime; brick.groovy
      // fills in the canonical base BrickSpan = 7.
      assertEquals(
          "trench's [Brick] BrickSpan = 7",
          7,
          snapshot.brick().spanTiles());
      assertEquals(
          "trench's [Brick] BrickTime = 1000 cs (= 10000 ms)",
          10_000L,
          snapshot.brick().timeMs());

      // [Misc] DecoyAliveTime arena-global tuning from decoy.groovy.
      // Migrated 1:1 from trench's pre-migration misc.groovy
      // (DecoyAliveTime 10000 cs = 100000 ms).
      assertEquals(
          "trench's [Misc] DecoyAliveTime = 10000 cs (= 100000 ms)",
          100_000L,
          snapshot.decoy().aliveTimeMs());

      // [Misc] WarpPointDelay arena-global tuning from portal.groovy.
      // Migrated 1:1 from trench's pre-migration misc.groovy
      // (WarpPointDelay 24000 cs = 240000 ms).
      assertEquals(
          "trench's [Misc] WarpPointDelay = 24000 cs (= 240000 ms)",
          240_000L,
          snapshot.portal().activeTimeMs());

      // Phase 3 — prize compat shim. trench's PrizeMaxExist = 12000 (cs) → 120000 ms.
      assertEquals(
          "trench's [Prize] PrizeMaxExist = 12000 cs (= 120000 ms)",
          120_000L,
          snapshot.prize().defaultDecayMs());

      // Typed prize-weights.groovy populated the prizeWeights slot (B3).
      // trench is a no-greens preset for most prize types; sample the few
      // non-zero entries to pin the typed loader's behaviour.
      assertEquals(
          "trench Repel weight = 100",
          Integer.valueOf(100),
          snapshot.prizeWeights().weights().get("Repel"));
      assertEquals(
          "trench MultiFire weight = 255",
          Integer.valueOf(255),
          snapshot.prizeWeights().weights().get("MultiFire"));
      assertEquals(
          "trench Brick weight = 3",
          Integer.valueOf(3),
          snapshot.prizeWeights().weights().get("Brick"));
    } finally {
      systems.stop();
      systems.terminate();
    }
  }
}
