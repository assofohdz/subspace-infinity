// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.events.zone;

import com.simsilica.es.EntityId;
import com.simsilica.event.EventType;
import com.simsilica.event.PlayerEntityEvent;

/**
 * Zone-level event for player lifecycle changes (joined, banned, etc.).
 *
 * @author Asser
 */
public class PlayerEvent extends PlayerEntityEvent {

    public static final EventType<PlayerEvent> playerBanned = EventType.create("PlayerBanned", PlayerEvent.class);

    public PlayerEvent(final EntityId player) {
        super(player);
    }

}
