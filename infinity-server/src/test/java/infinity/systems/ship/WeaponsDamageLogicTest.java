// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.es.Damage;
import infinity.es.Dead;
import infinity.es.Frequency;
import infinity.es.Parent;
import infinity.es.arena.FriendlyFireMode;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
import org.junit.Test;

/**
 * Regression test for the sign convention on {@link WeaponsDamageLogic#applyDirectHitDamage}:
 * {@link Damage#getIntendedDamage()} is stored positive; the call site MUST negate before passing
 * to {@link EnergySystem#damage} since {@code EnergyChange} treats positive as heal, negative as
 * damage. Pre-fix, the bot's Energy stayed at max because positive delta clamped at
 * {@code EnergyStats.max()}.
 */
public final class WeaponsDamageLogicTest {

  @Test
  public void directHit_appliesAsDamage_notHeal() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    final EnergySystem energy = systems.register(EnergySystem.class, new EnergySystem());
    systems.initialize();
    systems.start();
    try {
      // Victim ship — 100 HP cap, no recharge.
      final EntityId victim = ed.createEntity();
      ed.setComponent(victim, new Energy(100));
      ed.setComponent(victim, new EnergyStats(100, 100, 0, 0.0, 0.0, 0.0));
      ed.setComponent(victim, new Frequency(0));

      // Attacker ship on a different freq + FF off so the gate passes.
      final EntityId attacker = ed.createEntity();
      ed.setComponent(attacker, new Frequency(1));
      ed.setComponent(attacker, new FriendlyFireMode(0));

      // Damage entity — bullet-shape (positive intendedDamage, Parent points at attacker).
      final EntityId damageEntity = ed.createEntity();
      ed.setComponent(damageEntity, new Damage(0L, 30, null));
      ed.setComponent(damageEntity, new Parent(attacker));

      final Damage damage = ed.getComponent(damageEntity, Damage.class);
      WeaponsDamageLogic.applyDirectHitDamage(ed, energy, damageEntity, damage, victim, 0L);
      systems.update();

      assertEquals("Direct-hit deducts intendedDamage from victim Energy",
          70, ed.getComponent(victim, Energy.class).getEnergy());
    } finally {
      systems.stop();
      systems.terminate();
    }
  }

  @Test
  public void deadEntity_doesNotTakeFurtherDamage_orRefireDeath() {
    // Regression: BaseEnergyDrainSystem (status drain) was firing every tick on dead bots,
    // re-entering handleDeath, spamming the death log, and thrashing Dead/Decay set+remove
    // cycles. Fix: applyDelta gates on Dead → no-op after first death.
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    final EnergySystem energy = systems.register(EnergySystem.class, new EnergySystem());
    systems.initialize();
    systems.start();
    try {
      final EntityId victim = ed.createEntity();
      ed.setComponent(victim, new Energy(50));
      ed.setComponent(victim, new EnergyStats(100, 100, 0, 0.0, 0.0, 0.0));
      ed.setComponent(victim, new Frequency(0));

      final EntityId attacker = ed.createEntity();
      ed.setComponent(attacker, new Frequency(1));
      ed.setComponent(attacker, new FriendlyFireMode(0));

      final EntityId damageEntity = ed.createEntity();
      ed.setComponent(damageEntity, new Damage(0L, 75, null));
      ed.setComponent(damageEntity, new Parent(attacker));
      WeaponsDamageLogic.applyDirectHitDamage(
          ed, energy, damageEntity, ed.getComponent(damageEntity, Damage.class), victim, 0L);
      systems.update();
      // Sanity: victim is dead.
      assertNotNull(ed.getComponent(victim, Dead.class));
      final int energyAfterDeath = ed.getComponent(victim, Energy.class).getEnergy();

      // Simulate a status drain firing on the dead corpse before Decay reaps it.
      energy.damage(victim, -10); // unattributed, sub-zero — historical death-loop trigger

      systems.update();

      assertEquals(
          "Dead entity's Energy must not change from post-death state — second damage no-op'd",
          energyAfterDeath, ed.getComponent(victim, Energy.class).getEnergy());
    } finally {
      systems.stop();
      systems.terminate();
    }
  }

  @Test
  public void directHit_killsVictim_whenEnergyDropsToZero() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    final EnergySystem energy = systems.register(EnergySystem.class, new EnergySystem());
    systems.initialize();
    systems.start();
    try {
      final EntityId victim = ed.createEntity();
      ed.setComponent(victim, new Energy(50));
      ed.setComponent(victim, new EnergyStats(100, 100, 0, 0.0, 0.0, 0.0));
      ed.setComponent(victim, new Frequency(0));

      final EntityId attacker = ed.createEntity();
      ed.setComponent(attacker, new Frequency(1));
      ed.setComponent(attacker, new FriendlyFireMode(0));

      final EntityId damageEntity = ed.createEntity();
      ed.setComponent(damageEntity, new Damage(0L, 75, null)); // > 50 → death-edge
      ed.setComponent(damageEntity, new Parent(attacker));

      final Damage damage = ed.getComponent(damageEntity, Damage.class);
      WeaponsDamageLogic.applyDirectHitDamage(ed, energy, damageEntity, damage, victim, 0L);
      systems.update();

      assertNotNull("Victim stamped Dead when Energy drops to <= 0",
          ed.getComponent(victim, Dead.class));
    } finally {
      systems.stop();
      systems.terminate();
    }
  }
}
