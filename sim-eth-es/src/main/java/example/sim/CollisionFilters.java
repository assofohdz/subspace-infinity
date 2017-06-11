/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.sim;

import org.dyn4j.collision.CategoryFilter;

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
    private static final long COLLISION_CATEGORY_ALL = Long.MAX_VALUE;
    
    /**
     * Static bodies collides with all dynamics
     */
    public static final CategoryFilter FILTER_CATEGORY_STATIC_BODIES = new CategoryFilter(COLLISION_CATEGORY_STATIC_BODIES,
            COLLISION_CATEGORY_DYNAMICS_PLAYERS | COLLISION_CATEGORY_DYNAMICS_PROJECTILES | COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS);

    /**
     * Gravities collidie with all dynamics 
     */
    public static final CategoryFilter FILTER_CATEGORY_STATIC_GRAVITY = new CategoryFilter(COLLISION_CATEGORY_STATIC_GRAVITY,
            COLLISION_CATEGORY_DYNAMICS_PLAYERS | COLLISION_CATEGORY_DYNAMICS_PROJECTILES | COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS);
    
    /**
     * Dynamic players collide with everything except other players
     */
    public static final CategoryFilter FILTER_CATEGORY_DYNAMIC_PLAYERS = new CategoryFilter(COLLISION_CATEGORY_DYNAMICS_PLAYERS,
            COLLISION_CATEGORY_STATIC_GRAVITY | COLLISION_CATEGORY_DYNAMICS_PROJECTILES | COLLISION_CATEGORY_STATIC_BODIES | COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS);
    
    /**
     * Dynamic projectiles collide with everything except other projectiles
     */
    public static final CategoryFilter FILTER_CATEGORY_DYNAMIC_PROJECTILES = new CategoryFilter(COLLISION_CATEGORY_DYNAMICS_PROJECTILES,
            COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS | COLLISION_CATEGORY_DYNAMICS_PLAYERS | COLLISION_CATEGORY_STATIC_GRAVITY | COLLISION_CATEGORY_STATIC_BODIES);
    
    /**
     * Dynamic map objects collide with everything
     */
    public static final CategoryFilter FILTER_CATEGORY_DYNAMIC_MAPOBJECTS = new CategoryFilter(COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS,
            COLLISION_CATEGORY_ALL);
}
