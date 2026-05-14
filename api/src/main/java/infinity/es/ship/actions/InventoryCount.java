// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/** Shape contract for per-ship inventory-count components (Burst, Brick, Decoy, Portal, Repel, Rocket, ThorCurrentCount). */
public interface InventoryCount extends EntityComponent {

  int count();
}
