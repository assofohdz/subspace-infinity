// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.BombLevel;
import infinity.es.ship.weapons.MineChange;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineStats;

/** Canonical writer for live {@link MineCurrentLevel} (mines reuse {@link BombLevel}). All drain/clamp logic lives in {@link BaseWeaponLevelSystem}; this subclass binds the four types + accessor lambdas + the level constructor. */
public class MineSystem
    extends BaseWeaponLevelSystem<MineCurrentLevel, MineChange, MineStats, BombLevel> {

  public MineSystem() {
    super(
        MineCurrentLevel.class, MineChange.class, MineStats.class,
        BombLevel.values(),
        MineCurrentLevel::getLevel,
        MineStats::max,
        level -> new MineCurrentLevel(level));
  }
}
