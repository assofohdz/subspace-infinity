// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

/** Weapon type names; routed through {@code EntityData}'s string index. */
public class WeaponTypes {

    private WeaponTypes() { /* utility class */ }

    public static final String BULLET = "bullet"; // Fast moving projectile
    public static final String BOMB = "bomb"; // Slower moving, hardrr hitting
    public static final String GRAVITYBOMB = "gravityBomb"; // Bomb that stops and sucks everything in
    public static final String MINE = "mine"; // Stationary bomb
    public static final String BURST = "burst"; // Stationary bomb
    public static final String THOR = "thor"; // Bomb that can penetrate walls
}
