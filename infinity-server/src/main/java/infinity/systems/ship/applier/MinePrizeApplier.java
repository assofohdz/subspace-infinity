// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.weapons.MineChange;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>LEVEL family.</b> Emits a one-shot {@link MineChange}({@code +1})
 * Change holder; {@code MineSystem} drains and clamps at
 * {@link MineStats#max}. Wired only as a leaf of {@code BOMB} / {@code ALLWEAPONS}
 * composites (Subspace tradition: bomb upgrades bump mine level too).
 *
 * @see infinity.systems.ship.MineSystem
 */
public final class MinePrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(MinePrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final MineStats stats = ed.getComponent(ship, MineStats.class);
    if (stats == null || stats.max() == null) {
      return; // ship not allowed mines
    }
    final MineCurrentLevel curr = ed.getComponent(ship, MineCurrentLevel.class);
    if (curr == null || curr.getLevel() == null) {
      log.warn(
          "Ship {} has MineStats but no MineCurrentLevel — spawn projection invariant broken; skipping mine prize",
          ship);
      return;
    }
    if (curr.getLevel().ordinal() >= stats.max().ordinal()) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up mine prize", ship);
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new MineChange(1));
  }
}
