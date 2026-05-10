// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.weapons;

/**
 * Wire-stable byte constants identifying the five Subspace weapon families.
 *
 * <p>Used by:
 * <ul>
 *   <li>{@code GameSession.attack(byte)} — the client → server fire RPC carries
 *       the weapon family as one of these bytes.
 *   <li>{@code WeaponsFireSystem} — dispatches the per-family fire pipeline
 *       (cooldown / cost / projectile-spawn / sound).
 *   <li>{@code WeaponsImpactSystem} / {@code WeaponsReaperSystem} — emit
 *       {@code DamageSource(attackerId, weaponFlag)} on damage intents so
 *       reactors (HUD / audio / score) can fork on weapon family.
 * </ul>
 *
 * <p>Lives in the api/ module because clients reference these constants
 * directly (e.g. {@code AvatarMovementState.session.attack(WeaponType.BOMB)}).
 * Pre-RaM the constants lived on {@code WeaponsSystem}; the split moved them
 * to api/ so server-side splits don't churn client imports and so the constants
 * stop crossing the api → server layer boundary (api-contracts.md).
 *
 * @author AFahrenholz
 */
public final class WeaponType {

  /** Sentinel — non-weapon damage (regen, env, future hazards). */
  public static final byte NONE = -1;

  public static final byte BULLET = 0x0;
  public static final byte BOMB = 0x1;
  public static final byte GRAVBOMB = 0x2;
  public static final byte MINE = 0x3;
  public static final byte BURST = 0x4;

  private WeaponType() {
    // constants holder — instantiation prevented
  }
}
