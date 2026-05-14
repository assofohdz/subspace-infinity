// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import infinity.es.ship.actions.InventoryCap;

/** Stats record for the GravBomb aspect: hard-cap inventory count + per-fire cooldown duration. Live cooldown clock stays per-instance on {@link GravityBombFireDelay}. Infinity extension — no canonical {@code [GravBomb]} section. See ADR 0001. */
public record GravBombStats(int max, long fireDelayMillis) implements InventoryCap {

  public GravBombStats() {
    this(0, 0L);
  }
}
