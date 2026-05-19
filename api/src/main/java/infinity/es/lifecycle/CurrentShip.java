// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.lifecycle;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;

/**
 * Player → ship reverse link. Present when the player is alive and piloting a ship; absent
 * when the player is a ghost (between death and respawn). Canonical writer: {@code AvatarSystem}.
 * The forward link (ship → player) is the existing {@code Parent} component on the ship.
 *
 * <p>Client reads this to resolve the durable player entity's current ship without ever
 * holding a stale ship-entity-id watcher when the ship dies. See
 * {@code .scratch/player-vs-ship-identity/PRD.md} (slice P2/P4).
 */
public final class CurrentShip implements EntityComponent {

  private final EntityId shipId;

  public CurrentShip() {
    this(null);
  }

  public CurrentShip(final EntityId shipId) {
    this.shipId = shipId;
  }

  public EntityId getShipId() {
    return shipId;
  }
}
