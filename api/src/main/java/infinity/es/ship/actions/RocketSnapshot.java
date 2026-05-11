// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.actions;

import com.simsilica.es.EntityComponent;

/**
 * Pre-buff snapshot of the ship's {@code Thrust} and {@code Speed}.
 * Lives on the rocket-buff entity (not the ship) so revert is atomic
 * with buff-entity removal — when the {@link com.simsilica.es.common.Decay}
 * reaper deletes the buff entity, {@code RocketBuffSystem.onRemoved}
 * reads this snapshot off the (still-readable last view of the)
 * removed entity and restores the ship.
 *
 * <p>Pairs with {@link RocketBuff} (marker) and
 * {@link com.simsilica.es.common.Parent} (ship link) on the same buff
 * entity.
 *
 * <p><b>Known limitation — thruster/topspeed prize during an active
 * buff is lost on revert.</b> The snapshot is captured at activate
 * time and frozen for the buff's lifetime. If the player picks up a
 * {@code Thruster} or {@code TopSpeed} prize while the buff is
 * active, the prize emits an {@link Intent}-wrapped {@link CapBump}
 * tagged {@link CapField#THRUST} / {@link CapField#SPEED} that
 * {@code ShipSpawnSystem} drains after the rocket-buff drain — so the
 * bump lands on the *buffed* {@code Thrust} / {@code Speed} value,
 * NOT the pre-buff snapshot stored here. When the buff expires, the
 * revert intent restores the snapshot and the prize bump silently
 * disappears.
 *
 * <p>Pre-existing behaviour from before the C2a prize-applier RaM
 * migration (the direct-write appliers had the same race). Preserved
 * deliberately — fixing it requires reading
 * {@code ActiveBuff} state from the cap-bump drain and updating
 * either the snapshot or the post-revert target value, which is a
 * separate design conversation. Documented here so the next
 * encounter doesn't re-investigate as a fresh bug.
 *
 * @author Asser Fahrenholz
 */
public class RocketSnapshot implements EntityComponent {

  private final int originalThrust;
  private final int originalSpeed;

  public RocketSnapshot() {
    this(0, 0);
  }

  public RocketSnapshot(final int originalThrust, final int originalSpeed) {
    this.originalThrust = originalThrust;
    this.originalSpeed = originalSpeed;
  }

  public int getOriginalThrust() {
    return originalThrust;
  }

  public int getOriginalSpeed() {
    return originalSpeed;
  }

  @Override
  public String toString() {
    return "RocketSnapshot[thrust=" + originalThrust + ", speed=" + originalSpeed + "]";
  }
}
