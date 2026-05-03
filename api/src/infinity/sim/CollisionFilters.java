// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

/**
 * Project-wide collision category constants and category filters.
 *
 * @author Asser
 */
public class CollisionFilters {

  /** Static bodies collides with all bodies except other static bodies. */
  private static final long COLLISION_CATEGORY_STATIC_BODIES = 1;

  private static final long COLLISION_CATEGORY_STATIC_GRAVITY = 2;
  private static final long COLLISION_CATEGORY_DYNAMICS_PLAYERS = 4;
  private static final long COLLISION_CATEGORY_DYNAMICS_PROJECTILES = 8;
  private static final long COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS = 16;
  private static final long COLLISION_CATEGORY_SENSOR_FLAGS = 32;
  /** Flags collide only with players */
  public static final CategoryFilter FILTER_CATEGORY_SENSOR_FLAGS =
      new CategoryFilter(COLLISION_CATEGORY_SENSOR_FLAGS, COLLISION_CATEGORY_DYNAMICS_PLAYERS);
  private static final long COLLISION_CATEGORY_STATIC_TOWERS = 64;
  private static final long COLLISION_CATEGORY_DYNAMICS_MOBS = 128;
  /** Static bodies collides with all dynamics */
  public static final CategoryFilter FILTER_CATEGORY_STATIC_BODIES =
      new CategoryFilter(
          COLLISION_CATEGORY_STATIC_BODIES,
          COLLISION_CATEGORY_DYNAMICS_PLAYERS
              | COLLISION_CATEGORY_DYNAMICS_PROJECTILES
              | COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS
              | COLLISION_CATEGORY_DYNAMICS_MOBS);
  /** Gravities collides with all dynamics */
  public static final CategoryFilter FILTER_CATEGORY_STATIC_GRAVITY =
      new CategoryFilter(
          COLLISION_CATEGORY_STATIC_GRAVITY,
          COLLISION_CATEGORY_DYNAMICS_PLAYERS
              | COLLISION_CATEGORY_DYNAMICS_PROJECTILES
              | COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS
              | COLLISION_CATEGORY_DYNAMICS_MOBS);
  /** Towers collides players, mobs and projectiles */
  public static final CategoryFilter FILTER_CATEGORY_STATIC_TOWERS =
      new CategoryFilter(
          COLLISION_CATEGORY_STATIC_TOWERS,
          COLLISION_CATEGORY_DYNAMICS_PLAYERS
              | COLLISION_CATEGORY_DYNAMICS_MOBS
              | COLLISION_CATEGORY_DYNAMICS_PROJECTILES
              | COLLISION_CATEGORY_STATIC_TOWERS);
  private static final long COLLISION_CATEGORY_STATIC_BASE = 256;
  /** Dynamic projectiles collide with everything except other projectiles */
  public static final CategoryFilter FILTER_CATEGORY_DYNAMIC_PROJECTILES =
      new CategoryFilter(
          COLLISION_CATEGORY_DYNAMICS_PROJECTILES,
          COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS
              | COLLISION_CATEGORY_DYNAMICS_PLAYERS
              | COLLISION_CATEGORY_STATIC_GRAVITY
              | COLLISION_CATEGORY_STATIC_BODIES
              | COLLISION_CATEGORY_DYNAMICS_MOBS
              | COLLISION_CATEGORY_STATIC_TOWERS
              | COLLISION_CATEGORY_STATIC_BASE);
  /**
   * Mobs collides with players, projectiles, map objects, but not themselves (should be able to
   * stack on top of each other)
   */
  public static final CategoryFilter FILTER_CATEGORY_DYNAMIC_MOBS =
      new CategoryFilter(
          COLLISION_CATEGORY_DYNAMICS_MOBS,
          COLLISION_CATEGORY_DYNAMICS_PLAYERS
              | COLLISION_CATEGORY_DYNAMICS_PROJECTILES
              | COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS
              | COLLISION_CATEGORY_STATIC_TOWERS
              | COLLISION_CATEGORY_STATIC_BASE);
  /** Base senses players and mobs */
  public static final CategoryFilter FILTER_CATEGORY_STATIC_BASE =
      new CategoryFilter(
          COLLISION_CATEGORY_STATIC_BASE,
          COLLISION_CATEGORY_DYNAMICS_PLAYERS
              | COLLISION_CATEGORY_DYNAMICS_MOBS
              | COLLISION_CATEGORY_DYNAMICS_PROJECTILES);
  private static final long COLLISION_CATEGORY_SENSOR_TOWERS = 512;
  public static final CategoryFilter FILTER_CATEGORY_SENSOR_TOWERS =
      new CategoryFilter(COLLISION_CATEGORY_SENSOR_TOWERS, COLLISION_CATEGORY_DYNAMICS_MOBS);
  private static final long COLLISION_CATEGORY_SENSOR_REPEL = 1024;
  public static final CategoryFilter FILTER_CATEGORY_SENSOR_REPEL =
      new CategoryFilter(
          COLLISION_CATEGORY_SENSOR_REPEL,
          COLLISION_CATEGORY_DYNAMICS_MOBS
              | COLLISION_CATEGORY_DYNAMICS_PLAYERS
              | COLLISION_CATEGORY_DYNAMICS_PROJECTILES
              | COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS);
  private static final long COLLISION_CATEGORY_DYNAMICS_SHIP_PROJECTILES = 2048;
  public static final CategoryFilter FILTER_CATEGORY_DYNAMIC_SHIP_PROJECTILES =
      new CategoryFilter(
          COLLISION_CATEGORY_DYNAMICS_SHIP_PROJECTILES, COLLISION_CATEGORY_DYNAMICS_PLAYERS);
  private static final long COLLISION_CATEGORY_PRIZES = 4096;
  private static final long COLLISION_CATEGORY_WORMHOLES = 8192;
  public static final CategoryFilter FILTER_CATEGORY_PRIZES =
      new CategoryFilter(
          COLLISION_CATEGORY_PRIZES,
          COLLISION_CATEGORY_DYNAMICS_PLAYERS | COLLISION_CATEGORY_WORMHOLES);
  public static final CategoryFilter FILTER_CATEGORY_WORMHOLES =
      new CategoryFilter(
          COLLISION_CATEGORY_WORMHOLES,
          COLLISION_CATEGORY_DYNAMICS_PLAYERS | COLLISION_CATEGORY_PRIZES);
  /** Dynamic players collide with everything except other players */
  public static final CategoryFilter FILTER_CATEGORY_DYNAMIC_PLAYERS =
      new CategoryFilter(
          COLLISION_CATEGORY_DYNAMICS_PLAYERS,
          COLLISION_CATEGORY_STATIC_GRAVITY
              | COLLISION_CATEGORY_DYNAMICS_PROJECTILES
              | COLLISION_CATEGORY_STATIC_BODIES
              | COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS
              | COLLISION_CATEGORY_SENSOR_FLAGS
              | COLLISION_CATEGORY_DYNAMICS_MOBS
              | COLLISION_CATEGORY_PRIZES
              | COLLISION_CATEGORY_WORMHOLES);
  private static final long COLLISION_CATEGORY_ALL = Long.MAX_VALUE;
  /** Dynamic map objects collide with everything */
  public static final CategoryFilter FILTER_CATEGORY_DYNAMIC_MAPOBJECTS =
      new CategoryFilter(COLLISION_CATEGORY_DYNAMICS_MAPOBJECTS, COLLISION_CATEGORY_ALL);
}
