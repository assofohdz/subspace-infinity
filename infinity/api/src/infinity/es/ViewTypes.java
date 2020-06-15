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
package infinity.es;

import com.simsilica.es.EntityData;
import infinity.es.ship.weapons.BombLevelEnum;
import infinity.es.ship.weapons.GunLevelEnum;

/**
 * Factory methods for the common object types. Because we run the string names
 * through the EntityData's string index we can't just have normal constants.
 *
 * @author Paul Speed
 */
public class ViewTypes {

    public static final String SHIP_SHARK = "ship_shark";
    public static final String SHIP_WARBIRD = "ship_warbird";
    public static final String SHIP_JAVELIN = "ship_javelin";
    public static final String SHIP_SPIDER = "ship_spider";
    public static final String SHIP_LEVI = "ship_leviathan";
    public static final String SHIP_TERRIER = "ship_terrier";
    public static final String SHIP_WEASEL = "ship_weasel";
    public static final String SHIP_LANCASTER = "ship_lancaster";
    public static final String GRAV_SPHERE = "gravSphere";
    public static final String THRUST = "thrust";
    public static final String EXPLOSION = "explosion";

    public static final String BULLETL1 = "bullet_l1";
    public static final String BULLETL2 = "bullet_l2";
    public static final String BULLETL3 = "bullet_l3";
    public static final String BULLETL4 = "bullet_l4";

    public static final String BOMBL1 = "bomb_l1";
    public static final String BOMBL2 = "bomb_l2";
    public static final String BOMBL3 = "bomb_l3";
    public static final String BOMBL4 = "bomb_l4";

    public static final String EMPL1 = "emp_l1";
    public static final String EMPL2 = "emp_l2";
    public static final String EMPL3 = "emp_l3";
    public static final String EMPL4 = "emp_l4";

    public static final String THOR = "thor";
    public static final String BURST = "burst";

    public static final String PRIZE = "bounty";
    public static final String ARENA = "arena";
    public static final String MAPTILE = "maptile";
    public static final String EXPLOSION2 = "explosion2";
    public static final String OVER1 = "over1";
    public static final String OVER2 = "over2";
    public static final String OVER5 = "over5";
    public static final String WORMHOLE = "wormhole";
    public static final String WARP = "warp";
    public static final String REPEL = "repel";
    public static final String FLAG_OURS = "flag_ours";
    public static final String FLAG_THEIRS = "flag_theirs";

    //Will probably need more mob visuals along the way
    public static final String MOB = "mob";
    //Will probably need more tower visuals along the way
    public static final String TOWER = "tower";
    //Will probably need more tower visuals along the way
    public static final String BASE = "base";

    public static ViewType base(EntityData ed) {
        return ViewType.create(BASE, ed);
    }

    public static ViewType tower(EntityData ed) {
        return ViewType.create(TOWER, ed);
    }

    public static ViewType mob(EntityData ed) {
        return ViewType.create(MOB, ed);
    }

    /*
    The different ships
     */
    public static ViewType ship_warbird(EntityData ed) {
        return ViewType.create(SHIP_WARBIRD, ed);
    }

    public static ViewType ship_javelin(EntityData ed) {
        return ViewType.create(SHIP_JAVELIN, ed);
    }

    public static ViewType ship_spider(EntityData ed) {
        return ViewType.create(SHIP_SPIDER, ed);
    }

    public static ViewType ship_levi(EntityData ed) {
        return ViewType.create(SHIP_LEVI, ed);
    }

    public static ViewType ship_terrier(EntityData ed) {
        return ViewType.create(SHIP_TERRIER, ed);
    }

    public static ViewType ship_weasel(EntityData ed) {
        return ViewType.create(SHIP_WEASEL, ed);
    }

    public static ViewType ship_lanc(EntityData ed) {
        return ViewType.create(SHIP_LANCASTER, ed);
    }

    public static ViewType ship_shark(EntityData ed) {
        return ViewType.create(SHIP_SHARK, ed);
    }

    public static ViewType gravSphereType(EntityData ed) {
        return ViewType.create(GRAV_SPHERE, ed);
    }

    public static ViewType thrust(EntityData ed) {
        return ViewType.create(THRUST, ed);
    }

    public static ViewType explosion(EntityData ed) {
        return ViewType.create(EXPLOSION, ed);
    }

    //Bullets
    public static ViewType bullet(EntityData ed, GunLevelEnum level) {
        switch (level.level) {
            case 1:
                return ViewType.create(BULLETL1, ed);
            case 2:
                return ViewType.create(BULLETL2, ed);
            case 3:
                return ViewType.create(BULLETL3, ed);
            case 4:
                return ViewType.create(BULLETL4, ed);
            default:
                return ViewType.create(BULLETL1, ed);
        }
    }

    public static ViewType prize(EntityData ed) {
        return ViewType.create(PRIZE, ed);
    }

    //Bombs
    public static ViewType bomb(EntityData ed, BombLevelEnum level) {
        switch (level.level) {
            case 1:
                return ViewType.create(BOMBL1, ed);
            case 2:
                return ViewType.create(BOMBL2, ed);
            case 3:
                return ViewType.create(BOMBL3, ed);
            case 4:
                return ViewType.create(BOMBL4, ed);
            default:
                return ViewType.create(BOMBL1, ed);
        }
    }
    
    public static ViewType thor(EntityData ed){
        return ViewType.create(THOR, ed);
    }

    public static ViewType arena(EntityData ed) {
        return ViewType.create(ARENA, ed);
    }

    public static ViewType mapTile(EntityData ed) {
        return ViewType.create(MAPTILE, ed);
    }

    public static ViewType explosion2(EntityData ed) {
        return ViewType.create(EXPLOSION2, ed);
    }

    public static ViewType wormhole(EntityData ed) {
        return ViewType.create(WORMHOLE, ed);
    }

    public static ViewType over5(EntityData ed) {
        return ViewType.create(OVER5, ed);
    }

    public static ViewType over1(EntityData ed) {
        return ViewType.create(OVER1, ed);
    }

    public static ViewType warp(EntityData ed) {
        return ViewType.create(WARP, ed);
    }

    public static ViewType repel(EntityData ed) {
        return ViewType.create(REPEL, ed);
    }

    public static ViewType over2(EntityData ed) {
        return ViewType.create(OVER2, ed);
    }

    public static ViewType flag_ours(EntityData ed) {
        return ViewType.create(FLAG_OURS, ed);
    }

    public static ViewType flag_theirs(EntityData ed) {
        return ViewType.create(FLAG_THEIRS, ed);
    }

    public static ViewType burst(EntityData ed) {
        return ViewType.create(BURST, ed);
    }
}
