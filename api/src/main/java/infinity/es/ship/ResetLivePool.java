// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/** Marker forcing {@code ShipSpawnSystem} to re-project with {@code resetLivePool=true} (resets Energy + {@code *CurrentLevel} from {@code ShipConfig}); set by {@code AvatarSystem} on ship-swap. */
public class ResetLivePool implements EntityComponent {

  public ResetLivePool() {
    // marker
  }
}
