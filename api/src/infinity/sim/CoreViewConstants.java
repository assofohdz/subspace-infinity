// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

import com.simsilica.mathd.Vec3d;

/**
 * Time must be specified in milliseconds.
 *
 * @author Asser
 */
public class CoreViewConstants {

  // Sizes
  public static final float BULLETSIZE = 0.25f;
  public static final float BOMBSIZE = 1f;
  public static final float THORSIZE = 1f;
  public static final float PRIZESIZE = 1f;
  public static final float SHIPSIZE = 2f;
  public static final float MOBSIZE = 2f;
  public static final float TOWERSIZE = 2f;
  public static final float MAPTILESIZE = 1f;
  public static final float FLAGSIZE = 1;
  public static final float BASESIZE = 5f;
  public static final float BURSTSIZE = 0.25f;
  public static final float EXPLOSION2SIZE = 3f;
  public static final float EXPLOSION1SIZE = 2f;
  public static final float EXPLOSION0SIZE = 0.5f;
  public static final float WORMHOLESIZE = 4f;
  public static final float OVER1SIZE = 1f;
  public static final float OVER2SIZE = 2f;
  public static final float OVER5SIZE = 4f;
  public static final float WARPSIZE = 3f;
  public static final float REPELSIZE = 4f;
  // Game
  public static final int ARENASIZE = 1024;
  // Decays must be in milliseconds
  public static final long EXPLOSION2DECAY = 2000;
  public static final long EXPLOSION1DECAY = 1500;
  public static final long EXPLOSION0DECAY = 500;
  public static final long WARPDECAY = 800;
  // LightSize radius
  public static final float SHIPLIGHTRADIUS = 32;
  public static final Vec3d SHIPLIGHTOFFSET = new Vec3d(0, 2, 0);

  // Wall-run lights: placed above long (>=WALL_LIGHT_MIN_RUN tile) straight wall runs.
  // ColorRGBA isn't clamped; intensity > 1.0 pushes more tiles to peak brightness,
  // visually widening the lit pool even with a short linear-falloff radius.
  // Long runs receive one light per WALL_LIGHT_SPACING tiles so that long isolated
  // walls don't go dark at the ends relative to dense clustered walls.
  public static final float WALL_LIGHT_RADIUS = 15f;
  public static final float WALL_LIGHT_INTENSITY = 4.0f;
  public static final double WALL_LIGHT_PLANE_Y = 2.0;
  public static final int WALL_LIGHT_MIN_RUN = 13;
  public static final int WALL_LIGHT_SPACING = 25;

  public static float DOORSIZE = 1f;

  private CoreViewConstants() {}
}
