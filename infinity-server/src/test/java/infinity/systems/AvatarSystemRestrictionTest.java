// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.sim.GameSystemManager;
import infinity.Ship;
import infinity.config.EngineConfig;
import infinity.config.ShipRestrictionsConfig;
import infinity.es.ChangeTarget;
import infinity.es.Frequency;
import infinity.es.FrequencyChange;
import infinity.es.ShapeNames;
import infinity.es.arena.ArenaId;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.ShipType;
import infinity.es.ship.ShipTypeChange;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.EngineConfigSystem;
import com.simsilica.mathd.Vec3d;
import java.util.EnumSet;
import java.util.Map;
import org.junit.Test;

/** Energy-gate + arena-restrictor coverage for {@link AvatarSystem#requestShipChange} and {@link AvatarSystem#requestFreqChange}. */
public class AvatarSystemRestrictionTest {

  private static final String ARENA = "trench";

  // ──────────────────────────────────────────────────────────────────────
  // Fixture wiring
  // ──────────────────────────────────────────────────────────────────────

  private static Fixture newFixture(final ShipRestrictionsConfig restrictions) {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(ConfigRegistrySystem.class, new StubConfigRegistrySystem(restrictions));
    systems.register(EngineConfigSystem.class, new StubEngineConfigSystem());
    systems.register(ArenaSystem.class, new StubArenaSystem());
    final AvatarSystem avatars = new AvatarSystem();
    systems.register(AvatarSystem.class, avatars);
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed, avatars);
  }

  // ──────────────────────────────────────────────────────────────────────
  // Energy gate
  // ──────────────────────────────────────────────────────────────────────

  @Test
  public void requestShipChange_partialEnergy_denied() {
    final Fixture f = newFixture(ShipRestrictionsConfig.DEFAULTS);
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(ship, new EnergyStats(1000, 1500, 100, 50.0, 75.0, 5.0));
      f.ed.setComponent(ship, new Energy(500)); // < max(1000)

      f.avatars.requestShipChange(ship, Ship.JAVELIN.getId());

      assertNoShipTypeChange(f.ed);
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void requestShipChange_fullEnergy_allowed() {
    final Fixture f = newFixture(ShipRestrictionsConfig.DEFAULTS);
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(ship, new EnergyStats(1000, 1500, 100, 50.0, 75.0, 5.0));
      f.ed.setComponent(ship, new Energy(1000));

      f.avatars.requestShipChange(ship, Ship.JAVELIN.getId());

      assertOneShipTypeChange(f.ed, Ship.JAVELIN);
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void requestShipChange_noEnergyStats_passesGate() {
    // SPEC-style ships with no EnergyStats are eligible — the gate is "have enough"
    // not "must have a pool". This keeps the test fixture simple too.
    final Fixture f = newFixture(ShipRestrictionsConfig.DEFAULTS);
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new ShipType(Ship.WARBIRD));

      f.avatars.requestShipChange(ship, Ship.JAVELIN.getId());

      assertOneShipTypeChange(f.ed, Ship.JAVELIN);
    } finally {
      f.shutdown();
    }
  }

  // ──────────────────────────────────────────────────────────────────────
  // Arena restrictor — allow / deny lists
  // ──────────────────────────────────────────────────────────────────────

  @Test
  public void requestShipChange_deniedByAllowList_blocked() {
    final ShipRestrictionsConfig restrictions =
        new ShipRestrictionsConfig(EnumSet.of(Ship.WARBIRD), EnumSet.noneOf(Ship.class), Map.of());
    final Fixture f = newFixture(restrictions);
    try {
      final EntityId ship = createFullEnergyShip(f.ed);

      f.avatars.requestShipChange(ship, Ship.JAVELIN.getId());

      assertNoShipTypeChange(f.ed);
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void requestShipChange_allowedByAllowList_passes() {
    final ShipRestrictionsConfig restrictions =
        new ShipRestrictionsConfig(
            EnumSet.of(Ship.WARBIRD, Ship.JAVELIN), EnumSet.noneOf(Ship.class), Map.of());
    final Fixture f = newFixture(restrictions);
    try {
      final EntityId ship = createFullEnergyShip(f.ed);

      f.avatars.requestShipChange(ship, Ship.JAVELIN.getId());

      assertOneShipTypeChange(f.ed, Ship.JAVELIN);
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void requestShipChange_denyList_blocks() {
    final ShipRestrictionsConfig restrictions =
        new ShipRestrictionsConfig(EnumSet.noneOf(Ship.class), EnumSet.of(Ship.SHARK), Map.of());
    final Fixture f = newFixture(restrictions);
    try {
      final EntityId ship = createFullEnergyShip(f.ed);

      f.avatars.requestShipChange(ship, Ship.SHARK.getId());

      assertNoShipTypeChange(f.ed);
    } finally {
      f.shutdown();
    }
  }

  // ──────────────────────────────────────────────────────────────────────
  // Frequency-change gate
  // ──────────────────────────────────────────────────────────────────────

  @Test
  public void requestFreqChange_allowedShip_emitsFrequencyChange() {
    final Fixture f = newFixture(ShipRestrictionsConfig.DEFAULTS);
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(ship, new Frequency(0));

      f.avatars.requestFreqChange(ship, 3);

      assertEquals(1, countFrequencyChanges(f.ed));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void requestFreqChange_deniedByAllowList_blocked() {
    final ShipRestrictionsConfig restrictions =
        new ShipRestrictionsConfig(EnumSet.of(Ship.JAVELIN), EnumSet.noneOf(Ship.class), Map.of());
    final Fixture f = newFixture(restrictions);
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new ShipType(Ship.WARBIRD));
      f.ed.setComponent(ship, new Frequency(0));
      f.ed.setComponent(ship, new ArenaId(ARENA, EntityId.NULL_ID));

      f.avatars.requestFreqChange(ship, 3);

      assertEquals(0, countFrequencyChanges(f.ed));
    } finally {
      f.shutdown();
    }
  }

  // ──────────────────────────────────────────────────────────────────────
  // Helpers
  // ──────────────────────────────────────────────────────────────────────

  private static EntityId createFullEnergyShip(final EntityData ed) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new ShipType(Ship.WARBIRD));
    ed.setComponent(ship, new EnergyStats(1000, 1500, 100, 50.0, 75.0, 5.0));
    ed.setComponent(ship, new Energy(1000));
    // ArenaId lets ConfigShipRestrictor.resolveConfig look up the per-arena policy
    // (StubConfigRegistrySystem returns the test-fixture snapshot for any ArenaId).
    ed.setComponent(ship, new ArenaId(ARENA, EntityId.NULL_ID));
    // Provide a ShapeInfo so the count probe in ConfigShipRestrictor has a fixture.
    ed.setComponent(ship, ShapeInfo.create(ShapeNames.SHIP_WARBIRD, 1.0, ed));
    return ship;
  }

  private static void assertNoShipTypeChange(final EntityData ed) {
    final EntitySet changes = ed.getEntities(ShipTypeChange.class, ChangeTarget.class);
    try {
      changes.applyChanges();
      assertEquals("no ShipTypeChange emitted (gated)", 0, changes.size());
    } finally {
      changes.release();
    }
  }

  private static void assertOneShipTypeChange(final EntityData ed, final Ship expected) {
    final EntitySet changes = ed.getEntities(ShipTypeChange.class, ChangeTarget.class);
    try {
      changes.applyChanges();
      assertEquals("one ShipTypeChange emitted", 1, changes.size());
      final Entity holder = changes.iterator().next();
      final ShipTypeChange c = holder.get(ShipTypeChange.class);
      assertNotNull(c);
      assertEquals(expected, c.newShipType());
    } finally {
      changes.release();
    }
  }

  private static int countFrequencyChanges(final EntityData ed) {
    final EntitySet changes = ed.getEntities(FrequencyChange.class, ChangeTarget.class);
    try {
      changes.applyChanges();
      return changes.size();
    } finally {
      changes.release();
    }
  }

  // ──────────────────────────────────────────────────────────────────────
  // Stubs
  // ──────────────────────────────────────────────────────────────────────

  /** Returns the test-fixture's {@link ShipRestrictionsConfig} for every arena. */
  private static final class StubConfigRegistrySystem extends ConfigRegistrySystem {
    private final ConfigRegistry snapshot;

    private StubConfigRegistrySystem(final ShipRestrictionsConfig restrictions) {
      this.snapshot =
          ConfigRegistry.builder().with(ShipRestrictionsConfig.class, restrictions).build();
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

  /** Stub {@link ArenaSystem} — {@link #getArenaSpawn(String, int)} returns {@code null} so the warp-teleport branch is a no-op. */
  private static final class StubArenaSystem extends ArenaSystem {
    @Override
    protected void initialize() {
      // bypass — no zone.groovy load, no asset wiring
    }

    @Override
    protected void terminate() {
      // bypass
    }

    @Override
    @javax.annotation.Nullable
    public Vec3d getArenaSpawn(final String arenaName, final int freq) {
      return null;
    }
  }

  /** Stub {@link EngineConfigSystem} — only {@link #get()} is exercised. */
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
    private final AvatarSystem avatars;

    private Fixture(
        final GameSystemManager systems,
        final DefaultEntityData ed,
        final AvatarSystem avatars) {
      this.systems = systems;
      this.ed = ed;
      this.avatars = avatars;
    }

    private void shutdown() {
      systems.stop();
      systems.terminate();
    }
  }
}
