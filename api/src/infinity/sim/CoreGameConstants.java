/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.sim;

/**
 * Time must be specified in milliseconds
 *
 * @author Asser Fahrenholz
 */
public class CoreGameConstants {

  public static int GRAVBOMBDAMAGE = 10;
  public static int BOMBDAMAGE = 10;
  public static int BULLETDAMAGE = 10;
  public static int THORDAMAGE = 10;

  private CoreGameConstants() {
    // Private constructor to prevent instantiation
  }

  // Projectile speeds
  public static final double BASEPROJECTILESPEED = 1;
  public static final double BOMBPROJECTILESPEED = 25;
  public static final double BULLETPROJECTILESPEED = 50;
  public static final double GRAVBOMBPROJECTILESPEED = 15;
  public static final double THORPROJECTILESPEED = 30;
  public static final double BURSTPROJECTILESPEED = 50;

  // Decays must be in milliseconds
  public static final long BULLETDECAY = 1500;
  public static final long PRIZEDECAY = 20000;
  public static final long THORDECAY = 1500;
  public static final long GRAVBOMBDECAY = 4000;
  public static final long MINEDECAY = 10000;

  // Health
  public static final int SHIPHEALTH = 100;
  public static final int BASEHEALTH = 1000;
  public static final int MOBHEALTH = 100;

  // Cooldowns
  public static final long THORCOOLDOWN = 500;
  public static final long BURSTCOOLDOWN = 250;
  public static final long GUNCOOLDOWN = 25;
  public static final long BOMBCOOLDOWN = 25;
  public static final long MINECOOLDOWN = 25;

  // Cost of firing
  public static final int GUNCOST = 10;
  public static final int BOMBCOST = 10;
  public static final int MINECOST = 10;

  public static final long BURSTPROJECTILECOUNT = 30;

  public static final int BOUNTYVALUE = 10;
  public static final int PRIZEMAXCOUNT = 10;

  public static final long GRAVBOMBDELAY = 1000;
  public static final double GRAVBOMBWORMHOLEFORCE = 5000;

  public static final double RESOURCE_UPDATE_INTERVAL = 1;
  public static final double GOLD_PER_SECOND = 10000;
  public static final float PATHWAYPOINTDISTANCE = 0.5f;
  public static final double MOBSPEED = 100;
  public static final double MOBMAXFORCE = 200;
  public static final float PATHHELPERHEIGHT = 1000;
  public static final float PATHHELPERWIDTH = 1000;

  public static final int TOWERCOST = 1000;

  public static final String DEFAULTARENAID = "default";
  public static final String BOMBLEVELPREPENDTEXT = "bomb_l";
  public static final String BULLETLEVELPREPENDTEXT = "bullet_l";

  public static final double UPDATE_SETTINGS_INTERVAL_MS = 1000;
  public static final String MINELEVELPREPENDTEXT = "mine_l";
}
