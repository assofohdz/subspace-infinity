// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.BulletMaxLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>LEVEL family.</b> Bumps {@link BulletCurrentLevel} toward {@link BulletMaxLevel}.
 * Pure component read; see {@link BombPrizeApplier} for the rationale on
 * dropping the legacy first-time-acquisition branch.
 */
public final class GunPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(GunPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final BulletMaxLevel max = ed.getComponent(ship, BulletMaxLevel.class);
    if (max == null) {
      return; // ship not allowed bullets
    }
    final BulletCurrentLevel curr = ed.getComponent(ship, BulletCurrentLevel.class);
    if (curr == null) {
      log.warn(
          "Ship {} has BulletMaxLevel but no BulletCurrentLevel — spawn projection invariant broken; skipping gun prize",
          ship);
      return;
    }
    if (curr.getLevel().level < max.getLevel().level) {
      log.info("Gun level increased to {}", curr.getLevel().next());
      ed.setComponent(ship, new BulletCurrentLevel(curr.getLevel().next()));
    }
  }
}
