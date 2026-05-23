// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import infinity.ai.bt.Action;
import infinity.ai.bt.Status;

/**
 * Queues a fire request via {@link infinity.sim.WeaponsFiring} — same gate humans go
 * through. Returns {@code SUCCESS} on dispatch (the eligibility system decides whether
 * the request becomes a real shot — bots respect cooldowns + energy + ammo per ADR-0009).
 * {@code FAILURE} if no firing service is wired (defensive; happens in api-only tests).
 */
public final class FireWeapon implements Action {

  private final byte weaponType;

  public FireWeapon(final byte weaponType) {
    this.weaponType = weaponType;
  }

  @Override
  public Status tick(final Blackboard blackboard) {
    if (blackboard.firing() == null || blackboard.selfId() == null) {
      return Status.FAILURE;
    }
    blackboard.firing().requestFire(blackboard.selfId(), this.weaponType);
    return Status.SUCCESS;
  }
}
