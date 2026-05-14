// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/** Shape contract for stats records that carry a per-second energy drain rate consumed by {@code BaseEnergyDrainSystem} subclasses. Today implemented by the status-family stats (CloakStats/StealthStats/XRadarStats/AntiwarpStats); future drain consumers (e.g. afterburner) plug in by adding another implementor + a sibling subclass. */
public interface EnergyDrain extends EntityComponent {

  double energyDrainPerSecond();
}
