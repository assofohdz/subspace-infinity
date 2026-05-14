// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.actions;

/** Stats record for the Thor aspect: hard-cap inventory count + per-fire cooldown duration. Replaces the previous {@code ThorMaxCount} component and the cold duration field of {@code ThorFireDelay}; the live cooldown clock stays per-instance on {@link ThorFireDelay}. See ADR 0001. */
public record ThorStats(int max, long fireDelayMillis) implements InventoryCap {

  public ThorStats() {
    this(0, 0L);
  }
}
