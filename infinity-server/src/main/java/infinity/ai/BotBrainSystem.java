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

/**
 * Canonical writer of {@link MovementInput} on {@link BotShip} entities. v1 stub: emits a
 * constant left-turn rate (intent.x = 1.0, intent.z = 0) to prove input-parity wiring —
 * same rate-shaped intent a keyboard player produces via F_TURN. Actual turn speed comes
 * from the ship's {@code RotationStats.max} via {@code PlayerDriver}; this system never
 * picks physical rates. Steering + BT logic lands in later slices. See ADR-0009.
 */
public final class BotBrainSystem extends BaseInfinitySystem {

  // Rate-shaped intent: x = turn rate in [-1, 1], z = thrust in [-1, 1]. PlayerDriver
  // scales these by the ship's RotationStats.max / ThrustStats.max — same path as the
  // keyboard client's analog F_TURN / F_THRUST values.
  private static final Vec3d CONSTANT_LEFT_TURN = new Vec3d(1.0, 0.0, 0.0);

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
    for (final Entity entity : this.bots) {
      this.ed.setComponent(
          entity.getId(),
          new MovementInput(CONSTANT_LEFT_TURN.clone(), new Quatd(), MovementInput.NONE));
    }
  }
}
