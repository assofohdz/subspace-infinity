// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.server;

import com.jme3.network.HostedConnection;
import com.jme3.network.service.AbstractHostedConnectionService;
import com.jme3.network.service.HostedServiceManager;
import com.jme3.network.service.rmi.RmiHostedService;
import com.jme3.network.service.rmi.RmiRegistry;
import com.simsilica.event.EventBus;
import infinity.events.arena.PlayerKilledEvent;
import infinity.net.EventBusBroadcastListener;
import infinity.sim.util.InfinityRunTimeException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Bridges server {@link EventBus} events to client subscribers over RMI; informational fan-out, never a write channel (ADR-0005). */
public class EventBusBroadcastHostedService extends AbstractHostedConnectionService {

  private static final String ATTRIBUTE_LISTENER = "eventbus.broadcastListener";

  static Logger log = LoggerFactory.getLogger(EventBusBroadcastHostedService.class);

  private final List<HostedConnection> connections = new CopyOnWriteArrayList<>();
  private RmiHostedService rmiService;

  public EventBusBroadcastHostedService() {
    // no-op: parent ctor defaults autoHost=true
  }

  @Override
  protected void onInitialize(final HostedServiceManager s) {
    rmiService = getService(RmiHostedService.class);
    if (rmiService == null) {
      throw new InfinityRunTimeException(
          "EventBusBroadcastHostedService requires an RMI service.");
    }
    // Subscribe to the curated set of EventTypes; extend here as new cross-tier events land.
    EventBus.addListener(this, PlayerKilledEvent.playerKilled);
  }

  @Override
  public void terminate(final HostedServiceManager serviceManager) {
    EventBus.removeListener(this, PlayerKilledEvent.playerKilled);
    super.terminate(serviceManager);
  }

  @Override
  public void startHostingOnConnection(final HostedConnection conn) {
    log.debug("startHostingOnConnection({})", conn);
    connections.add(conn);
  }

  @Override
  public void stopHostingOnConnection(final HostedConnection conn) {
    log.debug("stopHostingOnConnection({})", conn);
    connections.remove(conn);
    conn.setAttribute(ATTRIBUTE_LISTENER, null);
  }

  /** EventBus reflective dispatch — name pattern is {@code on<EventTypeName>}. */
  public void onPlayerKilled(final PlayerKilledEvent event) {
    broadcastPlayerKilled(event);
  }

  /** Test seam — visible-for-testing fan-out body, callable without a HostedConnection. */
  protected void broadcastPlayerKilled(final PlayerKilledEvent event) {
    for (final HostedConnection conn : connections) {
      final EventBusBroadcastListener listener = getListener(conn);
      if (listener != null) {
        listener.onPlayerKilled(event.getVictim(), event.getKiller(), event.getWeaponFlag());
      }
    }
  }

  private EventBusBroadcastListener getListener(final HostedConnection conn) {
    EventBusBroadcastListener listener = conn.getAttribute(ATTRIBUTE_LISTENER);
    if (listener == null) {
      // Lazy resolve — the client shares its callback during its own onInitialize,
      // which may not have completed when our startHostingOnConnection fires.
      final RmiRegistry rmi = rmiService.getRmiRegistry(conn);
      listener = rmi.getRemoteObject(EventBusBroadcastListener.class);
      if (listener != null) {
        conn.setAttribute(ATTRIBUTE_LISTENER, listener);
      }
    }
    return listener;
  }
}
