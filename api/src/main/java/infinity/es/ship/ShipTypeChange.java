// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;
import infinity.Ship;

/**
 * Value-replacement payload for {@link ShipType}; pairs with {@link infinity.es.ChangeTarget}. Drained by {@code AvatarSystem} (canonical writer chosen because it already owns the ship-swap lifecycle — {@code =N} chat handling, {@code ResetLivePool} stamping, frequency-restrictor gate; a separate {@code ShipTypeSystem} would duplicate that lifecycle for one emit site). See ADR 0001.
 */
public record ShipTypeChange(Ship newShipType) implements EntityComponent {

  public ShipTypeChange() {
    this(null);
  }
}
