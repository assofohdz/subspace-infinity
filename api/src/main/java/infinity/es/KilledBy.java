// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;
import infinity.es.ship.weapons.WeaponType;

/**
 * Death-edge attribution sibling on the dying entity — carries the killer
 * EntityId + weapon-family flag forward to {@code DeathSystem}, which
 * publishes the {@code PlayerKilledEvent} and emits the {@code PrizeSpawnIntent}
 * one tick after {@code EnergySystem} stamps {@link Dead}.
 *
 * <p>Absence of this component on a {@code Dead} entity means "unattributed
 * death" — environmental / regen / unknown. Consumers fall back to self-credit
 * (per the kill-credit ledger).
 *
 * <p>Per ADR-0001: keeps the death cycle's writer split clean. {@code EnergySystem}
 * is the canonical writer of {@code Energy} + {@code Dead}; {@code DeathSystem}
 * is the canonical writer of {@code Decay} + the side-effect channels.
 */
public record KilledBy(EntityId killer, byte weaponFlag) implements EntityComponent {

  public KilledBy() {
    this(null, WeaponType.NONE);
  }
}
