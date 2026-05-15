// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.server;

import com.jme3.network.HostedConnection;
import com.jme3.network.service.AbstractHostedConnectionService;
import com.jme3.network.service.HostedServiceManager;
import com.jme3.network.service.rmi.RmiHostedService;
import com.jme3.network.service.rmi.RmiRegistry;
import com.simsilica.es.EntityId;
import com.simsilica.event.EventBus;
import infinity.events.arena.PlayerEnteredSession;
import infinity.events.arena.PlayerKilledEvent;
import infinity.events.arena.TargetedEvent;
import infinity.net.EventBusBroadcastListener;
import infinity.sim.util.InfinityRunTimeException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridges server {@link EventBus} events to client subscribers over RMI; informational fan-out, never a write channel (ADR-0005).
 * Reserved for low-frequency lifecycle events (kill, join, leave). NEVER subscribe tick-rate events here — reliable RMI buffer floods.
 * For per-tick state, use SimEthereal component sync. DO NOT use this bridge for state derivable via SimEthereal — late joiners miss prior events.
 */
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
    // WHY: Informational fan-out for transient UI reactions (toasts, kill feed). DO NOT use this
    // bridge for state derivable via SimEthereal — late joiners miss prior events. Roster /
    // player list should be built from Zay-ES component visibility (arena-membership).
    EventBus.addListener(this, PlayerKilledEvent.playerKilled);
    EventBus.addListener(this, TargetedEvent.targeted);
    EventBus.addListener(this, PlayerEnteredSession.playerEnteredSession);
  }

  @Override
  public void terminate(final HostedServiceManager serviceManager) {
    EventBus.removeListener(this, PlayerKilledEvent.playerKilled);
    EventBus.removeListener(this, TargetedEvent.targeted);
    EventBus.removeListener(this, PlayerEnteredSession.playerEnteredSession);
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

  /** EventBus reflective dispatch — per-recipient filter happens here. */
  public void onTargeted(final TargetedEvent event) {
    broadcastTargeted(event);
  }

  /** EventBus reflective dispatch — zone-wide fan-out, every connection receives. */
  public void onPlayerEnteredSession(final PlayerEnteredSession event) {
    broadcastPlayerEntered(event);
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

  /** Test seam — visible-for-testing zone-wide fan-out of {@link PlayerEnteredSession}. */
  protected void broadcastPlayerEntered(final PlayerEnteredSession event) {
    if (log.isDebugEnabled()) {
      log.debug(
          "broadcastPlayerEntered player={} name={} connections={}",
          event.getPlayer(),
          event.getPlayerName(),
          connections.size());
    }
    for (final HostedConnection conn : connections) {
      final EventBusBroadcastListener listener = getListener(conn);
      if (listener != null) {
        listener.onPlayerEnteredSession(event.getPlayer(), event.getPlayerName());
      }
    }
  }

  /** Test seam — sends to only the recipients whose connections are currently hosted; recipient set is server-side only, never crosses the wire. */
  protected void broadcastTargeted(final TargetedEvent event) {
    if (log.isDebugEnabled()) {
      log.debug(
          "broadcastTargeted tag={} payload={} recipients={}",
          event.getTag(),
          event.getPayload(),
          event.getRecipients());
    }
    for (final EntityId recipient : event.getRecipients()) {
      final HostedConnection conn = lookupConnection(recipient);
      if (conn == null) {
        if (log.isDebugEnabled()) {
          log.debug("No hosted connection for recipient {} (offline or wrong arena)", recipient);
        }
        continue;
      }
      final EventBusBroadcastListener listener = getListener(conn);
      if (listener != null) {
        // The full recipient set is server-side targeting metadata; do NOT include it on the wire.
        listener.onTargetedEvent(event.getTag(), event.getPayload());
      }
    }
  }

  /** EntityId → HostedConnection lookup via the sibling {@code AccountHostedService} session map. */
  protected HostedConnection lookupConnection(final EntityId recipient) {
    final AccountHostedService accounts = getService(AccountHostedService.class);
    if (accounts == null) {
      return null;
    }
    return accounts.getHostedConnection(recipient);
  }

  protected EventBusBroadcastListener getListener(final HostedConnection conn) {
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
