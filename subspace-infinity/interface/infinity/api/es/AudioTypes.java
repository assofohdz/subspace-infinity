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
import infinity.api.es.ship.weapons.BombLevel;
import infinity.api.es.ship.weapons.GunLevel;

/**
 *
 * @author Asser
 */
public class AudioTypes {
    
    public static final String FIRE_THOR = "fire_thor"; //Bomb that can penetrate walls
    public static final String PICKUP_PRIZE = "pickup_prize";
    public static final String FIRE_GRAVBOMB = "fire_gravbomb";
    public static final String FIRE_GUNS_L1 ="fire_guns_l1";
    public static final String FIRE_GUNS_L2 ="fire_guns_l2";
    public static final String FIRE_GUNS_L3 ="fire_guns_l3";
    public static final String FIRE_GUNS_L4 ="fire_guns_l4";
    public static final String FIRE_BOMBS_L1 ="fire_bombs_l1";
    public static final String FIRE_BOMBS_L2 ="fire_bombs_l2";
    public static final String FIRE_BOMBS_L3 ="fire_bombs_l3";
    public static final String FIRE_BOMBS_L4 ="fire_bombs_l4";
    public static final String EXPLOSION2 ="explosion2";
    public static final String BURST ="burst";

    public static AudioType fire_thor(EntityData ed) {
        return AudioType.create(FIRE_THOR, ed);
    }
    
    public static AudioType pickup_prize(EntityData ed) {
        return AudioType.create(PICKUP_PRIZE, ed);
    }
    
    public static AudioType fire_gravbomb(EntityData ed) {
        return AudioType.create(FIRE_GRAVBOMB, ed);
    }

    //Bombs
    public static AudioType fire_bomb(EntityData ed, BombLevel level) {
        switch (level.level) {
            case 1:
                return AudioType.create(FIRE_BOMBS_L1, ed);
            case 2:
                return AudioType.create(FIRE_BOMBS_L2, ed);
            case 3:
                return AudioType.create(FIRE_BOMBS_L3, ed);
            case 4:
                return AudioType.create(FIRE_BOMBS_L4, ed);
            default:
                throw new UnsupportedOperationException("Unknown bomb level: "+level.level);
        }
    }
    //Bullets
    public static AudioType fire_bullet(EntityData ed, GunLevel level) {
        switch (level.level) {
            case 1:
                return AudioType.create(FIRE_GUNS_L1, ed);
            case 2:
                return AudioType.create(FIRE_GUNS_L2, ed);
            case 3:
                return AudioType.create(FIRE_GUNS_L3, ed);
            case 4:
                return AudioType.create(FIRE_GUNS_L4, ed);
            default:
                throw new UnsupportedOperationException("Unknown gun level: "+level.level);
        }
    }
    
    public static AudioType explosion2(EntityData ed) {
        return AudioType.create(EXPLOSION2, ed);
    }

    
    public static AudioType fire_burst(EntityData ed) {
        return AudioType.create(BURST, ed);
    }
}
