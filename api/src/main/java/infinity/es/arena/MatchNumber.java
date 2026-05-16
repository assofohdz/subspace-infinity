// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.arena;

import com.simsilica.es.EntityComponent;

/** Current match counter on the arena entity; canonical writer is the active {@code matchStructure} module via the dispatcher. */
public final class MatchNumber implements EntityComponent {

  private final int value;

  public MatchNumber() {
    this(0);
  }

  public MatchNumber(final int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
