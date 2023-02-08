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
