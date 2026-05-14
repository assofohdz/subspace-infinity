// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/** Shape contract for additive integer-delta Change records used by the RaM Change-entity recipe (per ADR 0001). Implemented by inventory, movement, weapon-level, and energy Change records. */
public interface DeltaChange extends EntityComponent {

  int delta();
}
