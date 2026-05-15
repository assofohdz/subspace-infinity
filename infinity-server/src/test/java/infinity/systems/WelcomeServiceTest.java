// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.event.EventBus;
import com.simsilica.sim.GameSystemManager;
import infinity.events.arena.PlayerEnteredSession;
import infinity.events.arena.TargetedEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/** Pins {@link WelcomeService}: {@link PlayerEnteredSession} → {@link TargetedEvent} with tag {@code "welcome"} and a single-recipient set. */
public final class WelcomeServiceTest {

  private GameSystemManager systems;
  private Capturer capturer;

  @Before
  public void setUp() {
    systems = new GameSystemManager();
    systems.register(WelcomeService.class, new WelcomeService());
    systems.initialize();
    systems.start();
    capturer = new Capturer();
    EventBus.addListener(capturer, TargetedEvent.targeted);
  }

  @After
  public void tearDown() {
    EventBus.removeListener(capturer, TargetedEvent.targeted);
    systems.stop();
    systems.terminate();
  }

  @Test
  public void playerEnteredSession_publishesTargetedWelcome() {
    final EntityId player = new EntityId(42L);

    EventBus.publish(
        PlayerEnteredSession.playerEnteredSession, new PlayerEnteredSession(player, "Asser"));

    assertEquals(1, capturer.events.size());
    final TargetedEvent t = capturer.events.get(0);
    assertEquals("welcome", t.getTag());
    assertEquals("Asser", t.getPayload());
    assertEquals(1, t.getRecipients().size());
    assertTrue(t.getRecipients().contains(player));
  }

  private static final class Capturer {
    final List<TargetedEvent> events = new ArrayList<>();

    public void onTargeted(final TargetedEvent event) {
      events.add(event);
    }
  }
}
