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
import infinity.es.ship.actions.Portal;
import infinity.es.ship.actions.PortalMax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>COUNT family.</b> Bumps {@link Portal} inventory by one toward
 * {@link PortalMax}. No-op when the ship lacks {@link PortalMax} (=
 * disallowed) or is at the cap. {@code WarpPointDelay} (Subspace
 * canonical {@code [Misc]} key for portal active duration) is consumed
 * by {@code ConsumableSystem} when the portal is dropped, not here.
 */
public final class PortalPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(PortalPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final PortalMax max = ed.getComponent(ship, PortalMax.class);
    if (max == null) {
      return;
    }
    final Portal curr = ed.getComponent(ship, Portal.class);
    if (curr == null) {
      log.warn(
          "Ship {} has PortalMax but no Portal — spawn projection invariant broken; skipping portal prize",
          ship);
      return;
    }
    if (curr.getCount() < max.getCount()) {
      final int next = curr.getCount() + 1;
      log.info("Ship {} picked up portal prize and now has {} portals", ship, next);
      ed.setComponent(ship, new Portal(next));
    }
  }
}
