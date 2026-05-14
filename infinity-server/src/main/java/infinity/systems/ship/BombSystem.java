// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.BombLevel;
import infinity.es.ship.weapons.BombChange;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombStats;

/** Canonical writer for live {@link BombCurrentLevel}. All drain/clamp logic lives in {@link BaseWeaponLevelSystem}; this subclass binds the four types + accessor lambdas + the level constructor. */
public class BombSystem
    extends BaseWeaponLevelSystem<BombCurrentLevel, BombChange, BombStats, BombLevel> {

  public BombSystem() {
    super(
        BombCurrentLevel.class, BombChange.class, BombStats.class,
        BombLevel.values(),
        BombCurrentLevel::getLevel,
        BombStats::max,
        level -> new BombCurrentLevel(level));
  }
}
