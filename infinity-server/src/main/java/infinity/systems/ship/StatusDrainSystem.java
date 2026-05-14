// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems.ship;

import infinity.es.ship.toggles.AntiwarpActive;
import infinity.es.ship.toggles.AntiwarpStats;
import infinity.es.ship.toggles.CloakActive;
import infinity.es.ship.toggles.CloakStats;
import infinity.es.ship.toggles.StealthActive;
import infinity.es.ship.toggles.StealthStats;
import infinity.es.ship.toggles.XRadarActive;
import infinity.es.ship.toggles.XRadarStats;

/** Per-tick Energy drain for ships with active Status-family toggles (Cloak/Stealth/XRadar/Antiwarp). All drain logic lives in {@link BaseEnergyDrainSystem}; this subclass just registers the 4 status bindings. */
public class StatusDrainSystem extends BaseEnergyDrainSystem {

  @Override
  protected void registerDrainEntries() {
    addDrainEntry(CloakActive.class, CloakStats.class);
    addDrainEntry(StealthActive.class, StealthStats.class);
    addDrainEntry(XRadarActive.class, XRadarStats.class);
    addDrainEntry(AntiwarpActive.class, AntiwarpStats.class);
  }
}
