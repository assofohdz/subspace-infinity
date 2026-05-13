// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/**
 * Per-ship snapshot of arena {@code BombConfig} fields needed for the
 * bomb-safety eligibility check (per-shot fire-time read). Carries the
 * arena's {@code BombSafety} toggle + L1 proximity-arm radius in tiles;
 * the effective radius after per-ship bomb-level scaling is computed at
 * fire time via {@code WeaponsLogic.proximityRadiusForLevel}.
 *
 * <p>Projected at ship spawn by {@code ShipWeaponsProjector.projectBombSafety}
 * from {@code BombConfig.bombSafety()} + {@code BombConfig.proximityDistance()}.
 * Per ADR-0002 Config-Component Projection; closes the hot-path
 * {@code infinity.config.BombConfig} import in {@code WeaponsEligibility}.
 *
 * <p>TODO: bomb.groovy edits do not re-project this onto existing ships
 * today (consistent with existing fragment hot-reload story per
 * ADR-0004); selective per-component reproject is future work.
 */
public record BombSafetyRadius(boolean enabled, int baseTiles) implements EntityComponent {

  public BombSafetyRadius() {
    this(false, 0);
  }
}
