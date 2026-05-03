// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship.applier;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.es.ship.toggles.Multishot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>BULLET-MODE family.</b> Toggles the {@link Multishot} capability on
 * the ship — gun fires thereafter shoot multiple bullets at the per-ship
 * {@code MultiFireAngle} spread, costing {@code MultiFireEnergy} per shot
 * with {@code MultiFireDelay} cooldown.
 *
 * <p>Unlike Cloak / Stealth / XRadar / AntiWarp, MultiFire has no
 * per-ship {@code *Status} tri-state in Subspace canon (REFERENCE.md
 * "Bullets" section). Every ship is eligible; the per-ship
 * {@code MultiFireEnergy} value gates whether multifire is actually
 * useful (energy=0 → effectively dead). Apply unconditionally stamps
 * {@code Multishot(true)} — the firing-mode consumer (WeaponsSystem,
 * deferred) reads the toggle plus the per-ship MultiFire knobs.
 *
 * <p>Slice 6c ships the applier flip only. Per-ship
 * {@code MultiFireEnergy} / {@code MultiFireDelay} / {@code MultiFireAngle}
 * plumbing into the firing path is deferred to a follow-up
 * "MultiFire firing mode" slice that extends WeaponsSystem.
 */
public final class MultiFirePrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(MultiFirePrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    log.info("Ship {} picked up multifire prize; enabling Multishot toggle", ship);
    ed.setComponent(ship, new Multishot(true));
  }
}
