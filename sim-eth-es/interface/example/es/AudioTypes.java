/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es;

import com.simsilica.es.EntityData;
import example.es.ship.weapons.BombLevel;
import example.es.ship.weapons.GunLevel;

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
}
