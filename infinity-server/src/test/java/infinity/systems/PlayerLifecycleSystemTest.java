// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.event.EventBus;
import com.simsilica.sim.GameSystemManager;
import infinity.events.arena.PlayerEnteredSession;
import infinity.net.AccountEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Pins {@link PlayerLifecycleSystem} login-driven trigger semantics. */
public final class PlayerLifecycleSystemTest {

  private GameSystemManager systems;
  private Capturer capturer;

  @Before
  public void setUp() {
    systems = new GameSystemManager();
    systems.register(EntityData.class, new DefaultEntityData());
    systems.register(PlayerLifecycleSystem.class, new PlayerLifecycleSystem());
    systems.initialize();
    systems.start();
    capturer = new Capturer();
    EventBus.addListener(capturer, PlayerEnteredSession.playerEnteredSession);
  }

  @After
  public void tearDown() {
    EventBus.removeListener(capturer, PlayerEnteredSession.playerEnteredSession);
    systems.stop();
    systems.terminate();
  }

  @Test
  public void playerLoggedOn_publishesPlayerEnteredSession() {
    final EntityId player = new EntityId(42L);

    EventBus.publish(AccountEvent.playerLoggedOn, new AccountEvent(null, "Alice", player));

    assertEquals(1, capturer.events.size());
    assertEquals(player, capturer.events.get(0).getPlayer());
    assertEquals("Alice", capturer.events.get(0).getPlayerName());
  }

  @Test
  public void multipleLogins_eachFireOnce() {
    final EntityId alice = new EntityId(1L);
    final EntityId bob = new EntityId(2L);

    EventBus.publish(AccountEvent.playerLoggedOn, new AccountEvent(null, "Alice", alice));
    EventBus.publish(AccountEvent.playerLoggedOn, new AccountEvent(null, "Bob", bob));

    assertEquals("each login publishes once", 2, capturer.events.size());
  }

  private static final class Capturer {
    final List<PlayerEnteredSession> events = new ArrayList<>();

    public void onPlayerEnteredSession(final PlayerEnteredSession event) {
      events.add(event);
    }
  }
}
