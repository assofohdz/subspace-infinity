// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.weapons.MineCurrentLevel;
import infinity.es.ship.weapons.MineMaxLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>LEVEL family.</b> Bumps {@link MineCurrentLevel} toward
 * {@link MineMaxLevel}. Note: there's no standalone {@code Mine} prize type
 * in Subspace; this applier is wired only as a leaf of the {@code BOMB} and
 * {@code ALLWEAPONS} composites (a bomb prize bumps both bomb level <em>and</em>
 * mine level — Subspace tradition).
 *
 * <p>Pure component read; see {@link BombPrizeApplier} for the rationale on
 * dropping the legacy first-time-acquisition branch.
 */
public final class MinePrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(MinePrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final MineMaxLevel max = ed.getComponent(ship, MineMaxLevel.class);
    if (max == null) {
      return; // ship not allowed mines
    }
    final MineCurrentLevel curr = ed.getComponent(ship, MineCurrentLevel.class);
    if (curr == null) {
      log.warn(
          "Ship {} has MineMaxLevel but no MineCurrentLevel — spawn projection invariant broken; skipping mine prize",
          ship);
      return;
    }
    if (curr.getLevel().level < max.getLevel().level) {
      if (log.isInfoEnabled()) {
        log.info(
            "Ship {} picked up mine prize and now has {} mines", ship, curr.getLevel().next());
      }
      ed.setComponent(ship, new MineCurrentLevel(curr.getLevel().next()));
    }
  }
}
