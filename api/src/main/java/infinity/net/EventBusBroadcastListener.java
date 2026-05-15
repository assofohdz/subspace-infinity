// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.net;

import com.jme3.network.service.rmi.Asynchronous;
import com.simsilica.es.EntityId;
import java.util.Set;

/** Informational fan-out of server-side {@code EventBus} events to client subscribers; not a write channel (see ADR-0005). */
public interface EventBusBroadcastListener {

  /** Mirrors {@code PlayerKilledEvent.playerKilled}; {@code killer == null} for unattributed deaths. */
  @Asynchronous
  void onPlayerKilled(EntityId victim, EntityId killer, byte weaponFlag);

  /** Per-recipient {@code TargetedEvent}; server pre-filters so only listed connections receive the call. */
  @Asynchronous
  void onTargetedEvent(Set<EntityId> recipients, String tag, String payload);
}
