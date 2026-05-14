// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/** Shape contract for value-replacement boolean-toggle Change records used by the RaM Change-entity recipe (per ADR 0001). Implemented by the status-family {@code *ActiveChange} records (Cloak/Stealth/XRadar/Antiwarp). */
public interface ToggleChange extends EntityComponent {

  boolean newValue();
}
