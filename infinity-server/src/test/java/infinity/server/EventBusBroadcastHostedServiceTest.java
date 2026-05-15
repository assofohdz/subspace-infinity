// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.jme3.network.HostedConnection;
import com.simsilica.es.EntityId;
import com.simsilica.event.EventBus;
import infinity.es.ship.weapons.WeaponType;
import infinity.events.arena.PlayerKilledEvent;
import infinity.events.arena.TargetedEvent;
import infinity.net.EventBusBroadcastListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    EventBus.addListener(service, TargetedEvent.targeted);
  }

  @After
  public void tearDown() {
    EventBus.removeListener(service, PlayerKilledEvent.playerKilled);
    EventBus.removeListener(service, TargetedEvent.targeted);
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

  @Test
  public void targetedEvent_singleRecipient_dispatchesToOnlyThatConnection() {
    final EntityId alice = new EntityId(1L);
    final EntityId bob = new EntityId(2L);
    final FakeListener aliceListener = new FakeListener();
    final FakeListener bobListener = new FakeListener();
    service.register(alice, aliceListener);
    service.register(bob, bobListener);

    EventBus.publish(
        TargetedEvent.targeted, new TargetedEvent(Set.of(alice), "welcome", "Alice"));

    assertEquals("Alice receives exactly one call", 1, aliceListener.calls.size());
    assertTrue("Bob receives nothing", bobListener.calls.isEmpty());
    final FakeListener.Call call = aliceListener.calls.get(0);
    assertEquals("welcome", call.tag);
    assertEquals("Alice", call.payload);
    assertTrue("recipients arg propagated", call.recipients.contains(alice));
  }

  @Test
  public void targetedEvent_multiRecipient_dispatchesToEachListedConnection() {
    final EntityId alice = new EntityId(1L);
    final EntityId bob = new EntityId(2L);
    final EntityId carol = new EntityId(3L);
    final EntityId dave = new EntityId(4L);
    final FakeListener aliceL = new FakeListener();
    final FakeListener bobL = new FakeListener();
    final FakeListener carolL = new FakeListener();
    final FakeListener daveL = new FakeListener();
    service.register(alice, aliceL);
    service.register(bob, bobL);
    service.register(carol, carolL);
    service.register(dave, daveL);

    EventBus.publish(
        TargetedEvent.targeted,
        new TargetedEvent(Set.of(alice, bob, carol), "achievement", "FirstBlood"));

    assertEquals(1, aliceL.calls.size());
    assertEquals(1, bobL.calls.size());
    assertEquals(1, carolL.calls.size());
    assertTrue("Dave is not in the recipient set", daveL.calls.isEmpty());
  }

  @Test
  public void targetedEvent_offlineRecipient_dropsWithoutException() {
    final EntityId online = new EntityId(1L);
    final EntityId offline = new EntityId(999L);
    final FakeListener onlineL = new FakeListener();
    service.register(online, onlineL);
    // offline intentionally not registered

    EventBus.publish(
        TargetedEvent.targeted,
        new TargetedEvent(Set.of(online, offline), "welcome", "Hi"));

    assertEquals("online recipient still receives", 1, onlineL.calls.size());
    assertFalse(
        "no lookup match should not throw",
        service.lookupMisses.isEmpty());
    assertTrue(service.lookupMisses.contains(offline));
  }

  /** Test seam — captures broadcasts instead of iterating connections; fakes the EntityId→listener map. */
  private static final class CapturingService extends EventBusBroadcastHostedService {

    final List<PlayerKilledEvent> broadcasts = new ArrayList<>();
    final Map<EntityId, FakeListener> listenersByRecipient = new HashMap<>();
    final Map<HostedConnection, FakeListener> listenersByConn = new HashMap<>();
    final Map<EntityId, HostedConnection> connsByRecipient = new HashMap<>();
    final Set<EntityId> lookupMisses = new HashSet<>();

    void register(final EntityId recipient, final FakeListener listener) {
      // Marker connection — we never call HostedConnection methods on it, just identity comparison.
      final HostedConnection conn = new FakeConnection(recipient);
      listenersByRecipient.put(recipient, listener);
      listenersByConn.put(conn, listener);
      connsByRecipient.put(recipient, conn);
    }

    @Override
    protected void broadcastPlayerKilled(final PlayerKilledEvent event) {
      broadcasts.add(event);
    }

    @Override
    protected HostedConnection lookupConnection(final EntityId recipient) {
      final HostedConnection conn = connsByRecipient.get(recipient);
      if (conn == null) {
        lookupMisses.add(recipient);
      }
      return conn;
    }

    @Override
    protected EventBusBroadcastListener getListener(final HostedConnection conn) {
      return listenersByConn.get(conn);
    }
  }

  /** Captures RMI calls so tests can assert exact recipients and payload. */
  private static final class FakeListener implements EventBusBroadcastListener {

    final List<Call> calls = new ArrayList<>();

    @Override
    public void onPlayerKilled(final EntityId victim, final EntityId killer, final byte flag) {
      // not exercised by targeted tests
    }

    @Override
    public void onTargetedEvent(
        final Set<EntityId> recipients, final String tag, final String payload) {
      calls.add(new Call(recipients, tag, payload));
    }

    static final class Call {
      final Set<EntityId> recipients;
      final String tag;
      final String payload;

      Call(final Set<EntityId> recipients, final String tag, final String payload) {
        this.recipients = recipients;
        this.tag = tag;
        this.payload = payload;
      }
    }
  }

  /** Stub: only identity matters in this test; no real network plumbing. */
  private static final class FakeConnection implements HostedConnection {
    private final EntityId tag;

    FakeConnection(final EntityId tag) {
      this.tag = tag;
    }

    @Override
    public com.jme3.network.Server getServer() {
      return null;
    }

    @Override
    public int getId() {
      return (int) tag.getId();
    }

    @Override
    public String getAddress() {
      return "fake";
    }

    @Override
    public void close(final String reason) {
      // no-op for the stub
    }

    @Override
    public void send(final com.jme3.network.Message message) {
      // no-op for the stub
    }

    @Override
    public void send(final int channel, final com.jme3.network.Message message) {
      // no-op for the stub
    }

    @Override
    public Object setAttribute(final String name, final Object value) {
      return null;
    }

    @Override
    public <T> T getAttribute(final String name) {
      return null;
    }

    @Override
    public java.util.Set<String> attributeNames() {
      return java.util.Collections.emptySet();
    }
  }
}
