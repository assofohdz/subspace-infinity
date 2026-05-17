// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.events.arena;

import com.simsilica.es.EntityId;
import com.simsilica.event.EventType;
import infinity.es.ship.weapons.WeaponType;

/** 1-to-many fan-out of a player death — victim + killer + weapon family. Sibling to ECS death-edge kill-credit on {@code ChangeTarget.source}. */
public class PlayerKilledEvent {

  public static final EventType<PlayerKilledEvent> playerKilled =
      EventType.create("PlayerKilled", PlayerKilledEvent.class);

  /**
   * Client-local re-broadcast variant. The client's RMI bridge republishes the server's
   * {@link #playerKilled} on THIS type so server-side listeners (also subscribed to
   * {@link #playerKilled}) do not re-fire in single-JVM dev mode. Per the rule in
   * {@code .claude/rules/client-read-only.md}: client-side UI / HUD code should
   * subscribe to {@code playerKilledLocal}, not {@link #playerKilled}.
   */
  public static final EventType<PlayerKilledEvent> playerKilledLocal =
      EventType.create("PlayerKilledLocal", PlayerKilledEvent.class);

  private final EntityId victim;
  private final EntityId killer;
  private final byte weaponFlag;

  /** {@code killer == null} for unattributed (regen / env); {@link WeaponType#NONE} for non-weapon damage. */
  public PlayerKilledEvent(final EntityId victim, final EntityId killer, final byte weaponFlag) {
    this.victim = victim;
    this.killer = killer;
    this.weaponFlag = weaponFlag;
  }

  public EntityId getVictim() {
    return victim;
  }

  public EntityId getKiller() {
    return killer;
  }

  public byte getWeaponFlag() {
    return weaponFlag;
  }
}
