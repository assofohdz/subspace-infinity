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
import infinity.es.ship.weapons.GunCurrentLevel;
import infinity.es.ship.weapons.GunMaxLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>LEVEL family.</b> Bumps {@link GunCurrentLevel} toward {@link GunMaxLevel}.
 * Pure component read; see {@link BombPrizeApplier} for the rationale on
 * dropping the legacy first-time-acquisition branch.
 */
public final class GunPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(GunPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final GunMaxLevel max = ed.getComponent(ship, GunMaxLevel.class);
    if (max == null) {
      return; // ship not allowed guns
    }
    final GunCurrentLevel curr = ed.getComponent(ship, GunCurrentLevel.class);
    if (curr == null) {
      log.warn(
          "Ship {} has GunMaxLevel but no GunCurrentLevel — spawn projection invariant broken; skipping gun prize",
          ship);
      return;
    }
    if (curr.getLevel().level < max.getLevel().level) {
      log.info("Gun level increased to {}", curr.getLevel().next());
      ed.setComponent(ship, new GunCurrentLevel(curr.getLevel().next()));
    }
  }
}
