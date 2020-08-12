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
package infinity.sim;

/**
 *
 * @author Asser
 */
public class CollisionFilters {

    /**
     * Static bodies collides with all bodies except other static bodies.
     */
    private static final long COLLISION_CATEGORY_STATIC_BODIES = 1;
    private static final long COLLISION_CATEGORY_STATIC_GRAVITY = 2;
    private static final long COLLISION_CATEGORY_DYNAMICS_PLAYERS = 4;
    private static final long COLLISION_CATEGORY_DYNAMICS_PROJECTILES = 8;
    private static final long COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS = 16;
    private static final long COLLISION_CATEGORY_SENSOR_FLAGS = 32;
    private static final long COLLISION_CATEGORY_STATIC_TOWERS = 64;
    private static final long COLLISION_CATEGORY_DYNAMICS_MOBS = 128;
    private static final long COLLISION_CATEGORY_STATIC_BASE = 256;
    private static final long COLLISION_CATEGORY_SENSOR_TOWERS = 512;
    private static final long COLLISION_CATEGORY_SENSOR_REPEL = 1024;
    private static final long COLLISION_CATEGORY_DYNAMICS_SHIP_PROJECTILES = 2048;
    private static final long COLLISION_CATEGORY_ALL = Long.MAX_VALUE;

    /**
     * Static bodies collides with all dynamics
     */
    public static final CategoryFilter FILTER_CATEGORY_STATIC_BODIES = new CategoryFilter(
            COLLISION_CATEGORY_STATIC_BODIES,
            COLLISION_CATEGORY_DYNAMICS_PLAYERS | COLLISION_CATEGORY_DYNAMICS_PROJECTILES
                    | COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS | COLLISION_CATEGORY_DYNAMICS_MOBS);

    /**
     * Gravities collides with all dynamics
     */
    public static final CategoryFilter FILTER_CATEGORY_STATIC_GRAVITY = new CategoryFilter(
            COLLISION_CATEGORY_STATIC_GRAVITY,
            COLLISION_CATEGORY_DYNAMICS_PLAYERS | COLLISION_CATEGORY_DYNAMICS_PROJECTILES
                    | COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS | COLLISION_CATEGORY_DYNAMICS_MOBS);

    /**
     * Dynamic players collide with everything except other players
     */
    public static final CategoryFilter FILTER_CATEGORY_DYNAMIC_PLAYERS = new CategoryFilter(
            COLLISION_CATEGORY_DYNAMICS_PLAYERS,
            COLLISION_CATEGORY_STATIC_GRAVITY | COLLISION_CATEGORY_DYNAMICS_PROJECTILES
                    | COLLISION_CATEGORY_STATIC_BODIES | COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS
                    | COLLISION_CATEGORY_SENSOR_FLAGS | COLLISION_CATEGORY_DYNAMICS_MOBS);

    /**
     * Dynamic projectiles collide with everything except other projectiles
     */
    public static final CategoryFilter FILTER_CATEGORY_DYNAMIC_PROJECTILES = new CategoryFilter(
            COLLISION_CATEGORY_DYNAMICS_PROJECTILES,
            COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS | COLLISION_CATEGORY_DYNAMICS_PLAYERS
                    | COLLISION_CATEGORY_STATIC_GRAVITY | COLLISION_CATEGORY_STATIC_BODIES
                    | COLLISION_CATEGORY_DYNAMICS_MOBS | COLLISION_CATEGORY_STATIC_TOWERS
                    | COLLISION_CATEGORY_STATIC_BASE);

    /**
     * Dynamic map objects collide with everything
     */
    public static final CategoryFilter FILTER_CATEGORY_DYNAMIC_MAPOBJECTS = new CategoryFilter(
            COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS, COLLISION_CATEGORY_ALL);

    /**
     * Flags collide only with players
     */
    public static final CategoryFilter FILTER_CATEGORY_SENSOR_FLAGS = new CategoryFilter(
            COLLISION_CATEGORY_SENSOR_FLAGS, COLLISION_CATEGORY_DYNAMICS_PLAYERS);

    /**
     * Towers collides players, mobs and projectiles
     */
    public static final CategoryFilter FILTER_CATEGORY_STATIC_TOWERS = new CategoryFilter(
            COLLISION_CATEGORY_STATIC_TOWERS, COLLISION_CATEGORY_DYNAMICS_PLAYERS | COLLISION_CATEGORY_DYNAMICS_MOBS
                    | COLLISION_CATEGORY_DYNAMICS_PROJECTILES | COLLISION_CATEGORY_STATIC_TOWERS);

    /**
     * Mobs collides with players, projectiles, map objects, but not themselves
     * (should be able to stack on top of each other)
     */
    public static final CategoryFilter FILTER_CATEGORY_DYNAMIC_MOBS = new CategoryFilter(
            COLLISION_CATEGORY_DYNAMICS_MOBS,
            COLLISION_CATEGORY_DYNAMICS_PLAYERS | COLLISION_CATEGORY_DYNAMICS_PROJECTILES
                    | COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS | COLLISION_CATEGORY_STATIC_TOWERS
                    | COLLISION_CATEGORY_STATIC_BASE);
    /**
     * Base senses players and mobs
     */
    public static final CategoryFilter FILTER_CATEGORY_STATIC_BASE = new CategoryFilter(COLLISION_CATEGORY_STATIC_BASE,
            COLLISION_CATEGORY_DYNAMICS_PLAYERS | COLLISION_CATEGORY_DYNAMICS_MOBS
                    | COLLISION_CATEGORY_DYNAMICS_PROJECTILES);

    public static final CategoryFilter FILTER_CATEGORY_SENSOR_TOWERS = new CategoryFilter(
            COLLISION_CATEGORY_SENSOR_TOWERS, COLLISION_CATEGORY_DYNAMICS_MOBS);

    public static final CategoryFilter FILTER_CATEGORY_SENSOR_REPEL = new CategoryFilter(
            COLLISION_CATEGORY_SENSOR_REPEL, COLLISION_CATEGORY_DYNAMICS_MOBS | COLLISION_CATEGORY_DYNAMICS_PLAYERS
                    | COLLISION_CATEGORY_DYNAMICS_PROJECTILES | COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS);

    public static final CategoryFilter FILTER_CATEGORY_DYNAMIC_SHIP_PROJECTILES = new CategoryFilter(
            COLLISION_CATEGORY_DYNAMICS_SHIP_PROJECTILES, COLLISION_CATEGORY_DYNAMICS_PLAYERS);
}
