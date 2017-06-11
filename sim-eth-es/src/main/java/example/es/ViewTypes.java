package example.es;

import com.simsilica.es.EntityData;

/**
 * Factory methods for the common object types. Because we run the string names
 * through the EntityData's string index we can't just have normal constants.
 *
 * @author Paul Speed
 */
public class ViewTypes {

    public static final String SHIP = "ship";
    public static final String GRAV_SPHERE = "gravSphere";
    public static final String THRUST = "thrust";
    public static final String EXPLOSION = "explosion";
    public static final String BULLET = "bullet";
    public static final String BOMB = "bomb";
    public static final String BOUNTY = "bounty";
    public static final String ARENA = "arena";
    public static final String MAPTILE = "maptile";
    public static final String EXPLOSION2 = "explosion2";
    public static final String OVER1 = "over1";
    public static final String OVER2 = "over2";
    public static final String OVER5 = "over5";
    public static final String WORMHOLE = "wormhole";
    public static final String WARP = "warp";
    public static final String REPEL = "repel";

    public static ViewType ship(EntityData ed) {
        return ViewType.create(SHIP, ed);
    }

    public static ViewType gravSphereType(EntityData ed) {
        return ViewType.create(GRAV_SPHERE, ed);
    }

    public static ViewType thrust(EntityData ed) {
        return ViewType.create(THRUST, ed);
    }

    public static ViewType explosion(EntityData ed) {
        return ViewType.create(EXPLOSION, ed);
    }

    public static ViewType bullet(EntityData ed) {
        return ViewType.create(BULLET, ed);
    }

    public static ViewType bounty(EntityData ed) {
        return ViewType.create(BOUNTY, ed);
    }

    public static ViewType bomb(EntityData ed) {
        return ViewType.create(BOMB, ed);
    }

    public static ViewType arena(EntityData ed) {
        return ViewType.create(ARENA, ed);
    }

    public static ViewType mapTile(EntityData ed) {
        return ViewType.create(MAPTILE, ed);
    }

    public static ViewType explosion2(EntityData ed) {
        return ViewType.create(EXPLOSION2, ed);
    }

    public static ViewType wormhole(EntityData ed) {
        return ViewType.create(WORMHOLE, ed);
    }

    public static ViewType over5(EntityData ed) {
        return ViewType.create(OVER5, ed);
    }

    public static ViewType over1(EntityData ed) {
        return ViewType.create(OVER1, ed);
    }
    
    public static ViewType warp(EntityData ed){
        return ViewType.create(WARP, ed);
    }
    
    public static ViewType repel(EntityData ed){
        return ViewType.create(REPEL, ed);
    }
    
    public static ViewType over2(EntityData ed){
        return ViewType.create(OVER2, ed);
    }
}
