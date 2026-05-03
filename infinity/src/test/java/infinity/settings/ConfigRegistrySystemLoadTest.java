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
    systems.register(GroovyWeaponsLoader.class, new GroovyWeaponsLoader());
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
                  "/conf/trench-04-2026/mine.groovy"),
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
    } finally {
      systems.stop();
      systems.terminate();
    }
  }
}
