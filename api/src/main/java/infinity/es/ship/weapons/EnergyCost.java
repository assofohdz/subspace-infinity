// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/** Shape contract for components/stats that carry a per-fire energy cost (BulletStats, BombStats, MineStats). */
public interface EnergyCost extends EntityComponent {

  int energyCost();
}
