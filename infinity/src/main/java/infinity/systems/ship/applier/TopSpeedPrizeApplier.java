// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.Speed;
import infinity.es.ship.SpeedMax;
import infinity.es.ship.SpeedUpgrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>CAPABILITY family.</b> Bumps {@link Speed} by {@link SpeedUpgrade},
 * clamped at {@link SpeedMax}.
 *
 * <p>Subspace canon: per-ship {@code [Ship] InitialSpeed} / {@code MaximumSpeed}
 * (REFERENCE.md line 354) plus {@code UpgradeSpeed} per-pickup increment;
 * see REFERENCE.md {@code ## PrizeWeight} line 240 ({@code TopSpeed}) for
 * the prize-name registration.
 *
 * <p>Note: Infinity components ({@link Speed} / {@link SpeedMax} /
 * {@link SpeedUpgrade}) are named after the prize ("TopSpeed") rather
 * than the canonical knob names ({@code InitialSpeed} / {@code MaximumSpeed} /
 * {@code UpgradeSpeed}). Same value pipeline; cosmetic naming divergence.
 */
public final class TopSpeedPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(TopSpeedPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final Speed current = ed.getComponent(ship, Speed.class);
    final SpeedMax max = ed.getComponent(ship, SpeedMax.class);
    final SpeedUpgrade up = ed.getComponent(ship, SpeedUpgrade.class);
    if (current == null || max == null || up == null) {
      return;
    }
    final int next = Math.min(current.getSpeed() + up.getSpeedUpgrade(), max.getSpeedMax());
    if (next > current.getSpeed()) {
      if (log.isInfoEnabled()) {
        log.info("Ship {} topspeed upgrade: speed {} -> {}", ship, current.getSpeed(), next);
      }
      ed.setComponent(ship, new Speed(next));
    }
  }
}
