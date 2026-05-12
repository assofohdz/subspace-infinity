// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ChangeTarget;
import infinity.es.ship.actions.ThorChange;
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorFireDelay;
import infinity.es.ship.actions.ThorStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>INVENTORY family.</b> Emits a one-shot {@link ThorChange}({@code +1})
 * Change holder; {@code ThorSystem} drains and clamps at
 * {@link ThorStats#max}. Subspace canon: per-ship {@code [Ship] InitialThor}
 * / {@code ThorMax}; REFERENCE.md {@code ## PrizeWeight} ({@code Thor}).
 *
 * <p>Wave 4b fix — when seeding {@link ThorFireDelay} on first-time
 * acquisition (case: ship spawned without a thor, picks up the prize, no
 * spawn-projected cooldown timer exists), uses {@link ThorStats#fireDelayMillis}
 * sourced from per-ship {@code ShipConfig.thors.fireDelayCs}. Pre-Wave-4b
 * the applier hardcoded {@code new ThorFireDelay(1000)} which clobbered the
 * spawn-projected per-ship value — a divergence from Subspace canon now
 * removed. If {@link ThorStats} is absent (defensive — should not happen
 * given the guard above), the seed is skipped.
 *
 * @see infinity.systems.ship.ThorSystem
 */
public final class ThorPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(ThorPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final ThorStats stats = ed.getComponent(ship, ThorStats.class);
    if (stats == null || stats.max() <= 0) {
      return; // ship not allowed thors
    }
    final ThorCurrentCount curr = ed.getComponent(ship, ThorCurrentCount.class);
    if (curr != null && curr.getCount() >= stats.max()) {
      return; // already at cap
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} picked up thor prize", ship);
    }
    // First-time acquire (no current count, no spawn-projected cooldown
    // timer): seed ThorFireDelay from per-ship Stats so the consume path
    // can immediately re-stamp via stats.fireDelayMillis().
    if (curr == null && ed.getComponent(ship, ThorFireDelay.class) == null) {
      ed.setComponent(ship, new ThorFireDelay(stats.fireDelayMillis()));
    }
    final EntityId holder = ed.createEntity();
    ed.setComponents(holder, ChangeTarget.self(ship), new ThorChange(1));
  }
}
