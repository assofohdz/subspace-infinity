// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.team;

import com.simsilica.es.EntityComponent;

/**
 * Marker — this entity represents a team. Pairs with {@code Frequency} (the team's freq number),
 * {@link TeamMemberCount} (live member count), and {@code ArenaId} (owning arena). Canonical
 * writer: the active {@code TeamSetupModule} per ADR-0008.
 */
public final class TeamEntity implements EntityComponent {
  public TeamEntity() {
    // empty for serialization
  }
}
