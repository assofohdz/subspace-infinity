// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * On a player ship: number of crowns currently held in a KOTH-style arena. Canonical
 * writer is the {@code Crowns} mechanic — distributes 1 per active player at round-start,
 * transfers (all crowns) to the killer on attributed death, and removes the component at
 * round-end. Absent component = ghost / no crowns. Class-not-record per the wire-crossing
 * convention (jME3 FieldSerializer can't reflectively set record fields).
 */
public final class CrownHolder implements EntityComponent {

  private final int crowns;

  public CrownHolder() {
    this(0);
  }

  public CrownHolder(final int crowns) {
    this.crowns = crowns;
  }

  public int crowns() {
    return crowns;
  }
}
