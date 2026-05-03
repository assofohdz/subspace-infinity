// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
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
