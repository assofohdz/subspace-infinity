/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package infinity.es;

import com.simsilica.es.EntityData;
import com.simsilica.ext.mphys.ShapeInfo;
import infinity.sim.CorePhysicsConstants;
import infinity.sim.util.InfinityRunTimeException;

/**
 * These are the names of the shapes that are used by the game. They are used to look up the shape
 * information from the physics system.
 *
 * @author Asser Fahrenholz
 */
public class ShapeNames {

  public static final String SHIP_SHARK = "ship_shark";
  public static final String SHIP_WARBIRD = "ship_warbird";
  public static final String SHIP_JAVELIN = "ship_javelin";
  public static final String SHIP_SPIDER = "ship_spider";
  public static final String SHIP_LEVI = "ship_leviathan";
  public static final String SHIP_TERRIER = "ship_terrier";
  public static final String SHIP_WEASEL = "ship_weasel";
  public static final String SHIP_LANCASTER = "ship_lancaster";
  public static final String GRAV_SPHERE = "gravSphere";
  public static final String THRUST = "thrust";
  public static final String BULLETL1 = "bullet_l1";
  public static final String BULLETL2 = "bullet_l2";
  public static final String BULLETL3 = "bullet_l3";
  public static final String BULLETL4 = "bullet_l4";
  public static final String BOMBL1 = "bomb_l1";
  public static final String BOMBL2 = "bomb_l2";
  public static final String BOMBL3 = "bomb_l3";
  public static final String BOMBL4 = "bomb_l4";
  public static final String MINEL1 = "mine_l1";
  public static final String MINEL2 = "mine_l2";
  public static final String MINEL3 = "mine_l3";
  public static final String MINEL4 = "mine_l4";
  public static final String EMPL1 = "emp_l1";
  public static final String EMPL2 = "emp_l2";
  public static final String EMPL3 = "emp_l3";
  public static final String EMPL4 = "emp_l4";
  public static final String THOR = "thor";
  public static final String BURST = "burst";
  public static final String PRIZE = "bounty";
  public static final String ARENA = "arena";
  public static final String MAPTILE = "maptile";
  public static final String EXPLOSION = "explosionEffect";
  public static final String EXPLODE_0 = "explode0";
  public static final String EXPLODE_1 = "explode1";
  public static final String EXPLODE_2 = "explode2";
  public static final String OVER1 = "over1";
  public static final String OVER2 = "over2";
  public static final String OVER5 = "over5";
  public static final String WORMHOLE = "wormhole";
  public static final String WARP = "warp";
  public static final String REPEL = "repel";
  public static final String FLAG = "flag";
  public static final String DOOR = "door";

  private ShapeNames() {
    throw new InfinityRunTimeException("This class should not be instantiated");
  }

  /**
   * Creates a ship shape based on the ship type.
   *
   * @param ship the ship type
   * @param ed the entity data
   * @return the shape info
   */
  public static ShapeInfo createShip(byte ship, EntityData ed) {
    switch (ship) {
      case 0x1:
        return ShapeInfo.create(ShapeNames.SHIP_WARBIRD, CorePhysicsConstants.SHIPSIZERADIUS, ed);
      case 0x2:
        return ShapeInfo.create(ShapeNames.SHIP_JAVELIN, CorePhysicsConstants.SHIPSIZERADIUS, ed);
      case 0x3:
        return ShapeInfo.create(ShapeNames.SHIP_SPIDER, CorePhysicsConstants.SHIPSIZERADIUS, ed);
      case 0x4:
        return ShapeInfo.create(ShapeNames.SHIP_LEVI, CorePhysicsConstants.SHIPSIZERADIUS, ed);
      case 0x5:
        return ShapeInfo.create(ShapeNames.SHIP_TERRIER, CorePhysicsConstants.SHIPSIZERADIUS, ed);
      case 0x6:
        return ShapeInfo.create(ShapeNames.SHIP_LANCASTER, CorePhysicsConstants.SHIPSIZERADIUS, ed);
      case 0x7:
        return ShapeInfo.create(ShapeNames.SHIP_WEASEL, CorePhysicsConstants.SHIPSIZERADIUS, ed);
      case 0x8:
        return ShapeInfo.create(ShapeNames.SHIP_SHARK, CorePhysicsConstants.SHIPSIZERADIUS, ed);
      default:
        throw new InfinityRunTimeException("Unknown ship type: " + ship);
    }
  }
}
