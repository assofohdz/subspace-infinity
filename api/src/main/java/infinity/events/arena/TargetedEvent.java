// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.events.arena;

import com.simsilica.es.EntityId;
import com.simsilica.event.EventType;
import java.util.Set;

/** Per-recipient fan-out: server resolves {@code recipients} to HostedConnections, RMIs only those. */
public final class TargetedEvent {

  public static final EventType<TargetedEvent> targeted =
      EventType.create("Targeted", TargetedEvent.class);

  private final Set<EntityId> recipients;
  private final String tag;
  private final String payload;

  public TargetedEvent(final Set<EntityId> recipients, final String tag, final String payload) {
    // Defensive copy: emit-site mutations to the caller's set must not change the published event.
    this.recipients = Set.copyOf(recipients);
    this.tag = tag;
    this.payload = payload;
  }

  public Set<EntityId> getRecipients() {
    return recipients;
  }

  public String getTag() {
    return tag;
  }

  public String getPayload() {
    return payload;
  }
}
