// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.net;

import com.jme3.network.service.rmi.Asynchronous;
import com.simsilica.es.EntityId;

/** Informational fan-out of server-side {@code EventBus} events to client subscribers; not a write channel (see ADR-0005). */
public interface EventBusBroadcastListener {

  /** Mirrors {@code PlayerKilledEvent.playerKilled}; {@code killer == null} for unattributed deaths. */
  @Asynchronous
  void onPlayerKilled(EntityId victim, EntityId killer, byte weaponFlag);

  /** Per-recipient {@code TargetedEvent}; server pre-filters and dispatches per recipient — the recipient set itself is server-side targeting metadata, not wire payload. */
  @Asynchronous
  void onTargetedEvent(String tag, String payload);

  /** Zone-wide fan-out of {@code PlayerEnteredSession}; informational only, every connected client receives. */
  @Asynchronous
  void onPlayerEnteredSession(EntityId player, String playerName);
}
