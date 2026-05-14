// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.map;

public final class MapTypes {

  private MapTypes() {}

  public static final short VIE_NO_TILE = 0;
  public static final short VIE_NORMAL_START = 1;
  public static final short VIE_BORDER = 20; // Borders are not included in the .lvl files
  public static final short VIE_NORMAL_END = 161; // Tiles up to this point are part of sec.chk
  public static final short VIE_V_DOOR_START = 162;
  public static final short VIE_V_DOOR_END = 165;
  public static final short VIE_H_DOOR_START = 166;
  public static final short VIE_H_DOOR_END = 169;
  public static final short VIE_TURF_FLAG = 170;
  public static final short VIE_SAFE_ZONE = 171; // Also included in sec.chk
  public static final short VIE_GOAL_AREA = 172;
  public static final short VIE_FLY_OVER_START = 173;
  public static final short VIE_FLY_OVER_END = 175;
  public static final short VIE_FLY_UNDER_START = 176;
  public static final short VIE_FLY_UNDER_END = 190;
  public static final short VIE_ASTEROID_SMALL = 216;
  public static final short VIE_ASTEROID_MEDIUM = 217;
  public static final short VIE_ASTEROID_END = 218;
  public static final short VIE_STATION = 219;
  public static final short VIE_WORMHOLE = 220;
  public static final short SSB_TEAM_BRICK = 221; // These are internal
  public static final short SSB_ENEMY_BRICK = 222;
  public static final short SSB_TEAM_GOAL = 223;
  public static final short SSB_ENEMY_GOAL = 224;
  public static final short SSB_TEAM_FLAG = 225;
  public static final short SSB_ENEMY_FLAG = 226;
  public static final short SSB_PRIZE = 227;
  public static final short SSB_BORDER = 228; // Use SSB_BORDER instead of VIE_BORDER to fill border
}
