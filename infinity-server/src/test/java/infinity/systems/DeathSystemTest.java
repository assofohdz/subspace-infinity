// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.es.common.Decay;
import com.simsilica.event.EventBus;
import com.simsilica.event.EventListener;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.GameSystemManager;
import infinity.es.ChangeTarget;
import infinity.es.Dead;
import infinity.es.KilledBy;
import infinity.es.PrizeSpawnIntent;
import infinity.es.ship.weapons.WeaponType;
import infinity.events.arena.PlayerKilledEvent;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * Pins {@link DeathSystem}'s ownership of the death-cycle side effects per ADR-0001:
 * stamps {@link Decay} so the SiO2 reaper can despawn; publishes {@link PlayerKilledEvent}
 * for non-ECS consumers; emits a {@link PrizeSpawnIntent} with {@link KilledBy} attribution.
 */
public final class DeathSystemTest {

  @Test
  public void deadEntity_getsDecayStamped_andPublishesEvent() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(DeathSystem.class, new DeathSystem());
    systems.initialize();
    systems.start();
    final AtomicReference<PlayerKilledEvent> received = new AtomicReference<>();
    final EventListener<PlayerKilledEvent> listener = (type, event) -> received.set(event);
    EventBus.addListener(PlayerKilledEvent.playerKilled, listener);
    try {
      final EntityId victim = ed.createEntity();
      ed.setComponent(victim, new Dead(0L));

      systems.update();

      assertNotNull("DeathSystem must stamp Decay so the SiO2 reaper can despawn",
          ed.getComponent(victim, Decay.class));
      final PlayerKilledEvent event = received.get();
      assertNotNull("PlayerKilledEvent published for any Dead entity", event);
      assertEquals(victim, event.getVictim());
      assertNull("Unattributed death — no KilledBy stamped, killer is null", event.getKiller());
    } finally {
      EventBus.removeListener(PlayerKilledEvent.playerKilled, listener);
      systems.stop();
      systems.terminate();
    }
  }

  @Test
  public void attributedDeath_emitsPrizeIntent_withKillerCredit() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(DeathSystem.class, new DeathSystem());
    systems.initialize();
    systems.start();
    try {
      final EntityId killer = ed.createEntity();
      final EntityId victim = ed.createEntity();
      ed.setComponent(victim, new Dead(0L));
      ed.setComponent(victim, new KilledBy(killer, WeaponType.BULLET));
      final BodyPosition bp = new BodyPosition(10);
      bp.addFrame(0L, new Vec3d(1, 2, 3), new Quatd(), true);
      ed.setComponent(victim, bp);

      systems.update();

      // PrizeSpawnIntent emitted via a new holder entity.
      final EntitySet intents = ed.getEntities(PrizeSpawnIntent.class, ChangeTarget.class);
      intents.applyChanges();
      assertEquals("One PrizeSpawnIntent holder per death", 1, intents.size());
      final ChangeTarget ct = intents.iterator().next().get(ChangeTarget.class);
      assertEquals("Intent's ChangeTarget.target = victim", victim, ct.target());
      assertEquals("Intent's ChangeTarget.source = killer (credit)", killer, ct.source());
      intents.release();

      // KilledBy is consumed + removed by DeathSystem so the entity is clean for reap.
      assertNull("KilledBy removed after use", ed.getComponent(victim, KilledBy.class));
    } finally {
      systems.stop();
      systems.terminate();
    }
  }
}
