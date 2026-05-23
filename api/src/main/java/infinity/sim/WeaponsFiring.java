// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

import com.simsilica.es.EntityId;

/**
 * Server-side weapon-fire entry point. Bots + chat commands + RMI session.attack all call
 * {@link #requestFire} with the same shape; the implementation queues the request and
 * runs the canonical eligibility gate (cooldowns, energy, ammunition) next tick — no AI
 * bypass. See ADR-0009.
 */
public interface WeaponsFiring {

  /**
   * Queue a fire request for the given attacker. {@code weaponType} is a value from
   * {@link infinity.es.ship.weapons.WeaponType}.
   */
  void requestFire(EntityId attacker, byte weaponType);
}
