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
  public static final double OVER5SIZERADIUS = 0.1;
  public static final double OVER1SIZERADIUS = 0.5;
  public static final double OVER2SIZERADIUS = 1;
  public static final double FLAGSIZERADIUS = 0.5;
  public static final double BURSTSIZERADIUS = 0.125f;
  public static final double REPELRADIUS = 0.125f;
  public static final double MINESIZERADIUS = 0.5f;

  private CorePhysicsConstants() {
    // Private constructor to prevent instantiation
  }
}
