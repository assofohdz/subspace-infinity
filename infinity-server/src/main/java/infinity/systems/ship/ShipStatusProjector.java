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

/**
 * Pattern 4 spawn-projection helpers for the Status-family aspects (Cloak / Stealth / XRadar /
 * AntiWarp) per ADR 0001. Called only from {@code ShipSpawnSystem.project}.
 *
 * <p>For each aspect this projects two ECS components from the {@link StatusStats} template:
 *
 * <ul>
 *   <li>{@code *Stats(statusTier, energyDrainPerSecond)} — always re-project (mirrors
 *       {@code ThrustStats} / {@code EnergyStats}), even when {@code statusTier == 0}, so the
 *       prize applier can read the tier without a null check.
 *   <li>{@code *Active(boolean)} — Continuous half. Only seeded on {@code resetLivePool == true}
 *       (respawn / fresh spawn), {@code true} iff {@code statusTier == 2} (Subspace canon
 *       "start active"). Mid-arena tuning reproject ({@code resetLivePool == false}) preserves
 *       the live toggle so a Groovy edit doesn't clobber the player's state.
 * </ul>
 *
 * <p>The Subspace {@code *Energy} value is in {@code 1000ths-per-centisecond}; converted to
 * energy-per-second at this boundary ({@code raw / 10.0}) so the runtime consumer reads
 * SI-ish units. See REFERENCE.md "Ship abilities".
 */
final class ShipStatusProjector {

  private ShipStatusProjector() {
    // utility class — instantiation prevented
  }

  /** Subspace 1000ths-per-centisecond → energy-per-second. */
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
    if (resetLivePool && cloak.status() >= 1) {
      // statusTier == 0 (forbidden) → no Continuous toggle; tier == 1 → off; tier == 2 → start active.
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
