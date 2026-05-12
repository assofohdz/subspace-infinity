// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.weapons.BulletChange;
import infinity.es.ship.weapons.BulletCurrentLevel;
import infinity.es.ship.weapons.BulletStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>LEVEL family.</b> Emits a one-shot {@link BulletChange}({@code +1})
 * Change holder; {@code BulletSystem} drains and clamps at
 * {@link BulletStats#max}. Subspace canon: per-ship {@code [Ship] InitialGuns} /
 * {@code MaxGuns}; REFERENCE.md {@code ## PrizeWeight} ({@code Gun}). Infinity
 * renamed {@code Gun} → {@code Bullet} for clarity post-slice-R1.
 *
 * @see infinity.systems.ship.BulletSystem
 */
public final class GunPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(GunPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final BulletStats stats = ed.getComponent(ship, BulletStats.class);
    if (stats == null || stats.max() == null) {
      return; // ship not allowed bullets
    }
    final BulletCurrentLevel curr = ed.getComponent(ship, BulletCurrentLevel.class);
    if (curr == null || curr.getLevel() == null) {
      log.warn(
          "Ship {} has BulletStats but no BulletCurrentLevel — spawn projection invariant broken; skipping gun prize",
          ship);
      return;
    }
    if (curr.getLevel().ordinal() >= stats.max().ordinal()) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up gun prize", ship);
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new BulletChange(1));
  }
}
