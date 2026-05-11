// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.SpeedUpgrade;
import infinity.es.ship.actions.Intent;
import infinity.es.ship.actions.SpeedCapBump;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>CAPABILITY family.</b> Emits an {@link Intent}-wrapped
 * {@link SpeedCapBump} payload carrying the ship's per-prize
 * {@link SpeedUpgrade} delta; the canonical writer
 * ({@code ShipSpawnSystem}) drains the intent to fold the delta into
 * {@code Speed} (clamped at {@code SpeedMax}).
 *
 * <p><b>Replacement-as-Mutation</b> — this applier no longer writes
 * {@code Speed} directly. Same-tick multi-prize pickup accumulates
 * additively per {@link SpeedCapBump} class Javadoc. Closes the
 * {@code ShipSpawnSystem} (spawn + rocket-buff drain) /
 * {@code TopSpeedPrizeApplier} multi-writer violation on the
 * {@code Speed} component (BACKLOG C2a ship-body).
 *
 * <p><b>Rocket-buff interaction (preserved limitation).</b> Same shape
 * as {@link ThrusterPrizeApplier} — a topspeed prize picked up during
 * an active rocket buff is lost when the buff reverts. See
 * {@link SpeedCapBump} class Javadoc.
 *
 * <p>Subspace canon: per-ship {@code [Ship] InitialSpeed} /
 * {@code MaximumSpeed} (REFERENCE.md line 354) plus {@code UpgradeSpeed}
 * per-pickup increment; see REFERENCE.md {@code ## PrizeWeight} line 240
 * ({@code TopSpeed}) for the prize-name registration.
 *
 * <p>Note: Infinity components ({@link infinity.es.ship.Speed} /
 * {@link infinity.es.ship.SpeedMax} / {@link SpeedUpgrade}) are named
 * after the prize ("TopSpeed") rather than the canonical knob names
 * ({@code InitialSpeed} / {@code MaximumSpeed} / {@code UpgradeSpeed}).
 * Same value pipeline; cosmetic naming divergence.
 */
public final class TopSpeedPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(TopSpeedPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final SpeedUpgrade up = ed.getComponent(ship, SpeedUpgrade.class);
    if (up == null) {
      return;
    }
    final int delta = up.getSpeedUpgrade();
    if (delta == 0) {
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Ship {} topspeed upgrade: emitting cap-bump intent delta={}", ship, delta);
    }
    final EntityId intentId = ed.createEntity();
    ed.setComponent(intentId, Intent.of(new SpeedCapBump(ship, delta)));
  }
}
