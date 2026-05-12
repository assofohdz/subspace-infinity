// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.es.ship.weapons;

/** Wire-stable byte constants for the five Subspace weapon families; carried by {@code GameSession.attack(byte)}. */
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
