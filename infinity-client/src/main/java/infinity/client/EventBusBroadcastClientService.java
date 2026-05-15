// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client;

import com.jme3.network.service.AbstractClientService;
import com.jme3.network.service.ClientServiceManager;
import com.jme3.network.service.rmi.RmiClientService;
import com.simsilica.es.EntityId;
import com.simsilica.event.EventBus;
import infinity.events.arena.PlayerEnteredSession;
import infinity.events.arena.PlayerKilledEvent;
import infinity.events.arena.TargetedEvent;
import infinity.net.EventBusBroadcastListener;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Receives server-fan-out {@code EventBus} events over RMI and republishes them on the local client {@link EventBus}; informational only (ADR-0005).
 * Reserved for low-frequency lifecycle events (kill, join, leave). Tick-rate state goes through SimEthereal, not this bridge.
 * Do NOT use these events to derive roster / player list — late joiners miss prior events. Build that from Zay-ES component visibility.
 * Late-binding consumers (AppStates that initialize AFTER login) drain the pending queue on attach — see {@link #drainPendingTargeted(Consumer)}.
 */
public final class EventBusBroadcastClientService extends AbstractClientService {

  static Logger log = LoggerFactory.getLogger(EventBusBroadcastClientService.class);

  /** Cap to prevent unbounded growth if no consumer ever drains; in practice the queue holds a single welcome at most. */
  private static final int MAX_PENDING = 50;

  private final Queue<TargetedEvent> pendingTargeted = new ConcurrentLinkedQueue<>();
  private final BroadcastCallback callback;

  public EventBusBroadcastClientService() {
    this.callback = new BroadcastCallback(this);
  }

  @Override
  protected void onInitialize(final ClientServiceManager s) {
    final RmiClientService rmiService = getService(RmiClientService.class);
    if (rmiService == null) {
      throw new IllegalStateException("EventBusBroadcastClientService requires RMI service");
    }
    // Register early so server-initiated callbacks never beat us to the punch.
    rmiService.share(callback, EventBusBroadcastListener.class);
  }

  /** Late-binding consumers (e.g. ChatState attached AFTER login) call this in initialize() to catch up on welcomes that arrived before they subscribed. Drains all queued events. */
  public void drainPendingTargeted(final Consumer<TargetedEvent> consumer) {
    while (true) {
      final TargetedEvent ev = pendingTargeted.poll();
      if (ev == null) {
        break;
      }
      consumer.accept(ev);
    }
  }

  private void enqueueTargeted(final TargetedEvent ev) {
    pendingTargeted.add(ev);
    while (pendingTargeted.size() > MAX_PENDING) {
      pendingTargeted.poll();
    }
  }

  /** Server-side RMI target — republishes on the local client EventBus on the networking thread, plus queues for late-binding consumers. */
  private static final class BroadcastCallback implements EventBusBroadcastListener {

    private final EventBusBroadcastClientService owner;

    BroadcastCallback(final EventBusBroadcastClientService owner) {
      this.owner = owner;
    }

    @Override
    public void onPlayerKilled(final EntityId victim, final EntityId killer, final byte weaponFlag) {
      if (log.isTraceEnabled()) {
        log.trace("onPlayerKilled(victim={}, killer={}, flag={})", victim, killer, weaponFlag);
      }
      EventBus.publish(
          PlayerKilledEvent.playerKilled, new PlayerKilledEvent(victim, killer, weaponFlag));
    }

    @Override
    public void onTargetedEvent(final String tag, final String payload) {
      if (log.isDebugEnabled()) {
        log.debug("onTargetedEvent tag={} payload={}", tag, payload);
      }
      final TargetedEvent ev = new TargetedEvent(Set.of(), tag, payload);
      // Queue first so late-binding consumers (ChatState) can drain on init,
      // then publish for live subscribers that are already attached.
      owner.enqueueTargeted(ev);
      EventBus.publish(TargetedEvent.targetedLocal, ev);
    }

    @Override
    public void onPlayerEnteredSession(final EntityId player, final String playerName) {
      if (log.isDebugEnabled()) {
        log.debug("onPlayerEnteredSession player={} name={}", player, playerName);
      }
      EventBus.publish(
          PlayerEnteredSession.playerEnteredSessionLocal,
          new PlayerEnteredSession(player, playerName));
    }
  }
}
