// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.server.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.jme3.network.HostedConnection;
import com.jme3.network.Message;
import com.jme3.network.Server;
import com.simsilica.es.EntityId;
import infinity.sim.MessageTypes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/** Pins {@link InfinityChatHostedService#postPrivateMessage}: target lookup + wire-stable {@code [PM from X]} decoration. */
public final class InfinityChatHostedServicePrivateMessageTest {

  @Test
  public void postPrivateMessage_targetOnline_decoratesFromAndDeliversToOnlyTarget() {
    final EntityId alice = new EntityId(1L);
    final EntityId bob = new EntityId(2L);
    final FakeConnection aliceConn = new FakeConnection(alice);
    final FakeConnection bobConn = new FakeConnection(bob);
    final CapturingService svc = new CapturingService();
    svc.register(alice, aliceConn);
    svc.register(bob, bobConn);

    svc.postPrivateMessage("System", MessageTypes.MESSAGE, alice, "hi alice");

    assertEquals(1, svc.deliveries.size());
    final Delivery d = svc.deliveries.get(0);
    assertEquals(aliceConn, d.conn);
    assertEquals("[PM from System]", d.from);
    assertEquals("hi alice", d.message);
  }

  @Test
  public void postPrivateMessage_targetOffline_dropsWithoutException() {
    final EntityId offline = new EntityId(999L);
    final CapturingService svc = new CapturingService();
    // intentionally no registration for `offline`

    svc.postPrivateMessage("System", MessageTypes.MESSAGE, offline, "ignored");

    assertTrue("no delivery when target offline", svc.deliveries.isEmpty());
    assertTrue(svc.lookupMisses.contains(offline));
  }

  /** Test seam — overrides lookup + deliver to skip real network plumbing. */
  private static final class CapturingService extends InfinityChatHostedService {

    final Map<EntityId, HostedConnection> connByEntity = new HashMap<>();
    final Set<EntityId> lookupMisses = new java.util.HashSet<>();
    final List<Delivery> deliveries = new ArrayList<>();

    void register(final EntityId id, final HostedConnection conn) {
      connByEntity.put(id, conn);
    }

    @Override
    protected HostedConnection lookupConnection(final EntityId targetEntityId) {
      final HostedConnection conn = connByEntity.get(targetEntityId);
      if (conn == null) {
        lookupMisses.add(targetEntityId);
      }
      return conn;
    }

    @Override
    protected void deliverPrivate(
        final HostedConnection conn, final String from, final String message) {
      deliveries.add(new Delivery(conn, from, message));
    }
  }

  private static final class Delivery {
    final HostedConnection conn;
    final String from;
    final String message;

    Delivery(final HostedConnection conn, final String from, final String message) {
      this.conn = conn;
      this.from = from;
      this.message = message;
    }
  }

  /** Stub: identity-only HostedConnection. */
  private static final class FakeConnection implements HostedConnection {
    private final EntityId tag;

    FakeConnection(final EntityId tag) {
      this.tag = tag;
    }

    @Override
    public Server getServer() {
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
      // no-op
    }

    @Override
    public void send(final Message message) {
      // no-op
    }

    @Override
    public void send(final int channel, final Message message) {
      // no-op
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
    public Set<String> attributeNames() {
      return java.util.Collections.emptySet();
    }
  }
}
