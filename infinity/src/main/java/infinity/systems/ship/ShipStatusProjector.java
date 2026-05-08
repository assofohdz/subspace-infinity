// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.config.StatusStats;
import infinity.es.ship.toggles.Antiwarp;
import infinity.es.ship.toggles.AntiwarpEnergy;
import infinity.es.ship.toggles.AntiwarpStatus;
import infinity.es.ship.toggles.Cloak;
import infinity.es.ship.toggles.CloakEnergy;
import infinity.es.ship.toggles.CloakStatus;
import infinity.es.ship.toggles.Stealth;
import infinity.es.ship.toggles.StealthEnergy;
import infinity.es.ship.toggles.StealthStatus;
import infinity.es.ship.toggles.XRadar;
import infinity.es.ship.toggles.XRadarEnergy;
import infinity.es.ship.toggles.XRadarStatus;
import javax.annotation.Nullable;

/**
 * Pattern-4 spawn-projection helpers for the Status-family capabilities
 * (Cloak / Stealth / XRadar / AntiWarp). Extracted from {@link ShipSpawnSystem}
 * as pure static methods; same boundary discipline as
 * {@link ShipWeaponsProjector} (called only from
 * {@code ShipSpawnSystem.project}).
 *
 * <p>Each method follows the Subspace canonical Status-family shape:
 *
 * <ul>
 *   <li>The {@code *Status} component (tri-state: 0 forbidden, 1 acquirable,
 *       2 starts active) <b>always</b> projects, so prize appliers and
 *       {@code StatusDrainSystem} can read it without a null check.
 *   <li>The {@code *Energy} drain component projects only when the
 *       capability is at least acquirable ({@code status >= 1}).
 *   <li>The toggle component (e.g. {@link Cloak}) is seeded only on
 *       {@code resetLivePool == true} (respawn), as {@code true} when
 *       {@code status == 2} and {@code false} when {@code status == 1}.
 *       {@code resetLivePool == false} preserves the live toggle state
 *       across ship swaps / mid-arena reloads.
 * </ul>
 *
 * <p>Package-private; not part of any public API. Methods take
 * {@link EntityData} as their first argument because that's the only
 * piece of {@code ShipSpawnSystem} state they read.
 */
final class ShipStatusProjector {

  private ShipStatusProjector() {
    // utility class — instantiation prevented
  }

  /**
   * Pattern 4 spawn projection for the Cloak Status-family capability.
   * See class Javadoc for the tri-state semantics.
   */
  static void projectCloak(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final StatusStats cloak,
      final boolean resetLivePool) {
    if (cloak == null) {
      return;
    }
    ed.setComponent(shipId, new CloakStatus(cloak.status()));
    if (cloak.status() >= 1) {
      ed.setComponent(shipId, new CloakEnergy(cloak.energyDrainPer1000Cs()));
    }
    if (resetLivePool && cloak.status() >= 1) {
      // status == 0 (forbidden) → no toggle component; status == 1 → off;
      // status == 2 → start active (Subspace canon).
      ed.setComponent(shipId, new Cloak(cloak.status() == 2));
    }
  }

  /** Same shape as {@link #projectCloak} for Stealth. */
  static void projectStealth(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final StatusStats stealth,
      final boolean resetLivePool) {
    if (stealth == null) {
      return;
    }
    ed.setComponent(shipId, new StealthStatus(stealth.status()));
    if (stealth.status() >= 1) {
      ed.setComponent(shipId, new StealthEnergy(stealth.energyDrainPer1000Cs()));
    }
    if (resetLivePool && stealth.status() >= 1) {
      ed.setComponent(shipId, new Stealth(stealth.status() == 2));
    }
  }

  /** Same shape as {@link #projectCloak} for XRadar. */
  static void projectXRadar(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final StatusStats xradar,
      final boolean resetLivePool) {
    if (xradar == null) {
      return;
    }
    ed.setComponent(shipId, new XRadarStatus(xradar.status()));
    if (xradar.status() >= 1) {
      ed.setComponent(shipId, new XRadarEnergy(xradar.energyDrainPer1000Cs()));
    }
    if (resetLivePool && xradar.status() >= 1) {
      ed.setComponent(shipId, new XRadar(xradar.status() == 2));
    }
  }

  /** Same shape as {@link #projectCloak} for AntiWarp. */
  static void projectAntiwarp(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final StatusStats antiwarp,
      final boolean resetLivePool) {
    if (antiwarp == null) {
      return;
    }
    ed.setComponent(shipId, new AntiwarpStatus(antiwarp.status()));
    if (antiwarp.status() >= 1) {
      ed.setComponent(shipId, new AntiwarpEnergy(antiwarp.energyDrainPer1000Cs()));
    }
    if (resetLivePool && antiwarp.status() >= 1) {
      ed.setComponent(shipId, new Antiwarp(antiwarp.status() == 2));
    }
  }
}
