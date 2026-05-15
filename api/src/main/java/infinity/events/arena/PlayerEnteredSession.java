// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.events.arena;

import com.simsilica.es.EntityId;
import com.simsilica.event.EventType;

/** Fires ONCE per player per login session — not per arena entry. When arena-swap lands, add a sibling {@code PlayerEnteredArena}. */
public final class PlayerEnteredSession {

  /** Server-side publish: bridge fans out to all connected clients via RMI. */
  public static final EventType<PlayerEnteredSession> playerEnteredSession =
      EventType.create("PlayerEnteredSession", PlayerEnteredSession.class);

  /** Client-side local-bus republish: distinct type so server-side listeners don't loop in single-JVM dev mode. */
  public static final EventType<PlayerEnteredSession> playerEnteredSessionLocal =
      EventType.create("PlayerEnteredSessionLocal", PlayerEnteredSession.class);

  private final EntityId player;
  private final String playerName;

  public PlayerEnteredSession(final EntityId player, final String playerName) {
    this.player = player;
    this.playerName = playerName;
  }

  public EntityId getPlayer() {
    return player;
  }

  public String getPlayerName() {
    return playerName;
  }
}
