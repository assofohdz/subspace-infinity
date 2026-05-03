// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;

/**
 * <b>STATUS family</b> — STUB (timed debuff variant). Apply a temporary
 * "Engine Shutdown" debuff for {@code EngineShutdownTime} (1/100s).
 * Subspace's "Glue" prize.
 */
public final class GluePrizeApplier implements PrizeApplier {

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    throw new UnsupportedOperationException(
        "Glue prize not yet implemented (STATUS family — timed debuff pending)");
  }
}
