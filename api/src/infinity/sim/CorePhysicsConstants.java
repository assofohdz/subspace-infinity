// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

/**
 * This class contains all the constants used in the physics engine. It is
 * separated from the rest of the code to make it easier to change the values
 * without having to search through the code.
 *
 * @author Asser
 */
public class CorePhysicsConstants {

  // Radius
  public static final double BULLETSIZERADIUS = 0.125f;
  public static final double BOMBSIZERADIUS = 0.5f;
  public static final double THORSIZERADIUS = 0.5f;
  public static final double PRIZESIZERADIUS = 0.5f;
  public static final double SHIPSIZERADIUS = 1f;
  public static final double MOBSIZERADIUS = 1f;
  public static final double TOWERSIZERADIUS = 1f;
  public static final double BASESIZERADIUS = 2.5f;
  public static final double WORMHOLESIZERADIUS = 0.1;
  public static final double OVER5SIZERADIUS = 0.1;
  public static final double OVER1SIZERADIUS = 0.5;
  public static final double OVER2SIZERADIUS = 1;
  public static final double FLAGSIZERADIUS = 0.5;
  public static final double BURSTSIZERADIUS = 0.125f;
  public static final double SAFETYOFFSET = 0.05f;
  public static final double REPELRADIUS = 0.125f;
  // Weights
  public static final double SHIPMASS = 50;
  public static final double BOMBMASS = 25;
  public static final double BULLETMASS = 5;
  public static final double MAPTILEMASS = 0; // Infinite mass
  public static final double WORMHOLEMASS = 0;
  public static final double OVER1MASS = 10;
  public static final double OVER2MASS = 40;
  public static final double OVER5MASS = 0;
  // View
  public static final double PROJECTILEOFFSET = 3;
  // Forces
  public static final float SHIPTHRUST = 10;
  // Pathfinding and polygons
  public static final int VERTEXCOUNTCIRCLE = 20;
  // Map tiles
  public static final int MAPTILEWIDTH = 1;
  public static final int MAPTILEHEIGHT = 1;
  public static final double DOORWIDTH = 1;
  public static final double ARENAWIDTH = 1024;
  public static final double MINESIZERADIUS = 0.5f;
  public static final double PHYSICS_SCALE = 1;
  private CorePhysicsConstants() {
    // Private constructor to prevent instantiation
  }
}
