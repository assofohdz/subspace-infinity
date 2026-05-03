// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.Rotation;
import infinity.es.ship.RotationMax;
import infinity.es.ship.RotationUpgrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>CAPABILITY family.</b> Bumps {@link Rotation} by {@link RotationUpgrade},
 * clamped at {@link RotationMax}. Values are in rad/sec — the Subspace integer
 * rotation units are converted by {@code ShipSpawnSystem} at spawn.
 */
public final class RotationPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(RotationPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final Rotation current = ed.getComponent(ship, Rotation.class);
    final RotationMax max = ed.getComponent(ship, RotationMax.class);
    final RotationUpgrade up = ed.getComponent(ship, RotationUpgrade.class);
    if (current == null || max == null || up == null) {
      return;
    }
    final double next =
        Math.min(current.getRadSec() + up.getRadSecUpgrade(), max.getRadSecMax());
    if (next > current.getRadSec()) {
      log.info("Ship {} rotation upgrade: rad/sec {} -> {}", ship, current.getRadSec(), next);
      ed.setComponent(ship, new Rotation(next));
    }
  }
}
