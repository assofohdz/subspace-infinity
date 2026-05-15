// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.event.EventBus;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.GameSystemManager;
import infinity.es.ChangeTarget;
import infinity.es.Dead;
import infinity.es.PrizeSpawnIntent;
import infinity.es.ship.Energy;
import infinity.es.ship.EnergyStats;
import infinity.es.ship.Player;
import infinity.es.ship.weapons.WeaponType;
import infinity.events.arena.PlayerKilledEvent;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * Pins the hybrid kill-credit shape — ECS many-to-1 attribution on
 * {@link ChangeTarget#source} of the death-edge {@link PrizeSpawnIntent} holder, plus the
 * 1-to-many {@link PlayerKilledEvent} fan-out on the {@link EventBus}.
 *
 * <p>Mirrors {@link EnergySystemDamageSourceTest}'s fixture shape so the suite stays parallel:
 * minimal {@link GameSystemManager} + {@link DefaultEntityData} + {@link EnergySystem}, with
 * {@link Player} + {@link BodyPosition} added to make the death edge fire the prize-intent
 * holder.
 */
public class EnergySystemKillCreditTest {

  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(EnergySystem.class, new EnergySystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  /** Player ship at given position so the death-edge {@link PrizeSpawnIntent} branch fires. */
  private static EntityId newPlayerShip(
      final EntityData ed, final int pool, final int cap, final Vec3d pos) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new Energy(pool));
    ed.setComponent(ship, new EnergyStats(cap, cap, 0, 0.0, 0.0, 0.0));
    ed.setComponent(ship, new Player());
    final BodyPosition bp = new BodyPosition(2);
    bp.addFrame(1L, pos, new Quatd(), true);
    ed.setComponent(ship, bp);
    return ship;
  }

  /**
   * Kill-credit: ECS many-to-1 — the death-edge {@link PrizeSpawnIntent} holder's
   * {@link ChangeTarget#source} carries the killer's {@link EntityId}, not the dying ship.
   * EventBus 1-to-many — {@link PlayerKilledEvent} publishes with victim + killer + weaponFlag.
   */
  @Test
  public void lethalAttributedDamage_killCreditsKillerOnHolder_andPublishesEvent() {
    final Fixture f = newFixture();
    final AtomicReference<PlayerKilledEvent> received = new AtomicReference<>();
    final com.simsilica.event.EventListener<PlayerKilledEvent> listener =
        (type, event) -> received.set(event);
    EventBus.addListener(PlayerKilledEvent.playerKilled, listener);
    try {
      final EntityId killer = f.ed.createEntity();
      final EntityId victim = newPlayerShip(f.ed, 30, 1000, new Vec3d(100, 0, 200));

      final EnergySystem energy = f.systems.get(EnergySystem.class, true);
      energy.damage(victim, -30, killer, WeaponType.BOMB);

      f.systems.update();

      // ECS attribution: ChangeTarget.source on the surviving PrizeSpawnIntent holder.
      assertNotNull("Dead marker stamped", f.ed.getComponent(victim, Dead.class));
      final ChangeTarget intentCt = findPrizeIntentTarget(f.ed, victim);
      assertNotNull("PrizeSpawnIntent emitted for player death", intentCt);
      assertEquals("ChangeTarget.target = victim", victim, intentCt.target());
      assertEquals("ChangeTarget.source = killer (kill-credit)", killer, intentCt.source());

      // EventBus fan-out
      final PlayerKilledEvent event = received.get();
      assertNotNull("playerKilled event published", event);
      assertEquals("event.victim = victim", victim, event.getVictim());
      assertEquals("event.killer = killer", killer, event.getKiller());
      assertEquals("event.weaponFlag = BOMB", WeaponType.BOMB, event.getWeaponFlag());
    } finally {
      EventBus.removeListener(PlayerKilledEvent.playerKilled, listener);
      f.shutdown();
    }
  }

  /**
   * Unattributed death (no {@link infinity.es.DamageSource} sibling on the lethal change) —
   * {@link ChangeTarget#source} falls back to the dying ship (self-credit) and the published
   * event carries {@code killer = null} + {@code weaponFlag = NONE}.
   */
  @Test
  public void lethalUnattributedDamage_selfCreditsVictim_andPublishesNullKiller() {
    final Fixture f = newFixture();
    final AtomicReference<PlayerKilledEvent> received = new AtomicReference<>();
    final com.simsilica.event.EventListener<PlayerKilledEvent> listener =
        (type, event) -> received.set(event);
    EventBus.addListener(PlayerKilledEvent.playerKilled, listener);
    try {
      final EntityId victim = newPlayerShip(f.ed, 30, 1000, new Vec3d(0, 0, 0));

      final EnergySystem energy = f.systems.get(EnergySystem.class, true);
      energy.damage(victim, -30); // unattributed overload

      f.systems.update();

      final ChangeTarget intentCt = findPrizeIntentTarget(f.ed, victim);
      assertNotNull("PrizeSpawnIntent emitted", intentCt);
      assertEquals(
          "Unattributed death falls back to self-credit", victim, intentCt.source());

      final PlayerKilledEvent event = received.get();
      assertNotNull("playerKilled event published even for unattributed death", event);
      assertEquals("event.victim = victim", victim, event.getVictim());
      assertNull("event.killer = null (unattributed)", event.getKiller());
      assertEquals(
          "event.weaponFlag = NONE for unattributed",
          WeaponType.NONE,
          event.getWeaponFlag());
    } finally {
      EventBus.removeListener(PlayerKilledEvent.playerKilled, listener);
      f.shutdown();
    }
  }

  /**
   * Two attributed lethal hits on the same tick fold into one Energy write — kill-credit follows
   * the last-seen negative-delta {@link infinity.es.DamageSource} per target (Zay-ES insertion
   * order = damage call order; Subspace canon: last damager wins).
   */
  @Test
  public void multipleAttributedHits_killCreditFollowsLastSource() {
    final Fixture f = newFixture();
    final AtomicReference<PlayerKilledEvent> received = new AtomicReference<>();
    final com.simsilica.event.EventListener<PlayerKilledEvent> listener =
        (type, event) -> received.set(event);
    EventBus.addListener(PlayerKilledEvent.playerKilled, listener);
    try {
      final EntityId firstShooter = f.ed.createEntity();
      final EntityId finisher = f.ed.createEntity();
      final EntityId victim = newPlayerShip(f.ed, 50, 1000, new Vec3d(0, 0, 0));

      final EnergySystem energy = f.systems.get(EnergySystem.class, true);
      // Both attributed hits in the same tick — combined -60 kills the 50-pool victim.
      // The LATER call (finisher) must win kill-credit per last-damager-wins semantics.
      energy.damage(victim, -20, firstShooter, WeaponType.BULLET);
      energy.damage(victim, -40, finisher, WeaponType.BOMB);

      f.systems.update();

      assertNotNull("Dead marker stamped", f.ed.getComponent(victim, Dead.class));
      final ChangeTarget intentCt = findPrizeIntentTarget(f.ed, victim);
      assertNotNull("PrizeSpawnIntent emitted", intentCt);
      assertEquals(
          "Last-damager wins: ChangeTarget.source = finisher (the later .damage() call)",
          finisher,
          intentCt.source());

      final PlayerKilledEvent event = received.get();
      assertNotNull("playerKilled event published", event);
      assertEquals("event.killer = finisher (last damager)", finisher, event.getKiller());
      assertEquals(
          "event.weaponFlag = BOMB (finisher's weapon, not BULLET)",
          WeaponType.BOMB,
          event.getWeaponFlag());
    } finally {
      EventBus.removeListener(PlayerKilledEvent.playerKilled, listener);
      f.shutdown();
    }
  }

  /**
   * Surviving (non-lethal) hit AFTER a lethal one in the same tick still wins kill-credit, because
   * the writer caches the last negative-delta {@link infinity.es.DamageSource} regardless of which
   * blow "crossed zero." Pins the documented divergence from "lethal blow wins" semantics.
   */
  @Test
  public void lethalThenMinorHit_creditsMinorDamager_notLethalSource() {
    final Fixture f = newFixture();
    final AtomicReference<PlayerKilledEvent> received = new AtomicReference<>();
    final com.simsilica.event.EventListener<PlayerKilledEvent> listener =
        (type, event) -> received.set(event);
    EventBus.addListener(PlayerKilledEvent.playerKilled, listener);
    try {
      final EntityId lethalShooter = f.ed.createEntity();
      final EntityId minorShooter = f.ed.createEntity();
      final EntityId victim = newPlayerShip(f.ed, 50, 1000, new Vec3d(0, 0, 0));

      final EnergySystem energy = f.systems.get(EnergySystem.class, true);
      // Lethal hit first, minor hit second — same tick, both attributed.
      energy.damage(victim, -100, lethalShooter, WeaponType.BOMB);
      energy.damage(victim, -5, minorShooter, WeaponType.BULLET);

      f.systems.update();

      final ChangeTarget intentCt = findPrizeIntentTarget(f.ed, victim);
      assertNotNull("PrizeSpawnIntent emitted", intentCt);
      assertEquals(
          "Last-damager wins (Subspace canon), even though lethalShooter dealt the killing blow",
          minorShooter,
          intentCt.source());

      final PlayerKilledEvent event = received.get();
      assertNotNull("playerKilled event published", event);
      assertEquals("event.killer = minorShooter", minorShooter, event.getKiller());
    } finally {
      EventBus.removeListener(PlayerKilledEvent.playerKilled, listener);
      f.shutdown();
    }
  }

  /** Walk the surviving PrizeSpawnIntent + ChangeTarget pair targeting {@code victim}. */
  private static ChangeTarget findPrizeIntentTarget(final EntityData ed, final EntityId victim) {
    final EntitySet intents = ed.getEntities(PrizeSpawnIntent.class, ChangeTarget.class);
    try {
      intents.applyChanges();
      for (final Entity e : intents) {
        final ChangeTarget ct = e.get(ChangeTarget.class);
        if (victim.equals(ct.target())) {
          return ct;
        }
      }
      return null;
    } finally {
      intents.release();
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
