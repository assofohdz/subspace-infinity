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

/**
 * Factory methods for the prize types. Because we run the string names through
 * the EntityData's string index we can't just have normal constants.
 *
 * @author Paul Speed
 */
public class PrizeTypes {

    public static final String ALLWEAPONS = "AllWeapons";
    public static final String ANTIWARP = "AntiWarp";
    public static final String BOMB = "Bomb";
    public static final String BOUNCINGBULLETS = "BouncingBullets";
    public static final String BRICK = "Brick";
    public static final String BURST = "Burst";
    public static final String CLOAK = "Cloak";
    public static final String DECOY = "Decoy";
    public static final String ENERGY = "Energy";
    public static final String GLUE = "Glue";
    public static final String GUN = "Gun";
    public static final String MULTIFIRE = "MultiFire";
    public static final String MULTIPRIZE = "MultiPrize";
    public static final String PORTAL = "Portal";
    public static final String PROXIMITY = "Proximity";
    public static final String QUICKCHARGE = "QuickCharge";
    public static final String RECHARGE = "Recharge";
    public static final String REPEL = "Repel";
    public static final String ROCKET = "Rocket";
    public static final String ROTATION = "Rotation";
    public static final String SHIELDS = "Shields";
    public static final String SHRAPNEL = "Shrapnel";
    public static final String STEALTH = "Stealth";
    public static final String THOR = "Thor";
    public static final String THRUSTER = "Thruster";
    public static final String TOPSPEED = "TopSpeed";
    public static final String WARP = "Warp";
    public static final String XRADAR = "XRadar";
    public static final String DUD = "Dud";
    public static final String SUPER = "Super";

    public static PrizeType allWeapons(final EntityData ed) {
        return PrizeType.create(ALLWEAPONS, ed);
    }

    public static PrizeType antiWarp(final EntityData ed) {
        return PrizeType.create(ANTIWARP, ed);
    }

    public static PrizeType bomb(final EntityData ed) {
        return PrizeType.create(BOMB, ed);
    }

    public static PrizeType bouncingBullets(final EntityData ed) {
        return PrizeType.create(BOUNCINGBULLETS, ed);
    }

    public static PrizeType brick(final EntityData ed) {
        return PrizeType.create(BRICK, ed);
    }

    public static PrizeType burst(final EntityData ed) {
        return PrizeType.create(BURST, ed);
    }

    public static PrizeType cloak(final EntityData ed) {
        return PrizeType.create(CLOAK, ed);
    }

    public static PrizeType decoy(final EntityData ed) {
        return PrizeType.create(DECOY, ed);
    }

    public static PrizeType energy(final EntityData ed) {
        return PrizeType.create(ENERGY, ed);
    }

    public static PrizeType glue(final EntityData ed) {
        return PrizeType.create(GLUE, ed);
    }

    public static PrizeType gun(final EntityData ed) {
        return PrizeType.create(GUN, ed);
    }

    public static PrizeType multifire(final EntityData ed) {
        return PrizeType.create(MULTIFIRE, ed);
    }

    public static PrizeType multiprize(final EntityData ed) {
        return PrizeType.create(MULTIPRIZE, ed);
    }

    public static PrizeType portal(final EntityData ed) {
        return PrizeType.create(PORTAL, ed);
    }

    public static PrizeType proximity(final EntityData ed) {
        return PrizeType.create(PROXIMITY, ed);
    }

    public static PrizeType quickCharge(final EntityData ed) {
        return PrizeType.create(QUICKCHARGE, ed);
    }

    public static PrizeType recharge(final EntityData ed) {
        return PrizeType.create(RECHARGE, ed);
    }

    public static PrizeType repel(final EntityData ed) {
        return PrizeType.create(REPEL, ed);
    }

    public static PrizeType rocket(final EntityData ed) {
        return PrizeType.create(ROCKET, ed);
    }

    public static PrizeType rotation(final EntityData ed) {
        return PrizeType.create(ROTATION, ed);
    }

    public static PrizeType shields(final EntityData ed) {
        return PrizeType.create(SHIELDS, ed);
    }

    public static PrizeType shrapnel(final EntityData ed) {
        return PrizeType.create(SHRAPNEL, ed);
    }

    public static PrizeType stealth(final EntityData ed) {
        return PrizeType.create(STEALTH, ed);
    }

    public static PrizeType thor(final EntityData ed) {
        return PrizeType.create(THOR, ed);
    }

    public static PrizeType thruster(final EntityData ed) {
        return PrizeType.create(THRUSTER, ed);
    }

    public static PrizeType topspeed(final EntityData ed) {
        return PrizeType.create(TOPSPEED, ed);
    }

    public static PrizeType warp(final EntityData ed) {
        return PrizeType.create(WARP, ed);
    }

    public static PrizeType xradar(final EntityData ed) {
        return PrizeType.create(XRADAR, ed);
    }
}
