// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client;

import com.google.common.base.MoreObjects;

import com.simsilica.es.EntityId;
import com.simsilica.event.EventType;

/** Client-side event-bus payload for transport + session lifecycle. Mirrors {@code AccountEvent} shape. */
public class ClientEvent {

    /** Placeholder account id until account-resolution is wired client-side. */
    public static final String UNRESOLVED_ACCOUNT_ID = "unresolved";

    public static final EventType<ClientEvent> clientConnected = EventType.create("ClientConnected", ClientEvent.class);
    public static final EventType<ClientEvent> sessionStarted = EventType.create("SessionStarted", ClientEvent.class);
    public static final EventType<ClientEvent> sessionEnded = EventType.create("SessionEnded", ClientEvent.class);
    public static final EventType<ClientEvent> clientDisconnected = EventType.create("ClientDisconnected", ClientEvent.class);

    private final String accountId;
    private final EntityId playerEntity;
    private final int sessionId;
    private final long timestampMillis;

    private ClientEvent(final String accountId, final EntityId playerEntity, final int sessionId) {
        this.accountId = accountId;
        this.playerEntity = playerEntity;
        this.sessionId = sessionId;
        this.timestampMillis = System.currentTimeMillis();
    }

    /** Pre-login transport-up / transport-down event; player entity not yet resolved. */
    public static ClientEvent forTransport(final int sessionId) {
        return new ClientEvent(UNRESOLVED_ACCOUNT_ID, null, sessionId);
    }

    /** Post-login session event with the resolved player entity. */
    public static ClientEvent forSession(final EntityId playerEntity, final int sessionId) {
        return new ClientEvent(UNRESOLVED_ACCOUNT_ID, playerEntity, sessionId);
    }

    public String getAccountId() {
        return accountId;
    }

    public EntityId getPlayerEntity() {
        return playerEntity;
    }

    public int getSessionId() {
        return sessionId;
    }

    public long getTimestampMillis() {
        return timestampMillis;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass().getSimpleName())
                .add("accountId", accountId)
                .add("playerEntity", playerEntity)
                .add("sessionId", sessionId)
                .add("timestampMillis", timestampMillis)
                .toString();
    }
}
