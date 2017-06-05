/*
 * $Id$
 * 
 * Copyright (c) 2016, Simsilica, LLC
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions 
 * are met:
 * 
 * 1. Redistributions of source code must retain the above copyright 
 *    notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright 
 *    notice, this list of conditions and the following disclaimer in 
 *    the documentation and/or other materials provided with the 
 *    distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its 
 *    contributors may be used to endorse or promote products derived 
 *    from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS 
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT 
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS 
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE 
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, 
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES 
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR 
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) 
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, 
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) 
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED 
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package example.es;

import com.simsilica.es.EntityData;

/**
 * Factory methods for the common object types. Because we run the string names
 * through the EntityData's string index we can't just have normal constants.
 *
 * @author Paul Speed
 */
public class ObjectTypes {

    public static final String SHIP = "ship";
    public static final String GRAV_SPHERE = "gravSphere";
    public static final String THRUST = "thrust";
    public static final String EXPLOSION = "explosion";
    public static final String BULLET = "bullet";
    public static final String BOMB = "bomb";
    public static final String BOUNTY = "bounty";
    public static final String ARENA = "arena";
    public static final String MAPTILE = "maptile";
    public static final String EXPLOSION2 = "explosion2";
    public static final String OVER1 = "over1";
    public static final String OVER5 = "over5";
    public static final String WORMHOLE = "wormhole";
    public static final String WARP = "warp";
    public static final String REPEL = "repel";

    public static ObjectType ship(EntityData ed) {
        return ObjectType.create(SHIP, ed);
    }

    public static ObjectType gravSphereType(EntityData ed) {
        return ObjectType.create(GRAV_SPHERE, ed);
    }

    public static ObjectType thrust(EntityData ed) {
        return ObjectType.create(THRUST, ed);
    }

    public static ObjectType explosion(EntityData ed) {
        return ObjectType.create(EXPLOSION, ed);
    }

    public static ObjectType bullet(EntityData ed) {
        return ObjectType.create(BULLET, ed);
    }

    public static ObjectType bounty(EntityData ed) {
        return ObjectType.create(BOUNTY, ed);
    }

    public static ObjectType bomb(EntityData ed) {
        return ObjectType.create(BOMB, ed);
    }

    public static ObjectType arena(EntityData ed) {
        return ObjectType.create(ARENA, ed);
    }

    public static ObjectType mapTile(EntityData ed) {
        return ObjectType.create(MAPTILE, ed);
    }

    public static ObjectType explosion2(EntityData ed) {
        return ObjectType.create(EXPLOSION2, ed);
    }

    public static ObjectType wormhole(EntityData ed) {
        return ObjectType.create(WORMHOLE, ed);
    }

    public static ObjectType over5(EntityData ed) {
        return ObjectType.create(OVER5, ed);
    }

    public static ObjectType over1(EntityData ed) {
        return ObjectType.create(OVER1, ed);
    }
    
    public static ObjectType warp(EntityData ed){
        return ObjectType.create(WARP, ed);
    }
    
    public static ObjectType repel(EntityData ed){
        return ObjectType.create(REPEL, ed);
    }
}
