/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.actions.Repel;
import infinity.es.ship.actions.RepelMax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>COUNT family.</b> Bumps the ship's {@link Repel} count by one toward
 * {@link RepelMax}. No-op when the ship has no {@link RepelMax} component
 * (= ship not allowed repels) or is already at the cap.
 *
 * <p>Pure component read/write — no {@link infinity.config.ShipConfig}
 * access on the hot path. Applier only adjusts inventory; {@code RepelTime},
 * {@code RepelDistance}, {@code RepelSpeed} (Subspace canonical
 * {@code [Repel]} keys) describe what happens when the repel is fired and
 * belong to {@code ConsumableSystem}, not here.
 */
public final class RepelPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(RepelPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final RepelMax max = ed.getComponent(ship, RepelMax.class);
    if (max == null) {
      return; // ship not allowed repels
    }
    final Repel curr = ed.getComponent(ship, Repel.class);
    if (curr == null) {
      log.warn(
          "Ship {} has RepelMax but no Repel — spawn projection invariant broken; skipping repel prize",
          ship);
      return;
    }
    if (curr.getCount() < max.getCount()) {
      final int next = curr.getCount() + 1;
      log.info("Ship {} picked up repel prize and now has {} repels", ship, next);
      ed.setComponent(ship, new Repel(next));
    }
  }
}
