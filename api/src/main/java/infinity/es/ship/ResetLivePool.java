// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Marker forcing {@code ShipSpawnSystem} to re-project with {@code resetLivePool=true} (resets Energy + {@code *CurrentLevel}); set by {@code AvatarSystem} on ship-swap.
 *
 * <p>Needed because Zay-ES coalesces a same-tick {@code removeComponent} + {@code setComponent} of {@code ShipType}
 * into a single "changed" event (tuning branch), not added/removed — so swap-driven re-projection would miss the
 * "treat as respawn" branch otherwise. Read + cleared after one projection tick.
 */
public class ResetLivePool implements EntityComponent {

  public ResetLivePool() {
    // marker
  }
}
