// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityData;

/**
 * Factory methods for the common object types. Because we run the string names
 * through the EntityData's string index we can't just have normal constants.
 *
 * @author Paul Speed
 */
public class WeaponTypes {

    public static final String BULLET = "bullet"; // Fast moving projectile
    public static final String BOMB = "bomb"; // Slower moving, hardrr hitting
    public static final String GRAVITYBOMB = "gravityBomb"; // Bomb that stops and sucks everything in
    public static final String MINE = "mine"; // Stationary bomb
    public static final String BURST = "burst"; // Stationary bomb
    public static final String THOR = "thor"; // Bomb that can penetrate walls

    public static WeaponType bullet(final EntityData ed) {
        return WeaponType.create(BULLET, ed);
    }

    public static WeaponType burst(final EntityData ed) {
        return WeaponType.create(BURST, ed);
    }

    public static WeaponType bomb(final EntityData ed) {
        return WeaponType.create(BOMB, ed);
    }

    public static WeaponType gravityBomb(final EntityData ed) {
        return WeaponType.create(GRAVITYBOMB, ed);
    }

    public static WeaponType mine(final EntityData ed) {
        return WeaponType.create(MINE, ed);
    }

    public static WeaponType thor(final EntityData ed) {
        return WeaponType.create(THOR, ed);
    }
}
