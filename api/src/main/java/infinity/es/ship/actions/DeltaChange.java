// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/** Shape contract for additive integer-delta Change records (BurstChange, BrickChange, DecoyChange, PortalChange, RepelChange, RocketChange, ThorChange). */
public interface DeltaChange extends EntityComponent {

  int delta();
}
