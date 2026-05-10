// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;
import infinity.es.ship.weapons.WeaponType;

/**
 * Optional metadata sibling on a {@code HealthChange + Buff} intent entity:
 * identifies which entity originated the health-change request and which
 * weapon family triggered it.
 *
 * <p>Replacement-as-Mutation slice 1 (.scratch/replacement-as-mutation/PRD.md):
 * the existing {@code HealthChange + Buff} intent shape (drained by
 * {@link infinity.systems.ship.EnergySystem EnergySystem}) gains an optional
 * {@code DamageSource} sibling so reactors that fork on intent type — e.g.
 * "play a hit-sound only on damage, not regen" — have a signal to read.
 *
 * <p>Conventions:
 * <ul>
 *   <li>{@code source} is the originating entity. For enemy hits this is the
 *       attacker ship; for self-cost-deduction (firing a weapon debits the
 *       firer's Health) this is the firing ship; for non-weapon paths this is
 *       {@link EntityId#NULL_ID}.
 *   <li>{@code weaponFlag} is one of the {@link WeaponType} byte constants
 *       (BULLET / BOMB / GRAVBOMB / MINE / BURST). Non-weapon paths use
 *       {@link WeaponType#NONE}.
 *   <li>{@code DamageSource} is <strong>optional</strong> on the intent entity:
 *       absence means "unattributed" — typically regen / refill. The
 *       canonical Health writer ({@code EnergySystem}) folds intents
 *       regardless of {@code DamageSource} presence.
 * </ul>
 *
 * <p>Server-only — emitted and consumed inside the same tick on a short-lived
 * intent entity that never crosses the wire to clients. No serializer
 * registration in {@code GameServer.registerSerializers}.
 *
 * @author AFahrenholz
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

  /**
   * The originating entity for this health-change intent. {@link EntityId#NULL_ID}
   * for unattributed / environmental / regen paths.
   */
  public EntityId getSource() {
    return source;
  }

  /**
   * The weapon-family flag, one of the {@link WeaponType} byte constants.
   * {@link WeaponType#NONE} for non-weapon paths.
   */
  public byte getWeaponFlag() {
    return weaponFlag;
  }

  @Override
  public String toString() {
    return "DamageSource[source=" + source + ", weapon=" + weaponFlag + "]";
  }
}
