// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Marker component identifying this entity as a player ship.
 *
 * @author AFahrenholz
 */
public class Player implements EntityComponent {
  public Player() {
    // empty constructor for serialization
  }
}
