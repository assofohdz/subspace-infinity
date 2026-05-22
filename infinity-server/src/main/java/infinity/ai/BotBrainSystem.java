// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.SimTime;
import infinity.es.input.MovementInput;
import infinity.es.ship.BotShip;
import infinity.systems.BaseInfinitySystem;
import java.util.concurrent.TimeUnit;

/**
 * Canonical writer of {@link MovementInput} on {@link BotShip} entities. v1 stub: writes
 * a continuously-rotating facing each tick to prove input-parity wiring; steering + BT
 * logic lands in later slices. See ADR-0009.
 */
public final class BotBrainSystem extends BaseInfinitySystem {

  // 1 full rotation per ~5 seconds — visible at human-perception rates.
  private static final double YAW_RATE_RAD_PER_SEC = Math.PI * 2.0 / 5.0;

  private EntityData ed;
  private EntitySet bots;

  @Override
  protected void initialize() {
    this.ed = requireSystem(EntityData.class);
    this.bots = ed.getEntities(BotShip.class);
  }

  @Override
  protected void terminate() {
    if (this.bots != null) {
      this.bots.release();
      this.bots = null;
    }
  }

  @Override
  public void update(final SimTime time) {
    if (this.bots == null) {
      return;
    }
    this.bots.applyChanges();
    if (this.bots.isEmpty()) {
      return;
    }
    final double seconds = time.getTime() / (double) TimeUnit.SECONDS.toNanos(1L);
    final double yaw = seconds * YAW_RATE_RAD_PER_SEC;
    for (final Entity entity : this.bots) {
      final Quatd facing = new Quatd().fromAngles(0.0, yaw, 0.0);
      this.ed.setComponent(
          entity.getId(), new MovementInput(new Vec3d(), facing, MovementInput.NONE));
    }
  }
}
