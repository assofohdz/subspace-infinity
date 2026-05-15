// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.simsilica.es.EntityId;
import com.simsilica.event.EventBus;
import infinity.es.ship.weapons.WeaponType;
import infinity.events.arena.PlayerKilledEvent;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * In-process bridge test — verifies that publishing a {@link PlayerKilledEvent}
 * on the server-side {@link EventBus} reaches the service's reflective dispatch
 * method (the test seam {@code broadcastPlayerKilled}).
 *
 * <p>Skips the network stack: subclass overrides the fan-out body to capture
 * the event instead of iterating connections. The full RMI plumbing is
 * exercised end-to-end at runtime against a real client.
 */
public final class EventBusBroadcastHostedServiceTest {

  private CapturingService service;

  @Before
  public void setUp() {
    service = new CapturingService();
    // Mirror onInitialize's EventBus subscription — onInitialize itself
    // requires a HostedServiceManager + RmiHostedService which we don't have
    // in a unit-test context.
    EventBus.addListener(service, PlayerKilledEvent.playerKilled);
  }

  @After
  public void tearDown() {
    EventBus.removeListener(service, PlayerKilledEvent.playerKilled);
  }

  @Test
  public void publishOnEventBus_triggersBroadcast() {
    final EntityId victim = new EntityId(42L);
    final EntityId killer = new EntityId(7L);
    final byte weaponFlag = WeaponType.BOMB;

    EventBus.publish(
        PlayerKilledEvent.playerKilled,
        new PlayerKilledEvent(victim, killer, weaponFlag));

    assertEquals("one broadcast per publish", 1, service.broadcasts.size());
    final PlayerKilledEvent captured = service.broadcasts.get(0);
    assertNotNull(captured);
    assertEquals(victim, captured.getVictim());
    assertEquals(killer, captured.getKiller());
    assertEquals(weaponFlag, captured.getWeaponFlag());
  }

  /** Test seam — captures broadcasts instead of iterating connections. */
  private static final class CapturingService extends EventBusBroadcastHostedService {

    final List<PlayerKilledEvent> broadcasts = new ArrayList<>();

    @Override
    protected void broadcastPlayerKilled(final PlayerKilledEvent event) {
      broadcasts.add(event);
    }
  }
}
