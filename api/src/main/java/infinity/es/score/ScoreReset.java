// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.score;

import com.simsilica.es.EntityComponent;

/** Transient on the arena entity — signals {@code ScoreCoordinatorSystem} to zero the named score tier for all players in this arena. */
public record ScoreReset(Scope scope) implements EntityComponent {

  /** Which score tier to zero. {@code MATCH} support lands in F2d. */
  public enum Scope {
    ROUND,
    MATCH
  }

  public ScoreReset() {
    this(Scope.ROUND);
  }
}
