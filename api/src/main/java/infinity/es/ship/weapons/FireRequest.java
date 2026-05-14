// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;
import com.simsilica.mathd.Vec3d;

/** Post-eligibility fire intent; pairs with {@link infinity.es.ChangeTarget} (target = attacker). Drained by {@code WeaponsProjectileSpawnSystem} + {@code WeaponsFireAudioSystem}. See ADR 0001. */
public record FireRequest(byte weaponType, Vec3d location, Vec3d velocity) implements EntityComponent {

  public FireRequest() {
    this((byte) 0, null, null);
  }
}
