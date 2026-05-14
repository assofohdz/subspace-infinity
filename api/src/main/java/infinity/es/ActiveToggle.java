// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/** Shape contract for stateful on/off toggle components (status-family `CloakActive` / `StealthActive` / `XRadarActive` / `AntiwarpActive`). Presence-only markers like `RocketActive` are a different shape and do NOT implement this. */
public interface ActiveToggle extends EntityComponent {

  boolean isActive();
}
