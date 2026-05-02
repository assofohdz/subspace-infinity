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
import infinity.es.ship.actions.ThorCurrentCount;
import infinity.es.ship.actions.ThorFireDelay;
import infinity.es.ship.actions.ThorMaxCount;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>COUNT family.</b> Increments {@link ThorCurrentCount} by 1 if under
 * {@link ThorMaxCount}. Allowed iff {@code ThorMaxCount > 0 && current < max}.
 * First-time acquisition seeds count=1 and a fallback {@code ThorFireDelay(1000)}
 * — note the hardcoded delay is a pre-existing carryover from
 * {@code handleAcquireThor}; properly it should come from {@code ShipConfig.thors.fireDelayCs}
 * (which {@code ShipSpawnSystem} already projects), but the original code
 * overwrites it on first acquisition. Preserved bit-for-bit.
 */
public final class ThorPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(ThorPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final ThorCurrentCount thorCurrentCount = ed.getComponent(ship, ThorCurrentCount.class);
    final ThorMaxCount thorMaxCount = ed.getComponent(ship, ThorMaxCount.class);
    if (thorMaxCount == null || thorMaxCount.getCount() <= 0) {
      return; // ship not allowed thors
    }
    if (thorCurrentCount != null && thorCurrentCount.getCount() < thorMaxCount.getCount()) {
      final ThorCurrentCount thorNextCount = thorCurrentCount.add(1);
      log.info(
          "Ship {} picked up thor prize and now has {} thor", ship, thorNextCount.getCount());
      ed.setComponent(ship, thorNextCount);
    } else if (thorCurrentCount == null) {
      log.info("Ship {} picked up thor prize", ship);
      ed.setComponent(ship, new ThorCurrentCount(1));
      ed.setComponent(ship, new ThorFireDelay(1000));
    }
  }
}
