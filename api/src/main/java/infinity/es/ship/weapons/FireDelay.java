// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/** Shape contract for per-weapon cooldown components; {@code getPercent()} reaches {@code 1.0} when ready to fire. */
public interface FireDelay extends EntityComponent {

  double getPercent();
}
