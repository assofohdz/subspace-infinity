// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;
import infinity.es.ship.weapons.WeaponType;

/**
 * Optional metadata sibling on an {@code EnergyChange} holder — carries originator + weapon-family flag. See ADR 0001.
 *
 * <p>{@code source} = attacker for enemy hits, firer for self-cost-deduction, {@link EntityId#NULL_ID} for unattributed
 * (regen / env). Absence of the component on the holder means "unattributed" — drain still folds, reactors that fork
 * on weapon family no-op.
 */
public class DamageSource implements EntityComponent {

  private final EntityId source;
  private final byte weaponFlag;

  public DamageSource() {
    this(null, WeaponType.NONE);
  }

  public DamageSource(final EntityId source, final byte weaponFlag) {
    this.source = source;
    this.weaponFlag = weaponFlag;
  }

  public EntityId getSource() {
    return source;
  }

  public byte getWeaponFlag() {
    return weaponFlag;
  }

  @Override
  public String toString() {
    return "DamageSource[source=" + source + ", weapon=" + weaponFlag + "]";
  }
}
