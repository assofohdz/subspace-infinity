// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.BulletLevel;
import infinity.es.ChangeTarget;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstChange;
import infinity.es.ship.actions.BurstStats;
import infinity.BombLevel;
import infinity.es.ship.weapons.BombFireDelay;
import infinity.es.ship.weapons.BombStats;
import infinity.es.ship.weapons.BulletFireDelay;
import infinity.es.ship.weapons.BulletStats;
import infinity.es.ship.weapons.GravBomb;
import infinity.es.ship.weapons.GravBombStats;
import infinity.es.ship.weapons.GravityBombFireDelay;
import infinity.es.ship.weapons.MineFireDelay;
import infinity.es.ship.weapons.MineStats;
import infinity.es.ship.weapons.WeaponType;
import org.junit.Test;

/** Direct unit tests for {@link WeaponsEligibility} static helpers — the canonical pre-fire gate path used by {@link WeaponsFireEligibilitySystem}. Covers the energy + inventory dispatch arms (excluding bomb-safety which requires PhysicsSpace). */
public class WeaponsEligibilityTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(EnergySystem.class, new EnergySystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed, systems.get(EnergySystem.class));
  }

  private static EntityId energyShip(final EntityData ed, final int pool) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new Energy(pool));
    ed.setComponent(ship, new EnergyStats(pool, pool, 0, 0.0, 0.0, 0.0));
    return ship;
  }

  // ===== canAttackEnergyWeapon =====

  @Test
  public void canAttackEnergyWeapon_notInSet_false() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = energyShip(f.ed, 1000);
      // ship has no BulletFireDelay → not in set
      final EntitySet bullets = f.ed.getEntities(BulletFireDelay.class);
      bullets.applyChanges();
      assertFalse(
          WeaponsEligibility.canAttackEnergyWeapon(
              f.ed, f.energy, bullets, f.ed.getEntity(ship),
              BulletFireDelay.class, BulletStats.class));
      bullets.release();
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void canAttackEnergyWeapon_delayNotReady_false() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = energyShip(f.ed, 1000);
      // Long cooldown → delay.getPercent() < 1 for the duration of this test.
      f.ed.setComponent(ship, new BulletFireDelay(60_000L));
      f.ed.setComponent(ship, new BulletStats(BulletLevel.LEVEL_1, 10, 100L, 50));
      final EntitySet bullets = f.ed.getEntities(BulletFireDelay.class);
      bullets.applyChanges();
      assertFalse(
          WeaponsEligibility.canAttackEnergyWeapon(
              f.ed, f.energy, bullets, f.ed.getEntity(ship),
              BulletFireDelay.class, BulletStats.class));
      bullets.release();
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void canAttackEnergyWeapon_costExceedsHealth_false() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = energyShip(f.ed, 5);
      f.ed.setComponent(ship, new BulletFireDelay(0L)); // ready immediately
      f.ed.setComponent(ship, new BulletStats(BulletLevel.LEVEL_1, 100, 100L, 50));
      final EntitySet bullets = f.ed.getEntities(BulletFireDelay.class);
      bullets.applyChanges();
      f.systems.update(); // refresh EnergySystem.living so getHealth(ship) works
      assertFalse(
          "cost 100 > health 5",
          WeaponsEligibility.canAttackEnergyWeapon(
              f.ed, f.energy, bullets, f.ed.getEntity(ship),
              BulletFireDelay.class, BulletStats.class));
      bullets.release();
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void canAttackEnergyWeapon_eligible_true() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = energyShip(f.ed, 1000);
      f.ed.setComponent(ship, new BulletFireDelay(0L));
      f.ed.setComponent(ship, new BulletStats(BulletLevel.LEVEL_1, 10, 100L, 50));
      final EntitySet bullets = f.ed.getEntities(BulletFireDelay.class);
      bullets.applyChanges();
      f.systems.update(); // refresh EnergySystem.living so getHealth(ship) works
      assertTrue(
          WeaponsEligibility.canAttackEnergyWeapon(
              f.ed, f.energy, bullets, f.ed.getEntity(ship),
              BulletFireDelay.class, BulletStats.class));
      bullets.release();
    } finally {
      f.shutdown();
    }
  }

  // ===== canAttackInventoryWeapon =====

  @Test
  public void canAttackInventoryWeapon_notInSet_false() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      // no Burst component → not in set
      final EntitySet bursts = f.ed.getEntities(Burst.class);
      bursts.applyChanges();
      assertFalse(
          WeaponsEligibility.canAttackInventoryWeapon(
              f.ed, bursts, f.ed.getEntity(ship), Burst.class));
      bursts.release();
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void canAttackInventoryWeapon_zeroCount_false() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new Burst(0));
      final EntitySet bursts = f.ed.getEntities(Burst.class);
      bursts.applyChanges();
      assertFalse(
          "count == 0 must reject",
          WeaponsEligibility.canAttackInventoryWeapon(
              f.ed, bursts, f.ed.getEntity(ship), Burst.class));
      bursts.release();
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void canAttackInventoryWeapon_positiveCount_true() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new Burst(3));
      final EntitySet bursts = f.ed.getEntities(Burst.class);
      bursts.applyChanges();
      assertTrue(
          WeaponsEligibility.canAttackInventoryWeapon(
              f.ed, bursts, f.ed.getEntity(ship), Burst.class));
      bursts.release();
    } finally {
      f.shutdown();
    }
  }

  // ===== setCoolDownEnergyWeapon =====

  @Test
  public void setCoolDownEnergyWeapon_stampsFreshDelay() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new BulletStats(BulletLevel.LEVEL_1, 10, 250L, 50));
      f.ed.setComponent(ship, new BulletFireDelay(0L));
      final EntitySet bullets = f.ed.getEntities(BulletFireDelay.class);
      bullets.applyChanges();
      assertTrue(
          WeaponsEligibility.setCoolDownEnergyWeapon(
              f.ed, bullets, f.ed.getEntity(ship),
              BulletStats.class, s -> new BulletFireDelay(s.fireDelayMillis())));
      // After stamping with delayMillis=250, the fresh delay should NOT be ready immediately.
      final BulletFireDelay fresh = f.ed.getComponent(ship, BulletFireDelay.class);
      assertTrue("fresh 250ms cooldown reads as not-ready", fresh.getPercent() < 1.0);
      bullets.release();
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void setCoolDownEnergyWeapon_notInSet_false() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      final EntitySet bullets = f.ed.getEntities(BulletFireDelay.class);
      bullets.applyChanges();
      assertFalse(
          WeaponsEligibility.setCoolDownEnergyWeapon(
              f.ed, bullets, f.ed.getEntity(ship),
              BulletStats.class, s -> new BulletFireDelay(s.fireDelayMillis())));
      bullets.release();
    } finally {
      f.shutdown();
    }
  }

  // ===== deductEnergyCost =====

  @Test
  public void deductEnergyCost_eligible_emitsAttributedDamageIntent() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = energyShip(f.ed, 1000);
      f.ed.setComponent(ship, new BulletStats(BulletLevel.LEVEL_1, 25, 100L, 50));
      f.ed.setComponent(ship, new BulletFireDelay(0L));
      final EntitySet bullets = f.ed.getEntities(BulletFireDelay.class);
      bullets.applyChanges();
      f.systems.update(); // refresh EnergySystem.living so getHealth(ship) works
      assertTrue(
          WeaponsEligibility.deductEnergyCost(
              f.ed, f.energy, bullets, f.ed.getEntity(ship),
              BulletStats.class, WeaponType.BULLET));
      // Verify a damage intent was emitted (EnergySystem.damage creates a holder).
      final EntitySet damages = f.ed.getEntities(infinity.es.ship.EnergyChange.class, ChangeTarget.class);
      damages.applyChanges();
      assertEquals("one damage holder emitted", 1, damages.size());
      damages.release();
      bullets.release();
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void deductEnergyCost_costExceedsHealth_false() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = energyShip(f.ed, 10);
      f.ed.setComponent(ship, new BulletStats(BulletLevel.LEVEL_1, 100, 100L, 50));
      f.ed.setComponent(ship, new BulletFireDelay(0L));
      final EntitySet bullets = f.ed.getEntities(BulletFireDelay.class);
      bullets.applyChanges();
      f.systems.update(); // refresh EnergySystem.living so getHealth(ship) works
      assertFalse(
          WeaponsEligibility.deductEnergyCost(
              f.ed, f.energy, bullets, f.ed.getEntity(ship),
              BulletStats.class, WeaponType.BULLET));
      bullets.release();
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void deductEnergyCost_notInSet_false() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = energyShip(f.ed, 1000);
      final EntitySet bullets = f.ed.getEntities(BulletFireDelay.class);
      bullets.applyChanges();
      assertFalse(
          WeaponsEligibility.deductEnergyCost(
              f.ed, f.energy, bullets, f.ed.getEntity(ship),
              BulletStats.class, WeaponType.BULLET));
      bullets.release();
    } finally {
      f.shutdown();
    }
  }

  // ===== deductInventoryWeapon =====

  @Test
  public void deductInventoryWeapon_eligible_emitsChange() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new Burst(3));
      f.ed.setComponent(ship, new BurstStats(5, 3000));
      final EntitySet bursts = f.ed.getEntities(Burst.class);
      bursts.applyChanges();
      assertTrue(
          WeaponsEligibility.deductInventoryWeapon(
              f.ed, bursts, f.ed.getEntity(ship), new BurstChange(-1)));
      // Verify a BurstChange holder was emitted.
      final EntitySet changes = f.ed.getEntities(BurstChange.class, ChangeTarget.class);
      changes.applyChanges();
      assertEquals("one BurstChange(-1) holder emitted", 1, changes.size());
      changes.release();
      bursts.release();
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void deductInventoryWeapon_notInSet_false() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      final EntitySet bursts = f.ed.getEntities(Burst.class);
      bursts.applyChanges();
      assertFalse(
          WeaponsEligibility.deductInventoryWeapon(
              f.ed, bursts, f.ed.getEntity(ship), new BurstChange(-1)));
      bursts.release();
    } finally {
      f.shutdown();
    }
  }

  // ===== Dispatch arms (smoke coverage) =====

  @Test
  public void canAttack_dispatchBurstArm() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = energyShip(f.ed, 1000);
      f.ed.setComponent(ship, new Burst(2));
      // Build the 5 EntitySets canAttack() expects.
      final EntitySet bullets = f.ed.getEntities(BulletFireDelay.class);
      final EntitySet bombs = f.ed.getEntities(infinity.es.ship.weapons.BombFireDelay.class);
      final EntitySet gravs = f.ed.getEntities(GravBomb.class);
      final EntitySet mines = f.ed.getEntities(infinity.es.ship.weapons.MineFireDelay.class);
      final EntitySet bursts = f.ed.getEntities(Burst.class);
      final EntitySet energyEntities = f.ed.getEntities(Energy.class);
      bullets.applyChanges();
      bombs.applyChanges();
      gravs.applyChanges();
      mines.applyChanges();
      bursts.applyChanges();
      energyEntities.applyChanges();
      assertTrue(
          WeaponsEligibility.canAttack(
              f.ed, null, f.energy, bullets, bombs, gravs, mines, bursts, energyEntities,
              f.ed.getEntity(ship), WeaponType.BURST));
      assertFalse(
          "unknown weapon type falls through to default → false",
          WeaponsEligibility.canAttack(
              f.ed, null, f.energy, bullets, bombs, gravs, mines, bursts, energyEntities,
              f.ed.getEntity(ship), (byte) 99));
      assertFalse(
          "null requester rejected",
          WeaponsEligibility.canAttack(
              f.ed, null, f.energy, bullets, bombs, gravs, mines, bursts, energyEntities,
              null, WeaponType.BURST));
      bullets.release();
      bombs.release();
      gravs.release();
      mines.release();
      bursts.release();
      energyEntities.release();
    } finally {
      f.shutdown();
    }
  }

  // ===== canAttackInventoryWeaponWithDelay (gravbomb shape) =====

  @Test
  public void canAttackInventoryWeaponWithDelay_zeroCount_false() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new GravBomb(0));
      f.ed.setComponent(ship, new GravityBombFireDelay(0L));
      final EntitySet gravs = f.ed.getEntities(GravBomb.class);
      gravs.applyChanges();
      assertFalse(
          WeaponsEligibility.canAttackInventoryWeaponWithDelay(
              f.ed, gravs, f.ed.getEntity(ship), GravBomb.class, GravityBombFireDelay.class));
      gravs.release();
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void canAttackInventoryWeaponWithDelay_delayNotReady_false() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new GravBomb(3));
      f.ed.setComponent(ship, new GravityBombFireDelay(60_000L));
      final EntitySet gravs = f.ed.getEntities(GravBomb.class);
      gravs.applyChanges();
      assertFalse(
          WeaponsEligibility.canAttackInventoryWeaponWithDelay(
              f.ed, gravs, f.ed.getEntity(ship), GravBomb.class, GravityBombFireDelay.class));
      gravs.release();
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void canAttackInventoryWeaponWithDelay_eligible_true() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new GravBomb(3));
      f.ed.setComponent(ship, new GravityBombFireDelay(0L));
      final EntitySet gravs = f.ed.getEntities(GravBomb.class);
      gravs.applyChanges();
      assertTrue(
          WeaponsEligibility.canAttackInventoryWeaponWithDelay(
              f.ed, gravs, f.ed.getEntity(ship), GravBomb.class, GravityBombFireDelay.class));
      gravs.release();
    } finally {
      f.shutdown();
    }
  }

  // ===== Dispatcher arms — exercise canAttack/setCoolDown/deductCostOfAttack for each weapon type =====

  /** Build the 5 EntitySets the dispatcher needs + the Energy filter set. Caller releases. */
  private static EntitySet[] allWeaponSets(final EntityData ed) {
    final EntitySet[] sets = {
        ed.getEntities(BulletFireDelay.class),
        ed.getEntities(BombFireDelay.class),
        ed.getEntities(GravBomb.class),
        ed.getEntities(MineFireDelay.class),
        ed.getEntities(Burst.class),
        ed.getEntities(Energy.class),
    };
    for (final EntitySet s : sets) {
      s.applyChanges();
    }
    return sets;
  }

  private static void release(final EntitySet[] sets) {
    for (final EntitySet s : sets) {
      s.release();
    }
  }

  @Test
  public void canAttack_dispatchBulletArm_true() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = energyShip(f.ed, 1000);
      f.ed.setComponent(ship, new BulletFireDelay(0L));
      f.ed.setComponent(ship, new BulletStats(BulletLevel.LEVEL_1, 10, 100L, 50));
      final EntitySet[] sets = allWeaponSets(f.ed);
      f.systems.update();
      assertTrue(
          WeaponsEligibility.canAttack(
              f.ed, null, f.energy, sets[0], sets[1], sets[2], sets[3], sets[4], sets[5],
              f.ed.getEntity(ship), WeaponType.BULLET));
      release(sets);
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void canAttack_dispatchMineArm_true() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = energyShip(f.ed, 1000);
      f.ed.setComponent(ship, new MineFireDelay(0L));
      f.ed.setComponent(ship, new MineStats(BombLevel.BOMB_1, 10, 100L, 50));
      final EntitySet[] sets = allWeaponSets(f.ed);
      f.systems.update();
      assertTrue(
          WeaponsEligibility.canAttack(
              f.ed, null, f.energy, sets[0], sets[1], sets[2], sets[3], sets[4], sets[5],
              f.ed.getEntity(ship), WeaponType.MINE));
      release(sets);
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void canAttack_dispatchGravBombArm_true() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = energyShip(f.ed, 1000);
      f.ed.setComponent(ship, new GravBomb(3));
      f.ed.setComponent(ship, new GravBombStats(5, 100L));
      f.ed.setComponent(ship, new GravityBombFireDelay(0L));
      final EntitySet[] sets = allWeaponSets(f.ed);
      f.systems.update();
      assertTrue(
          WeaponsEligibility.canAttack(
              f.ed, null, f.energy, sets[0], sets[1], sets[2], sets[3], sets[4], sets[5],
              f.ed.getEntity(ship), WeaponType.GRAVBOMB));
      release(sets);
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void setCoolDown_dispatchAllArms() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = f.ed.createEntity();
      f.ed.setComponent(ship, new BulletFireDelay(0L));
      f.ed.setComponent(ship, new BulletStats(BulletLevel.LEVEL_1, 10, 250L, 50));
      f.ed.setComponent(ship, new BombFireDelay(0L));
      f.ed.setComponent(ship, new BombStats(BombLevel.BOMB_1, 10, 250L, 50, 0));
      f.ed.setComponent(ship, new GravBomb(3));
      f.ed.setComponent(ship, new GravBombStats(5, 250L));
      f.ed.setComponent(ship, new GravityBombFireDelay(0L));
      f.ed.setComponent(ship, new MineFireDelay(0L));
      f.ed.setComponent(ship, new MineStats(BombLevel.BOMB_1, 10, 250L, 50));
      f.ed.setComponent(ship, new Burst(2));
      final EntitySet[] sets = allWeaponSets(f.ed);
      final Entity req = f.ed.getEntity(ship);
      assertTrue(
          WeaponsEligibility.setCoolDown(
              f.ed, sets[0], sets[1], sets[2], sets[3], sets[4], req, WeaponType.BULLET));
      assertTrue(
          WeaponsEligibility.setCoolDown(
              f.ed, sets[0], sets[1], sets[2], sets[3], sets[4], req, WeaponType.BOMB));
      assertTrue(
          WeaponsEligibility.setCoolDown(
              f.ed, sets[0], sets[1], sets[2], sets[3], sets[4], req, WeaponType.GRAVBOMB));
      assertTrue(
          WeaponsEligibility.setCoolDown(
              f.ed, sets[0], sets[1], sets[2], sets[3], sets[4], req, WeaponType.MINE));
      assertTrue(
          WeaponsEligibility.setCoolDown(
              f.ed, sets[0], sets[1], sets[2], sets[3], sets[4], req, WeaponType.BURST));
      assertFalse(
          "unknown weapon type → default arm false",
          WeaponsEligibility.setCoolDown(
              f.ed, sets[0], sets[1], sets[2], sets[3], sets[4], req, (byte) 99));
      assertFalse(
          "null requester rejected",
          WeaponsEligibility.setCoolDown(
              f.ed, sets[0], sets[1], sets[2], sets[3], sets[4], null, WeaponType.BULLET));
      release(sets);
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void deductCostOfAttack_dispatchAllArms() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = energyShip(f.ed, 100_000);
      f.ed.setComponent(ship, new BulletFireDelay(0L));
      f.ed.setComponent(ship, new BulletStats(BulletLevel.LEVEL_1, 10, 100L, 50));
      f.ed.setComponent(ship, new BombFireDelay(0L));
      f.ed.setComponent(ship, new BombStats(BombLevel.BOMB_1, 15, 100L, 50, 0));
      f.ed.setComponent(ship, new GravBomb(3));
      f.ed.setComponent(ship, new GravBombStats(5, 100L));
      f.ed.setComponent(ship, new GravityBombFireDelay(0L));
      f.ed.setComponent(ship, new MineFireDelay(0L));
      f.ed.setComponent(ship, new MineStats(BombLevel.BOMB_1, 25, 100L, 50));
      f.ed.setComponent(ship, new Burst(3));
      final EntitySet[] sets = allWeaponSets(f.ed);
      f.systems.update();
      final Entity req = f.ed.getEntity(ship);
      assertTrue(
          WeaponsEligibility.deductCostOfAttack(
              f.ed, f.energy, sets[0], sets[1], sets[2], sets[3], sets[4], req, WeaponType.BULLET));
      assertTrue(
          WeaponsEligibility.deductCostOfAttack(
              f.ed, f.energy, sets[0], sets[1], sets[2], sets[3], sets[4], req, WeaponType.BOMB));
      assertTrue(
          WeaponsEligibility.deductCostOfAttack(
              f.ed, f.energy, sets[0], sets[1], sets[2], sets[3], sets[4], req, WeaponType.GRAVBOMB));
      assertTrue(
          WeaponsEligibility.deductCostOfAttack(
              f.ed, f.energy, sets[0], sets[1], sets[2], sets[3], sets[4], req, WeaponType.MINE));
      assertTrue(
          WeaponsEligibility.deductCostOfAttack(
              f.ed, f.energy, sets[0], sets[1], sets[2], sets[3], sets[4], req, WeaponType.BURST));
      assertFalse(
          "unknown weapon type → false",
          WeaponsEligibility.deductCostOfAttack(
              f.ed, f.energy, sets[0], sets[1], sets[2], sets[3], sets[4], req, (byte) 99));
      assertFalse(
          "null requester rejected",
          WeaponsEligibility.deductCostOfAttack(
              f.ed, f.energy, sets[0], sets[1], sets[2], sets[3], sets[4], null, WeaponType.BULLET));
      release(sets);
    } finally {
      f.shutdown();
    }
  }

  private record Fixture(GameSystemManager systems, DefaultEntityData ed, EnergySystem energy) {
    void shutdown() {
      systems.stop();
      systems.terminate();
    }
  }
}
