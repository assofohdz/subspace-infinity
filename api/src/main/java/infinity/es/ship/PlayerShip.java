// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Marker component identifying this entity as a player-controlled ship (the durable
 * player entity is a separate ECS entity, linked back via {@code Parent} on the ship
 * and {@code CurrentShip} on the player — see player-vs-ship-identity PRD).
 *
 * @author AFahrenholz
 */
public class PlayerShip implements EntityComponent {
  public PlayerShip() {
    // empty constructor for serialization
  }
}
