// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship;

import com.simsilica.es.EntityComponent;

/**
 * Marker requesting that {@code ShipSpawnSystem} project the ship's
 * config with {@code resetLivePool=true} on the next tick — i.e., reset
 * Health / Energy and re-stamp the {@code *CurrentLevel} starts (bomb
 * level, bullet level, mine level, thor count) from {@code ShipConfig}.
 *
 * <p>Rationale: Zay-ES coalesces a same-tick {@code removeComponent} +
 * {@code setComponent} on a tracked field into a single "changed" event,
 * not an "added/removed" pair. So the ship-swap path in
 * {@code AvatarSystem.requestShipChange}, which removes-and-re-adds
 * {@code ShipType} to force a re-projection, surfaces through
 * {@code ships.getChangedEntities()} (= {@code resetLivePool=false}, the
 * "tuning" branch) rather than {@code getAddedEntities()} (=
 * {@code resetLivePool=true}, the "respawn" branch). Without this marker,
 * a ship-swap to a bomb / mine / thor-equipped ship from a non-equipped
 * ship leaves the {@code *CurrentLevel} component never written, and the
 * weapon-EntitySet membership filter excludes the ship → no fire.
 *
 * <p>Set by {@code AvatarSystem.requestShipChange} alongside the
 * ShipType swap. Read + cleared by {@code ShipSpawnSystem.update} after
 * one projection tick.
 *
 * <p>Server-only — no serializer registration. Pure marker (no fields).
 */
public class ResetLivePool implements EntityComponent {

  public ResetLivePool() {
    // marker
  }
}
