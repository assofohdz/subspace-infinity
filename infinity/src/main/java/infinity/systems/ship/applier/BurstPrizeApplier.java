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
import infinity.es.ship.actions.Burst;
import infinity.es.ship.actions.BurstMax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>COUNT family.</b> Increments {@link Burst} by 1 if under {@link BurstMax}.
 * Allowed iff {@code BurstMax > 0 && Burst < BurstMax}. First-time acquisition
 * (no {@code Burst} component) seeds {@code Burst(1)}.
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
      log.info("Ship {} picked up burst prize and now has {} bursts", ship, burst.getCount() + 1);
      ed.setComponent(ship, new Burst(burst.getCount() + 1));
    } else if (burst == null) {
      log.info("Ship {} picked up burst prize", ship);
      ed.setComponent(ship, new Burst(1));
    }
  }
}
