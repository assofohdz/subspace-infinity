// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.events.zone;

import com.simsilica.es.EntityId;
import com.simsilica.event.PlayerEntityEvent;

/** Zone-level event payload for player lifecycle. No EventType constants; reserved for future wiring. */
public class PlayerEvent extends PlayerEntityEvent {

    public PlayerEvent(final EntityId player) {
        super(player);
    }

}
