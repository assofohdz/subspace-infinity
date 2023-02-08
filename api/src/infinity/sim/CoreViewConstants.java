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
  public static final float EXPLOSION2SIZE = 2f;
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
  public static final long WARPDECAY = 800;
  public static final long REPELDECAY = 400;
  // LightSize radius
  public static final float SHIPLIGHTRADIUS = 500;
  public static final Vec3d SHIPLIGHTOFFSET = new Vec3d(0, 5, 0);
  public static float DOORSIZE = 1f;

  private CoreViewConstants() {}
}
