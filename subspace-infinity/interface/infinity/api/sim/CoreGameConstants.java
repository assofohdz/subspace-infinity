/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.api.sim;

/**
 *
 * @author Asser Fahrenholz
 */
public class CoreGameConstants {
    //Projectile speeds
    public final static double BASEPROJECTILESPEED = 1;
    public final static double BOMBPROJECTILESPEED = 25;
    public final static double BULLETPROJECTILESPEED = 50;
    public final static double GRAVBOMBPROJECTILESPEED = 15;
    public final static double THORPROJECTILESPEED = 30;
    public final static double BURSTPROJECTILESPEED = 50;
    
    //Decays
    public final static long BULLETDECAY = 1500;
    public final static long PRIZEDECAY = 5000;
    public final static long THORDECAY = 1500;
    public final static long GRAVBOMBDECAY = 4000;
    
    //Health
    public final static int SHIPHEALTH = 100;
    public final static int BASEHEALTH = 1000;
    public final static int MOBHEALTH = 100;
    
    //Cooldowns
    public final static long THORCOOLDOWN = 500;
    public final static long BURSTCOOLDOWN = 250;
    
    public final static long BURSTPROJECTILECOUNT = 30;
    
    
    
    public final static int BOUNTYVALUE = 10;
    public final static int PRIZEMAXCOUNT = 50;
    
    public final static long GRAVBOMBDELAY = 1000;
    public final static double GRAVBOMBWORMHOLEFORCE = 5000;
    
    public final static double RESOURCE_UPDATE_INTERVAL = 1;
    public final static double GOLD_PER_SECOND = 10000;
    public final static float PATHWAYPOINTDISTANCE = 0.5f;
    public final static double MOBSPEED = 100;
    public final static double MOBMAXFORCE = 200;
    public final static float PATHHELPERHEIGHT = 1000;
    public final static float PATHHELPERWIDTH = 1000;
    
    public final static int TOWERCOST = 1000;
    
    public final static String DEFAULTARENAID = "default";
}
