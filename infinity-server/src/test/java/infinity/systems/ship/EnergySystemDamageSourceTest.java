// SPDX-License-Identifier: BSD-3-Clause
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.es.ChangeTarget;
import infinity.es.DamageSource;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyChange;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.weapons.WeaponType;
import org.junit.Test;

/**
 * ADR 0001 — pins the attributed
 * {@link EnergySystem#damage(EntityId, int, EntityId, byte)} overload
 * that lets reactors fork on Change type (damage vs regen vs cost-
 * deduction). The companion {@link EnergySystemChangeDrainTest} covers
 * the unattributed canonical drain; this file adds the sibling tests
 * for the optional {@link DamageSource} sibling on the Change holder.
 *
 * <p>Mirrors {@link EnergySystemChangeDrainTest}'s fixture shape
 * (minimal {@link GameSystemManager} + {@link DefaultEntityData} +
 * {@link EnergySystem}) so the two test files stay parallel: same
 * setup, asserts on adjacent features.
 */
public class EnergySystemDamageSourceTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(EnergySystem.class, new EnergySystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  /**
   * Stub fixture ship — high cap, zero recharge so test math stays
   * deterministic without needing to subtract a per-tick recharge
   * delta. The EnergyStats record carries hardMax==max so cap clamps
   * never trigger on the negative deltas under test.
   */
  private static EntityId newShip(final EntityData ed, final int pool, final int cap) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new Energy(pool));
    ed.setComponent(ship, new EnergyStats(cap, cap, 0, 0.0, 0.0, 0.0));
    return ship;
  }

  /**
   * The attributed overload writes the same Energy-folded result as
   * the unattributed call. {@link DamageSource} presence is purely
   * metadata for downstream reactors — does NOT change canonical-
   * writer arithmetic.
   */
  @Test
  public void attributedDamage_appliesSameEnergyFold_asUnattributed() {
    final Fixture f = newFixture();
    try {
      final EntityId attacker = f.ed.createEntity();
      final EntityId victim = newShip(f.ed, /* pool */ 1000, /* cap */ 1000);

      final EnergySystem energy = f.systems.get(EnergySystem.class, true);
      energy.damage(victim, -40, attacker, WeaponType.BOMB);

      f.systems.update();

      assertEquals(
          "Attributed damage folds delta identically to the unattributed call",
          960,
          f.ed.getComponent(victim, Energy.class).getEnergy());
    } finally {
      f.shutdown();
    }
  }

  /**
   * The {@link DamageSource} sibling is reaped alongside the canonical
   * {@link EnergyChange} + {@link ChangeTarget} pair when
   * {@code EnergySystem.update} destroys the one-shot holder. No
   * orphan components survive the drain.
   */
  @Test
  public void attributedChangeHolder_reapsDamageSourceSibling() {
    final Fixture f = newFixture();
    try {
      final EntityId attacker = f.ed.createEntity();
      final EntityId victim = newShip(f.ed, /* pool */ 1000, /* cap */ 1000);

      final EnergySystem energy = f.systems.get(EnergySystem.class, true);
      energy.damage(victim, -10, attacker, WeaponType.BULLET);

      f.systems.update();
      assertNoOrphanChange(f.ed);
    } finally {
      f.shutdown();
    }
  }

  /**
   * RaM rule 3 — two attributed Change holders on the same target in
   * the same tick collapse into one Energy write (folded sum of
   * deltas). The {@link DamageSource} attribution is per-holder and
   * gets reaped; a reactor that wants attribution would read it
   * pre-reap (in a future {@code HitFeedbackSystem}).
   */
  @Test
  public void multipleAttributedChanges_foldDeltasIdentically() {
    final Fixture f = newFixture();
    try {
      final EntityId firer = f.ed.createEntity();
      // Make firer a ship too so the self-cost shape applies cleanly.
      f.ed.setComponent(firer, new Energy(1000));
      f.ed.setComponent(firer, new EnergyStats(1000, 1000, 0, 0.0, 0.0, 0.0));
      final EntityId enemy = f.ed.createEntity();
      final EntityId victim = newShip(f.ed, /* pool */ 1000, /* cap */ 1000);

      final EnergySystem energy = f.systems.get(EnergySystem.class, true);
      // Self-cost from firer (BULLET cost-deduction shape)
      energy.damage(firer, -3, firer, WeaponType.BULLET);
      // Hostile damage from enemy
      energy.damage(victim, -25, enemy, WeaponType.BOMB);
      // Hostile damage from firer (different weapon family)
      energy.damage(victim, -10, firer, WeaponType.BURST);

      f.systems.update();

      // Victim folded both hostile changes.
      assertEquals(
          "victim folds 25 + 10 = 35 damage from two attributed changes",
          965,
          f.ed.getComponent(victim, Energy.class).getEnergy());
      // Self-cost lands on firer.
      assertEquals(
          "firer drops 3 from the self-cost deduction",
          997,
          f.ed.getComponent(firer, Energy.class).getEnergy());
      assertNoOrphanChange(f.ed);
    } finally {
      f.shutdown();
    }
  }

  /**
   * Self-cost-deduction shape — when a ship pays its own weapon cost,
   * the {@link DamageSource} carries {@code source = self} +
   * {@code weaponFlag = the weapon family}. This is the convention
   * {@code WeaponsEligibility.deductCostOfAttackBullet} (and siblings)
   * emit. Pin the round-trip so future reactors can fork on
   * {@code source.equals(victim)}.
   */
  @Test
  public void selfCostDeduction_emitsAttributedChangeWithSelfSource() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, /* pool */ 1000, /* cap */ 1000);

      final EnergySystem energy = f.systems.get(EnergySystem.class, true);
      energy.damage(ship, -7, ship, WeaponType.MINE);

      // BEFORE drain — verify a DamageSource sibling exists with
      // self-source attribution.
      final var probe = f.ed.getEntities(DamageSource.class);
      try {
        probe.applyChanges();
        assertEquals("exactly one Change holder carries DamageSource", 1, probe.size());
        for (final var e : probe) {
          final DamageSource src = e.get(DamageSource.class);
          assertNotNull("DamageSource sibling stamped", src);
          assertEquals("source = self for cost-deduction", ship, src.getSource());
          assertEquals("weaponFlag = MINE for mine cost", WeaponType.MINE, src.getWeaponFlag());
        }
      } finally {
        probe.release();
      }

      // AFTER drain — pool drops by 7 and the holder is reaped.
      f.systems.update();
      assertEquals(
          "Self-cost deduction applies through the same fold path",
          993,
          f.ed.getComponent(ship, Energy.class).getEnergy());
      assertNoOrphanChange(f.ed);
    } finally {
      f.shutdown();
    }
  }

  /**
   * After the canonical drain, no entity should still carry the
   * {@code (EnergyChange, ChangeTarget, DamageSource)} triple. Walks
   * each component type independently so we catch a partial reap
   * (which would indicate a bug — all three should be removed by the
   * same {@code removeEntity} call in
   * {@code EnergySystem.drainEnergyChanges}).
   */
  private static void assertNoOrphanChange(final EntityData ed) {
    final var byChange = ed.getEntities(EnergyChange.class);
    try {
      byChange.applyChanges();
      assertEquals("no orphan EnergyChange after drain", 0, byChange.size());
    } finally {
      byChange.release();
    }
    final var byTarget = ed.getEntities(ChangeTarget.class);
    try {
      byTarget.applyChanges();
      assertEquals("no orphan ChangeTarget after drain", 0, byTarget.size());
    } finally {
      byTarget.release();
    }
    final var bySource = ed.getEntities(DamageSource.class);
    try {
      bySource.applyChanges();
      assertEquals("no orphan DamageSource after drain", 0, bySource.size());
    } finally {
      bySource.release();
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
