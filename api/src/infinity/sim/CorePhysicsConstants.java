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
