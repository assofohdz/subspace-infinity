// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityData;
import infinity.BombLevel;
import infinity.BulletLevel;

/**
 * Audio asset identifiers used to look up sound effects.
 *
 * @author Asser
 */
public class AudioTypes {

    private AudioTypes() { /* utility class */ }

    public static final String FIRE_THOR = "fire_thor"; // Bomb that can penetrate walls
    public static final String PICKUP_PRIZE = "pickup_prize";
    public static final String FIRE_GRAVBOMB = "fire_gravbomb";
    public static final String FIRE_GUNS_L1 = "fire_guns_l1";
    public static final String FIRE_GUNS_L2 = "fire_guns_l2";
    public static final String FIRE_GUNS_L3 = "fire_guns_l3";
    public static final String FIRE_GUNS_L4 = "fire_guns_l4";
    public static final String FIRE_BOMBS_L1 = "fire_bombs_l1";
    public static final String FIRE_BOMBS_L2 = "fire_bombs_l2";
    public static final String FIRE_BOMBS_L3 = "fire_bombs_l3";
    public static final String FIRE_BOMBS_L4 = "fire_bombs_l4";
    public static final String EXPLOSION2 = "explosion2";
    public static final String BURST = "burst";
    public static final String REPEL = "repel";
    public static final String FIRE_MINE_L1 = "fire_mine_l1";
    public static final String FIRE_MINE_L2 = "fire_mine_l2";
    public static final String FIRE_MINE_L3 = "fire_mine_l3";
    public static final String FIRE_MINE_L4 = "fire_mine_l4";
    public static final String FLAG = "flag";


    public static AudioType fireThor(final EntityData ed) {
        return AudioType.create(FIRE_THOR, ed);
    }

    public static AudioType pickupPrize(final EntityData ed) {
        return AudioType.create(PICKUP_PRIZE, ed);
    }

    public static AudioType fireGravbomb(final EntityData ed) {
        return AudioType.create(FIRE_GRAVBOMB, ed);
    }

    public static AudioType fireMine(final EntityData ed, final BombLevel level){
        switch(level.level){
            case 1:
                return AudioType.create(FIRE_MINE_L1, ed);
            case 2:
                return AudioType.create(FIRE_MINE_L2, ed);
            case 3:
                return AudioType.create(FIRE_MINE_L3, ed);
            case 4:
                return AudioType.create(FIRE_MINE_L4, ed);
            default:
                throw new IllegalArgumentException("Unknown level: " + level);
        }
    }

    // BombLevel
    public static AudioType fireBomb(final EntityData ed, final BombLevel level) {
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
            throw new UnsupportedOperationException("Unknown bomb level: " + level.level);
        }
    }

    // Bullets
    public static AudioType fireBullet(final EntityData ed, final BulletLevel level) {
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
            throw new UnsupportedOperationException("Unknown bullet level: " + level.level);
        }
    }

    public static AudioType fireBurst(final EntityData ed) {
        return AudioType.create(BURST, ed);
    }
}
