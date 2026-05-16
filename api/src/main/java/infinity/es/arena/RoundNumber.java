// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.arena;

import com.simsilica.es.EntityComponent;

/** Current round counter on the arena entity; canonical writer is the active {@code roundStructure} module. */
public final class RoundNumber implements EntityComponent {

  private final int value;

  public RoundNumber() {
    this(0);
  }

  public RoundNumber(final int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
