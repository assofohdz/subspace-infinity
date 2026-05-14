// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.mathd.Grid;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.GameSystemManager;
import infinity.config.EngineConfig;
import infinity.config.PrizeConfig;
import infinity.es.ChangeTarget;
import infinity.es.PrizeSpawnIntent;
import infinity.es.PrizeType;
import infinity.es.arena.ArenaId;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.EngineConfigSystem;
import org.junit.Test;

/**
 * Channel A intent-drain round-trip for {@link DeathPrizeSystem}. Emits a
 * {@code (ChangeTarget, PrizeSpawnIntent)} holder, ticks the system, and
 * pins (a) one death-prize entity spawned, (b) the intent holder destroyed,
 * (c) the no-op path when the arena's {@code deathPrizeTimeMs == 0}.
 */
public class DeathPrizeSystemTest {

  private static final String ARENA = "trench";

  private static final int TEST_GRID_SPACING = 1024;

  /** Minimal test fixture wiring just the systems needed for the death-prize round-trip. */
  private static Fixture newFixture(final long deathPrizeTimeMs) {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(
        ConfigRegistrySystem.class, new StubConfigRegistrySystem(deathPrizeTimeMs));
    systems.register(EngineConfigSystem.class, new StubEngineConfigSystem());
    final PhysicsSpace<EntityId, MBlockShape> phys =
        new PhysicsSpace<>(new Grid(TEST_GRID_SPACING));
    systems.register(PrizeSpawnerSystem.class, new PrizeSpawnerSystem(phys));
    systems.register(DeathPrizeSystem.class, new DeathPrizeSystem(phys));
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  /** Emit a (ChangeTarget, PrizeSpawnIntent) one-shot holder; return its id. */
  private static EntityId emitIntent(
      final EntityData ed, final EntityId dyingShip, final Vec3d position) {
    final EntityId holder = ed.createEntity();
    ed.setComponents(
        holder, ChangeTarget.self(dyingShip), new PrizeSpawnIntent(position, 0L));
    return holder;
  }

  @Test
  public void intentDrains_oneShotHolderRemoved_andPrizeSpawned() {
    final Fixture f = newFixture(5_000L);
    try {
      final EntityId dyingShip = f.ed.createEntity();
      f.ed.setComponent(dyingShip, new ArenaId(ARENA, EntityId.NULL_ID));
      final EntityId intent = emitIntent(f.ed, dyingShip, new Vec3d(10, 0, 20));

      f.systems.update();

      assertNull(
          "Channel A intent holder destroyed by DeathPrizeSystem",
          f.ed.getComponent(intent, PrizeSpawnIntent.class));
      assertNull(
          "ChangeTarget removed along with the holder",
          f.ed.getComponent(intent, ChangeTarget.class));

      final EntitySet prizes = f.ed.getEntities(PrizeType.class);
      try {
        prizes.applyChanges();
        assertEquals("One death-drop prize spawned", 1, prizes.size());
        final Entity spawned = prizes.iterator().next();
        assertNotNull("Spawned prize has a PrizeType", spawned.get(PrizeType.class));
      } finally {
        prizes.release();
      }
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void deathPrizeTimeMsZero_noPrizeSpawned_holderStillReaped() {
    final Fixture f = newFixture(0L);
    try {
      final EntityId dyingShip = f.ed.createEntity();
      f.ed.setComponent(dyingShip, new ArenaId(ARENA, EntityId.NULL_ID));
      final EntityId intent = emitIntent(f.ed, dyingShip, new Vec3d(10, 0, 20));

      f.systems.update();

      assertNull(
          "Holder destroyed even when death-drops are disabled (Subspace canon)",
          f.ed.getComponent(intent, PrizeSpawnIntent.class));
      final EntitySet prizes = f.ed.getEntities(PrizeType.class);
      try {
        prizes.applyChanges();
        assertEquals(
            "deathPrizeTimeMs == 0 disables death-drops; no prize spawned",
            0,
            prizes.size());
      } finally {
        prizes.release();
      }
    } finally {
      f.shutdown();
    }
  }

  // ──────────────────────────────────────────────────────────────────────
  // Stubs — minimal ConfigRegistrySystem + EngineConfigSystem
  // ──────────────────────────────────────────────────────────────────────

  /**
   * Returns a {@link ConfigRegistry} whose {@link PrizeConfig#deathPrizeTimeMs()} is the
   * fixture-configured value; all other slots use their DEFAULTS sentinels. Avoids
   * loading any Groovy fragments.
   */
  private static final class StubConfigRegistrySystem extends ConfigRegistrySystem {
    private final ConfigRegistry snapshot;

    private StubConfigRegistrySystem(final long deathPrizeTimeMs) {
      // PrizeConfig fields: (defaultDecayMs, defaultMinDecayMs, deathPrizeTimeMs, prizeNegativeFactor, defaultMaxCount, bountyValue).
      // 0 prizeNegativeFactor → no DUD substitution; deterministic prize-type selection.
      final PrizeConfig prize = new PrizeConfig(20_000L, 20_000L, deathPrizeTimeMs, 0, 10, 10);
      this.snapshot = ConfigRegistry.builder().with(PrizeConfig.class, prize).build();
    }

    @Override
    protected void initialize() {
      // bypass — no SettingsSystem / GroovyShipLoader needed
    }

    @Override
    protected void terminate() {
      // bypass
    }

    @Override
    public ConfigRegistry forArena(final ArenaId arenaId) {
      return snapshot;
    }
  }

  /** Stub {@link EngineConfigSystem} — only {@link #get()} is exercised; returns canonical defaults. */
  private static final class StubEngineConfigSystem extends EngineConfigSystem {
    @Override
    protected void initialize() {
      // bypass — no Groovy load
    }

    @Override
    protected void terminate() {
      // bypass
    }

    @Override
    public EngineConfig get() {
      return EngineConfig.DEFAULTS;
    }
  }

  private static final class Fixture {
    private final GameSystemManager systems;
    private final DefaultEntityData ed;

    private Fixture(final GameSystemManager systems, final DefaultEntityData ed) {
      this.systems = systems;
      this.ed = ed;
    }

    private void shutdown() {
      systems.stop();
      systems.terminate();
    }
  }
}
