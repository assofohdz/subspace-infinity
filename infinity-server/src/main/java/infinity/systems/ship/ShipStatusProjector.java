// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import infinity.config.StatusStats;
import infinity.es.ship.toggles.AntiwarpActive;
import infinity.es.ship.toggles.AntiwarpStats;
import infinity.es.ship.toggles.CloakActive;
import infinity.es.ship.toggles.CloakStats;
import infinity.es.ship.toggles.StealthActive;
import infinity.es.ship.toggles.StealthStats;
import infinity.es.ship.toggles.XRadarActive;
import infinity.es.ship.toggles.XRadarStats;
import javax.annotation.Nullable;

/** Spawn projection for Status-family aspects (Cloak/Stealth/XRadar/Antiwarp); called from {@link ShipSpawnSystem}. */
final class ShipStatusProjector {

  private ShipStatusProjector() {}

  // Subspace canon: *Energy is 1000ths-per-centisecond → /10 = energy-per-second.
  private static double drainPerSecond(final int energyDrainPer1000Cs) {
    return energyDrainPer1000Cs / 10.0;
  }

  static void projectCloak(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final StatusStats cloak,
      final boolean resetLivePool) {
    if (cloak == null) {
      return;
    }
    ed.setComponent(shipId, new CloakStats(cloak.status(), drainPerSecond(cloak.energyDrainPer1000Cs())));
    // Subspace tier: 0=forbidden (no Continuous toggle), 1=off, 2=start active.
    if (resetLivePool && cloak.status() >= 1) {
      ed.setComponent(shipId, new CloakActive(cloak.status() == 2));
    }
  }

  static void projectStealth(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final StatusStats stealth,
      final boolean resetLivePool) {
    if (stealth == null) {
      return;
    }
    ed.setComponent(shipId, new StealthStats(stealth.status(), drainPerSecond(stealth.energyDrainPer1000Cs())));
    if (resetLivePool && stealth.status() >= 1) {
      ed.setComponent(shipId, new StealthActive(stealth.status() == 2));
    }
  }

  static void projectXRadar(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final StatusStats xradar,
      final boolean resetLivePool) {
    if (xradar == null) {
      return;
    }
    ed.setComponent(shipId, new XRadarStats(xradar.status(), drainPerSecond(xradar.energyDrainPer1000Cs())));
    if (resetLivePool && xradar.status() >= 1) {
      ed.setComponent(shipId, new XRadarActive(xradar.status() == 2));
    }
  }

  static void projectAntiwarp(
      final EntityData ed,
      final EntityId shipId,
      @Nullable final StatusStats antiwarp,
      final boolean resetLivePool) {
    if (antiwarp == null) {
      return;
    }
    ed.setComponent(shipId, new AntiwarpStats(antiwarp.status(), drainPerSecond(antiwarp.energyDrainPer1000Cs())));
    if (resetLivePool && antiwarp.status() >= 1) {
      ed.setComponent(shipId, new AntiwarpActive(antiwarp.status() == 2));
    }
  }
}
