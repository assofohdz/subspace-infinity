// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.es.Buff;
import infinity.es.DamageSource;
import infinity.es.HealthChange;
import infinity.es.ship.Energy;
import infinity.es.ship.Health;
import infinity.es.ship.weapons.WeaponType;
import org.junit.Test;

/**
 * Replacement-as-Mutation slice 1 — pins the new attributed
 * {@link EnergySystem#damage(EntityId, int, EntityId, byte)} overload that
 * lets reactors fork on intent type (damage vs regen vs cost-deduction). The
 * companion {@code EnergySystemIntentTest} (spawn-harness teammate's
 * deliverable) covers the unattributed legacy shape; this file adds the
 * sibling tests for the new {@link DamageSource} component.
 *
 * <p>Mirrors the {@code EnergySystemIntentTest} fixture shape (minimal
 * {@link GameSystemManager} + {@link DefaultEntityData} + {@link EnergySystem})
 * so the two test files stay parallel: same setup, asserts on adjacent
 * features. Kept as a separate file to avoid rebasing collisions with
 * spawn-harness's WIP.
 */
public class EnergySystemDamageSourceTest {

  // ──────────────────────────────────────────────────────────────────
  // Fixture helpers — duplicated from EnergySystemIntentTest so this
  // file is independently runnable. If both files land in the same
  // commit a follow-up slice can extract them.
  // ──────────────────────────────────────────────────────────────────

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(EnergySystem.class, new EnergySystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  private static EntityId newShip(final EntityData ed, final int health, final int cap) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new Health(health));
    ed.setComponent(ship, new Energy(cap));
    return ship;
  }

  // ──────────────────────────────────────────────────────────────────
  // Tests — the contract WeaponsImpactSystem / WeaponsReaperSystem rely on.
  // ──────────────────────────────────────────────────────────────────

  /**
   * The attributed overload writes the same Health-folded result as the
   * unattributed call (RaM rule 6 — same delta, same fold). The presence of
   * the {@link DamageSource} sibling does not change the canonical-writer
   * arithmetic; it is purely metadata for downstream reactors.
   */
  @Test
  public void attributedDamage_appliesSameHealthFold_asUnattributed() {
    final Fixture f = newFixture();
    try {
      final EntityId attacker = f.ed.createEntity();
      final EntityId victim = newShip(f.ed, /* health */ 1000, /* cap */ 1000);

      final EnergySystem energy = f.systems.get(EnergySystem.class, true);
      energy.damage(victim, -40, attacker, WeaponType.BOMB);

      f.systems.update();

      assertEquals(
          "Attributed damage folds delta identically to the legacy call",
          960,
          f.ed.getComponent(victim, Health.class).getHealth());
    } finally {
      f.shutdown();
    }
  }

  /**
   * The {@link DamageSource} sibling is reaped alongside the canonical
   * {@link Buff} + {@link HealthChange} pair when {@code EnergySystem.update}
   * removes the intent entity. Pins the "no-leak" property requested in
   * {@code EnergySystemIntentTest.intentForMissingTarget_isStillReaped}'s
   * follow-up note.
   */
  @Test
  public void attributedIntent_reapsDamageSourceSibling() {
    final Fixture f = newFixture();
    try {
      final EntityId attacker = f.ed.createEntity();
      final EntityId victim = newShip(f.ed, /* health */ 1000, /* cap */ 1000);

      final EnergySystem energy = f.systems.get(EnergySystem.class, true);
      energy.damage(victim, -10, attacker, WeaponType.BULLET);

      // Snapshot the intent's id before drain — the only intent entity is
      // the one we just emitted (no other system creates intents in this
      // fixture).
      // We can't grab the id directly from the damage(...) helper without
      // changing its return type; instead we drain and assert the intent
      // EntitySet ends up empty after the tick.
      f.systems.update();

      // Walk all entities with HealthChange / Buff / DamageSource — none
      // should remain after the canonical drain.
      assertNoOrphanIntent(f.ed);
    } finally {
      f.shutdown();
    }
  }

  /**
   * RaM rule 3 (writers fold previous-tick value + intents) — two attributed
   * intents on the same target in the same tick collapse into one Health
   * write (folded sum of deltas). The {@link DamageSource} attribution is
   * per-intent and gets reaped; the reactor that wants attribution would
   * read it pre-reap (in a future {@code HitFeedbackSystem}). This test
   * pins that the cap-clamp and per-target isolation continue to work with
   * the attributed shape.
   */
  @Test
  public void multipleAttributedIntents_foldDeltasIdentically() {
    final Fixture f = newFixture();
    try {
      final EntityId firer = f.ed.createEntity();
      final EntityId enemy = f.ed.createEntity();
      final EntityId victim = newShip(f.ed, /* health */ 1000, /* cap */ 1000);

      final EnergySystem energy = f.systems.get(EnergySystem.class, true);
      // Self-cost from firer (BULLET cost-deduction shape)
      energy.damage(firer, -3, firer, WeaponType.BULLET);
      // Hostile damage from enemy
      energy.damage(victim, -25, enemy, WeaponType.BOMB);
      // Hostile damage from firer (different weapon family)
      energy.damage(victim, -10, firer, WeaponType.BURST);

      f.systems.update();

      // Victim folded both hostile intents.
      assertEquals(
          "victim folds 25 + 10 = 35 damage from two attributed intents",
          965,
          f.ed.getComponent(victim, Health.class).getHealth());
      // No orphan intents.
      assertNoOrphanIntent(f.ed);
    } finally {
      f.shutdown();
    }
  }

  /**
   * Self-cost-deduction shape — when a ship pays its own weapon cost, the
   * attributed {@link DamageSource} carries {@code source = self} +
   * {@code weaponFlag = the weapon family}. This is the convention
   * {@link WeaponsEligibility#deductCostOfAttackBullet} (and siblings) emit;
   * pin the round-trip so future reactors can fork on
   * {@code source.equals(victim)} to distinguish "I shot myself in the foot"
   * from "an enemy shot me."
   */
  @Test
  public void selfCostDeduction_emitsAttributedIntentWithSelfSource() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, /* health */ 1000, /* cap */ 1000);

      final EnergySystem energy = f.systems.get(EnergySystem.class, true);
      energy.damage(ship, -7, ship, WeaponType.MINE);

      // BEFORE drain — the intent entity is observable in the EntityData;
      // verify a DamageSource sibling exists with self-source attribution.
      // (We can't capture the intent id from the helper, so probe the only
      // entity that has a DamageSource component.)
      final var probe = f.ed.getEntities(DamageSource.class);
      try {
        probe.applyChanges();
        assertEquals("exactly one intent entity carries DamageSource", 1, probe.size());
        for (final var e : probe) {
          final DamageSource src = e.get(DamageSource.class);
          assertNotNull("DamageSource sibling stamped", src);
          assertEquals("source = self for cost-deduction", ship, src.getSource());
          assertEquals("weaponFlag = MINE for mine cost", WeaponType.MINE, src.getWeaponFlag());
        }
      } finally {
        probe.release();
      }

      // AFTER drain — Health drops by 7 and the intent is reaped.
      f.systems.update();
      assertEquals(
          "Self-cost deduction applies through the same fold path",
          993,
          f.ed.getComponent(ship, Health.class).getHealth());
      assertNoOrphanIntent(f.ed);
    } finally {
      f.shutdown();
    }
  }

  // ──────────────────────────────────────────────────────────────────
  // Assertion helpers
  // ──────────────────────────────────────────────────────────────────

  /**
   * After the canonical-writer drain, no entity should still carry the
   * {@code (Buff, HealthChange, DamageSource)} triple. Walks each component
   * type independently so we catch a partial reap (which would indicate a
   * bug — all three should be removed by the same {@code removeEntity} call
   * in {@code EnergySystem.collectBuffChanges}).
   */
  private static void assertNoOrphanIntent(final EntityData ed) {
    final var byBuff = ed.getEntities(Buff.class);
    try {
      byBuff.applyChanges();
      assertEquals("no orphan Buff after drain", 0, byBuff.size());
    } finally {
      byBuff.release();
    }
    final var byChange = ed.getEntities(HealthChange.class);
    try {
      byChange.applyChanges();
      assertEquals("no orphan HealthChange after drain", 0, byChange.size());
    } finally {
      byChange.release();
    }
    final var bySource = ed.getEntities(DamageSource.class);
    try {
      bySource.applyChanges();
      assertNull(
          "no orphan DamageSource after drain (must be reaped with the intent)",
          bySource.isEmpty() ? null : bySource.iterator().next());
    } finally {
      bySource.release();
    }
  }

  // ──────────────────────────────────────────────────────────────────
  // Tiny holder so the tests can shut down the manager from finally.
  // ──────────────────────────────────────────────────────────────────

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
