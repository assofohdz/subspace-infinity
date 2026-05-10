// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstMax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>COUNT family.</b> Increments {@link Burst} by 1 if under {@link BurstMax}.
 * Allowed iff {@code BurstMax > 0 && Burst < BurstMax}. First-time acquisition
 * (no {@code Burst} component) seeds {@code Burst(1)}.
 *
 * <p>Subspace canon: per-ship {@code [Ship] InitialBurst} / {@code BurstMax}
 * bound the count; the {@code ## Burst} section in REFERENCE.md owns the
 * per-projectile knobs ({@code BurstSpeed}, {@code BurstDamageLevel},
 * {@code BurstShrapnel}, {@code BurstAliveTime}, {@code BurstHits}) — those
 * are consumed at fire time, not on prize pickup. Prize-weight entry:
 * {@code ## PrizeWeight} ({@code Burst}).
 */
public final class BurstPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(BurstPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final Burst burst = ed.getComponent(ship, Burst.class);
    final BurstMax burstMax = ed.getComponent(ship, BurstMax.class);
    if (burstMax == null || burstMax.getCount() <= 0) {
      return; // ship not allowed bursts
    }
    if (burst != null && burst.getCount() < burstMax.getCount()) {
      if (log.isInfoEnabled()) {
        log.info("Ship {} picked up burst prize and now has {} bursts", ship, burst.getCount() + 1);
      }
      ed.setComponent(ship, new Burst(burst.getCount() + 1));
    } else if (burst == null) {
      log.info("Ship {} picked up burst prize", ship);
      ed.setComponent(ship, new Burst(1));
    }
  }
}
