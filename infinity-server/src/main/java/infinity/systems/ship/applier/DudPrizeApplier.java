// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>INSTANT family.</b> Subspace's "Dud" prize — intentionally does
 * nothing. Implemented as an explicit no-op (rather than a registry miss)
 * so the ship's pickup is logged distinctly from "prize type not yet
 * implemented".
 *
 * <p>Infinity extension: no Subspace canon counterpart in REFERENCE.md.
 * The "Dud" name is Infinity-specific shorthand for an authored-no-op
 * prize slot — used as a placeholder so unimplemented prize entries in a
 * weight table can stamp a logged pickup rather than fall through to the
 * registry-miss path. Distinct from a STUB applier: Dud's no-op is
 * intentional and permanent.
 */
public final class DudPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(DudPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    log.info("Ship {} picked up dud prize (no effect)", ship);
  }
}
