/* 
 * Copyright (c) 2018, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package infinity.api.es;

import com.simsilica.es.EntityData;

/**
 * Factory methods for the common object types. Because we run the string names
 * through the EntityData's string index we can't just have normal constants.
 *
 * @author Paul Speed
 */
public class WeaponTypes {

    public static final String BULLET = "bullet"; //Fast moving projectile
    public static final String BOMB = "bomb"; //Slower moving, hardrr hitting
    public static final String GRAVITYBOMB = "gravityBomb"; //Bomb that stops and sucks everything in
    public static final String MINE = "mine"; //Stationary bomb
    public static final String BURST = "burst"; //Stationary bomb
    public static final String THOR = "thor"; //Bomb that can penetrate walls

    public static WeaponType bullet(EntityData ed) {
        return WeaponType.create(BULLET, ed);
    }

    public static WeaponType burst(EntityData ed) {
        return WeaponType.create(BURST, ed);
    }

    public static WeaponType bomb(EntityData ed) {
        return WeaponType.create(BOMB, ed);
    }

    public static WeaponType gravityBomb(EntityData ed) {
        return WeaponType.create(GRAVITYBOMB, ed);
    }

    public static WeaponType mine(EntityData ed) {
        return WeaponType.create(MINE, ed);
    }

    public static WeaponType thor(EntityData ed) {
        return WeaponType.create(THOR, ed);
    }
}
