// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.toggles.Multishot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Stamps {@link Multishot} toggle. Firing-mode consumer (REFERENCE.md per-ship MultiFire knobs) deferred. */
public final class MultiFirePrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(MultiFirePrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    log.info("Ship {} picked up multifire prize; enabling Multishot toggle", ship);
    ed.setComponent(ship, new Multishot(true));
  }
}
