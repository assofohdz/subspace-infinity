// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.weapons.BombChange;
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>LEVEL family.</b> Emits a one-shot {@link BombChange}({@code +1})
 * Change holder; {@code BombSystem} drains and clamps at
 * {@link BombStats#max}. No-op when ship not equipped for bombs
 * or already at cap. Subspace canon: per-ship {@code [Ship] InitialBombs} /
 * {@code MaxBombs}; REFERENCE.md {@code ## PrizeWeight} ({@code Bomb}).
 *
 * @see infinity.systems.ship.BombSystem
 */
public final class BombPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(BombPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final BombStats stats = ed.getComponent(ship, BombStats.class);
    if (stats == null || stats.max() == null) {
      return; // ship not allowed bombs
    }
    final BombCurrentLevel curr = ed.getComponent(ship, BombCurrentLevel.class);
    if (curr == null || curr.getLevel() == null) {
      log.warn(
          "Ship {} has BombStats but no BombCurrentLevel — spawn projection invariant broken; skipping bomb prize",
          ship);
      return;
    }
    if (curr.getLevel().ordinal() >= stats.max().ordinal()) {
      return; // already at cap
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up bomb prize", ship);
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new BombChange(1));
  }
}
