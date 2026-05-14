// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

/**
 * Stats record for the Repel aspect: hard-cap inventory count.
 *
 * <p>Note — diverges from Wave 4b task spec table
 * ({@code RepelStats(max, distance, speed)}). Subspace {@code [Repel]
 * RepelDistance} / {@code RepelSpeed} are arena-global knobs read at
 * fire-time from {@link infinity.config.RepelConfig} and stamped on the
 * spawned repel-effect entity (see {@code ConsumableSystem.createRepel}) —
 * they are not per-ship, so they do not live in the ship-side stats record.
 *
 * @see infinity.config.RepelConfig
 */
public record RepelStats(int max) implements InventoryCap {

  public RepelStats() {
    this(0);
  }
}
