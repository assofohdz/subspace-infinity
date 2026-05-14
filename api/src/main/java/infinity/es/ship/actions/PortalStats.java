// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

/** Stats record for the Portal aspect: hard-cap inventory count. See ADR 0001. */
public record PortalStats(int max) implements InventoryCap {

  public PortalStats() {
    this(0);
  }
}
