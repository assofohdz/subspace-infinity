// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.RotationUpgrade;
import infinity.es.ship.actions.Intent;
import infinity.es.ship.actions.RotationCapBump;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>CAPABILITY family.</b> Emits an {@link Intent}-wrapped
 * {@link RotationCapBump} payload carrying the ship's per-prize
 * {@link RotationUpgrade} delta (rad/sec — already converted from
 * Subspace integer rotation units at spawn projection); the canonical
 * writer ({@code ShipSpawnSystem}) drains the intent to fold the delta
 * into {@code Rotation} (clamped at {@code RotationMax}).
 *
 * <p><b>Replacement-as-Mutation</b> — this applier no longer writes
 * {@code Rotation} directly. Same-tick multi-prize pickup accumulates
 * additively per {@link RotationCapBump} class Javadoc. Closes the
 * {@code ShipSpawnSystem} / {@code RotationPrizeApplier} multi-writer
 * violation on the {@code Rotation} component (BACKLOG C2a ship-body).
 *
 * <p>Subspace canon: per-ship {@code [Ship] InitialRotation} /
 * {@code MaximumRotation} (raw integer rotation units; see REFERENCE.md
 * per-ship section) seed the cap. Per-prize bump amount is
 * {@code [Ship] UpgradeRotation}. Prize-weight entry: REFERENCE.md
 * {@code ## PrizeWeight} line 238 ({@code Rotation}).
 */
public final class RotationPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(RotationPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final RotationUpgrade up = ed.getComponent(ship, RotationUpgrade.class);
    if (up == null) {
      return;
    }
    final double delta = up.getRadSecUpgrade();
    if (Double.compare(delta, 0.0) == 0) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} rotation upgrade: emitting cap-bump intent delta={}", ship, delta);
    }
    final EntityId intentId = ed.createEntity();
    ed.setComponent(intentId, Intent.of(new RotationCapBump(ship, delta)));
  }
}
