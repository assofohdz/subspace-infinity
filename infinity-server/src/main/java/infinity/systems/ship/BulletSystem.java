// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.BulletLevel;
import infinity.es.ship.weapons.BulletChange;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.BulletStats;

/** Canonical writer for live {@link BulletCurrentLevel}. All drain/clamp logic lives in {@link BaseWeaponLevelSystem}; this subclass binds the four types + accessor lambdas + the level constructor. */
public class BulletSystem
    extends BaseWeaponLevelSystem<BulletCurrentLevel, BulletChange, BulletStats, BulletLevel> {

  public BulletSystem() {
    super(
        BulletCurrentLevel.class, BulletChange.class, BulletStats.class,
        BulletLevel.values(),
        BulletCurrentLevel::getLevel,
        BulletStats::max,
        level -> new BulletCurrentLevel(level));
  }
}
