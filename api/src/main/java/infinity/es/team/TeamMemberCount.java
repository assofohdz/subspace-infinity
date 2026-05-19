// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.team;

import com.simsilica.es.EntityComponent;

/** Live member count for a {@link TeamEntity}. Written by the arena's active {@code TeamSetupModule}. */
public record TeamMemberCount(int count) implements EntityComponent {

  public TeamMemberCount() {
    this(0);
  }
}
