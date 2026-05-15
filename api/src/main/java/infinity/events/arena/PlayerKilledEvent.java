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
