/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
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

package infinity.systems;

public class MapTypes {
  public static final short vieNoTile = 0;
  public static final short vieNormalStart = 1;
  public static final short vieBorder = 20; // Borders are not included in the .lvl files
  public static final short vieNormalEnd = 161; // Tiles up to this point are part of sec.chk
  public static final short vieVDoorStart = 162;
  public static final short vieVDoorEnd = 165;
  public static final short vieHDoorStart = 166;
  public static final short vieHDoorEnd = 169;
  public static final short vieTurfFlag = 170;
  public static final short vieSafeZone = 171; // Also included in sec.chk
  public static final short vieGoalArea = 172;
  public static final short vieFlyOverStart = 173;
  public static final short vieFlyOverEnd = 175;
  public static final short vieFlyUnderStart = 176;
  public static final short vieFlyUnderEnd = 190;
  public static final short vieAsteroidSmall = 216;
  public static final short vieAsteroidMedium = 217;
  public static final short vieAsteroidEnd = 218;
  public static final short vieStation = 219;
  public static final short vieWormhole = 220;
  public static final short ssbTeamBrick = 221; // These are internal
  public static final short ssbEnemyBrick = 222;
  public static final short ssbTeamGoal = 223;
  public static final short ssbEnemyGoal = 224;
  public static final short ssbTeamFlag = 225;
  public static final short ssbEnemyFlag = 226;
  public static final short ssbPrize = 227;
  public static final short ssbBorder = 228; // Use ssbBorder instead of vieBorder to fill border
}
