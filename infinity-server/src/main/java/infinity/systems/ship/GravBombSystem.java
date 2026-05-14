// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.es.ship.weapons.GravBomb;
import infinity.es.ship.weapons.GravBombChange;
import infinity.es.ship.weapons.GravBombStats;

/** Canonical writer for live {@link GravBomb}. All drain/clamp logic lives in {@link BaseInventoryCountSystem}; this subclass just binds the three types + the count constructor. */
public class GravBombSystem extends BaseInventoryCountSystem<GravBomb, GravBombChange, GravBombStats> {

  public GravBombSystem() {
    super(GravBomb.class, GravBombChange.class, GravBombStats.class, count -> new GravBomb(count));
  }
}
