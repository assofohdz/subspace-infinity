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
import infinity.es.ship.weapons.BombCurrentLevel;
import infinity.es.ship.weapons.BombMaxLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <b>LEVEL family.</b> Bumps {@link BombCurrentLevel} one step toward
 * {@link BombMaxLevel} via {@code level.next()}. No-op when the ship is
 * already at the cap or has no {@code BombMaxLevel} component (= ship not
 * allowed to carry bombs).
 *
 * <p>Pure component read — no {@link infinity.config.ShipConfig} access on
 * the hot path. The earlier handler had a "first-time acquisition" branch
 * that reached into the template when {@code BombMaxLevel} was projected
 * but {@code BombCurrentLevel} wasn't; that combination is unreachable in
 * the current spawn flow (both project together on respawn) so the branch
 * is gone. If it ever surfaces, log a warning and skip rather than
 * silently re-leak the template lookup.
 */
public final class BombPrizeApplier implements PrizeApplier {

  private static final Logger log = LoggerFactory.getLogger(BombPrizeApplier.class);

  @Override
  public void apply(final EntityId ship, final PrizeApplierContext ctx) {
    final EntityData ed = ctx.ed();
    final BombMaxLevel max = ed.getComponent(ship, BombMaxLevel.class);
    if (max == null) {
      return; // ship not allowed bombs
    }
    final BombCurrentLevel curr = ed.getComponent(ship, BombCurrentLevel.class);
    if (curr == null) {
      log.warn(
          "Ship {} has BombMaxLevel but no BombCurrentLevel — spawn projection invariant broken; skipping bomb prize",
          ship);
      return;
    }
    if (curr.getLevel().level < max.getLevel().level) {
      log.info(
          "Ship {} picked up bomb prize and now has {} bombs", ship, curr.getLevel().next());
      ed.setComponent(ship, new BombCurrentLevel(curr.getLevel().next()));
    }
  }
}
