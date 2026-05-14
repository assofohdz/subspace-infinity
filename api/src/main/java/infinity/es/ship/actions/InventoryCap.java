// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/** Shape contract for per-ship inventory-stats records that carry a hard-cap field (BurstStats, BrickStats, DecoyStats, PortalStats, RepelStats, RocketStats, ThorStats). */
public interface InventoryCap extends EntityComponent {

  int max();
}
