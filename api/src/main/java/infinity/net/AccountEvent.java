// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.net;

import com.google.common.base.MoreObjects;

import com.jme3.network.HostedConnection;

import com.simsilica.es.EntityId;
import com.simsilica.event.EventType;

/**
 * Events that are send to the event bus for different account state related
 * events. These are server-side only events and are available to the other
 * hosted services and possible the game systems in some rarer cases.
 *
 * @author Paul Speed
 */
public class AccountEvent {

    /**
     * Signals that a player has successfully logged in.
     */
    public static final EventType<AccountEvent> playerLoggedOn = EventType.create("PlayerLoggedOn", AccountEvent.class);

    /**
     * Signals that a player has logged out.
     */
    public static final EventType<AccountEvent> playerLoggedOff = EventType.create("PlayerLoggedOff", AccountEvent.class);

    private final HostedConnection conn;
    private final String playerName;
    private final EntityId playerEntity;

    public AccountEvent(final HostedConnection conn, final String playerName, final EntityId playerEntity) {
        this.conn = conn;
        this.playerName = playerName;
        this.playerEntity = playerEntity;
    }

    public HostedConnection getConnection() {
        return conn;
    }

    public String getPlayerName() {
        return playerName;
    }

    public EntityId getPlayerEntity() {
        return playerEntity;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass().getSimpleName()).add("conn", conn).add("playerName", playerName)
                .add("playerEntity", playerEntity).toString();
    }
}
