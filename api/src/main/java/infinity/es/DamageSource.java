// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;
import infinity.es.ship.weapons.WeaponType;

/**
 * Optional metadata sibling on an {@code EnergyChange + ChangeTarget}
 * Change holder entity: identifies which entity originated the
 * energy-change request and which weapon family triggered it.
 *
 * <p>ADR 0001 (see docs/adr/0001-ecs-component-model.md): the
 * {@code EnergyChange + ChangeTarget} Change holder (drained by
 * {@link infinity.systems.ship.EnergySystem EnergySystem}) carries
 * {@code DamageSource} optionally so reactors that fork on Change type
 * — e.g. "play a hit-sound only on damage, not regen" — have a signal
 * to read. {@code ChangeTarget#source()} also records the originating
 * entity; {@code DamageSource} additionally carries the
 * weapon-family discriminator.
 *
 * <p>Conventions:
 * <ul>
 *   <li>{@code source} is the originating entity. For enemy hits this is the
 *       attacker ship; for self-cost-deduction (firing a weapon debits the
 *       firer's Energy) this is the firing ship; for non-weapon paths this is
 *       {@link EntityId#NULL_ID}.
 *   <li>{@code weaponFlag} is one of the {@link WeaponType} byte constants
 *       (BULLET / BOMB / GRAVBOMB / MINE / BURST). Non-weapon paths use
 *       {@link WeaponType#NONE}.
 *   <li>{@code DamageSource} is <strong>optional</strong> on the Change holder:
 *       absence means "unattributed" — typically regen / refill. The
 *       canonical Energy writer ({@code EnergySystem}) folds Changes
 *       regardless of {@code DamageSource} presence.
 * </ul>
 *
 * <p>Server-only — emitted and consumed inside the same tick on a short-lived
 * Change holder that never crosses the wire to clients. No serializer
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
   * The originating entity for this energy-change holder.
   * {@link EntityId#NULL_ID} for unattributed / environmental / regen paths.
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
