/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.sim.events;

import com.simsilica.es.EntityId;
import com.simsilica.event.EventType;
import com.simsilica.event.PlayerEntityEvent;

/**
 *
 * @author Asser
 */
public class PlayerEvent extends PlayerEntityEvent {

    public static EventType<PlayerEvent> playerBanned = EventType.create("PlayerBanned", PlayerEvent.class);

    public PlayerEvent(EntityId player) {
        super(player);
    }

}
