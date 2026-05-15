// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client;

import com.jme3.network.service.AbstractClientService;
import com.jme3.network.service.ClientServiceManager;
import com.jme3.network.service.rmi.RmiClientService;
import com.simsilica.es.EntityId;
import com.simsilica.event.EventBus;
import infinity.events.arena.PlayerKilledEvent;
import infinity.events.arena.TargetedEvent;
import infinity.net.EventBusBroadcastListener;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Receives server-fan-out {@code EventBus} events over RMI and republishes them on the local client {@link EventBus}; informational only (ADR-0005). */
public final class EventBusBroadcastClientService extends AbstractClientService {

  static Logger log = LoggerFactory.getLogger(EventBusBroadcastClientService.class);

  private final BroadcastCallback callback = new BroadcastCallback();

  public EventBusBroadcastClientService() {
    // no-op: stateless wiring; RMI share happens in onInitialize
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

  /** Server-side RMI target — republishes on the local client EventBus on the networking thread. */
  private static final class BroadcastCallback implements EventBusBroadcastListener {

    @Override
    public void onPlayerKilled(final EntityId victim, final EntityId killer, final byte weaponFlag) {
      if (log.isTraceEnabled()) {
        log.trace("onPlayerKilled(victim={}, killer={}, flag={})", victim, killer, weaponFlag);
      }
      EventBus.publish(
          PlayerKilledEvent.playerKilled, new PlayerKilledEvent(victim, killer, weaponFlag));
    }

    @Override
    public void onTargetedEvent(
        final Set<EntityId> recipients, final String tag, final String payload) {
      // MVP welcome wire-check: log the welcome message at INFO so manual smoke-tests can verify
      // end-to-end without booting a HUD AppState. Future UI rendering subscribes via EventBus.
      if ("welcome".equals(tag) && log.isInfoEnabled()) {
        log.info("Welcome, {}!", payload);
      } else if (log.isTraceEnabled()) {
        log.trace("onTargetedEvent(recipients={}, tag={}, payload={})", recipients, tag, payload);
      }
      EventBus.publish(TargetedEvent.targeted, new TargetedEvent(recipients, tag, payload));
    }
  }
}
