// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.events.arena;

import com.simsilica.es.EntityId;
import com.simsilica.event.EventType;

/**
 * Game-related events. Mostly done for interfacing better with the bots of
 * SubSpace Continuum
 *
 * @author Asser
 */
public class ShipEvent {

    public static EventType<ShipEvent> shipDestroyed = EventType.create("ShipDestroyed", ShipEvent.class);
    public static EventType<ShipEvent> shipSpawned = EventType.create("ShipSpawned", ShipEvent.class);
    public static EventType<ShipEvent> weaponFiring = EventType.create("WeaponFiring", ShipEvent.class);
    public static EventType<ShipEvent> weaponFired = EventType.create("WeaponFired", ShipEvent.class);
    public static EventType<ShipEvent> shipChangeAllowed = EventType.create("ShipChangeAllowed", ShipEvent.class);
    public static EventType<ShipEvent> shipChangeDenied = EventType.create("ShipChangeDenied", ShipEvent.class);

    private final EntityId shipId;

    public ShipEvent(final EntityId shipId) {
        this.shipId = shipId;
    }

    public EntityId getShipId() {
        return shipId;
    }

}
