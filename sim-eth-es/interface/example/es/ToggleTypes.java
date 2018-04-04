package example.es;

import com.simsilica.es.EntityData;

/**
 * Factory methods for the common object types. Because we run the string names
 * through the EntityData's string index we can't just have normal constants.
 *
 * @author Paul Speed
 */
public class ToggleTypes {

    public static final String MULTI     = "repel"; //Fast moving projectile
    public static final String ANTI = "warp"; //Fast moving projectile
    public static final String STEALTH = "portal"; //Fast moving projectile
    public static final String CLOAK = "decoy"; //Fast moving projectile
    public static final String XRADAR = "rocket"; //Fast moving projectile
    

    public static ToggleType multi(EntityData ed) {
        return ToggleType.create(MULTI, ed);
    }
    public static ToggleType anti(EntityData ed) {
        return ToggleType.create(ANTI, ed);
    }
    public static ToggleType stealth(EntityData ed) {
        return ToggleType.create(STEALTH, ed);
    }
    public static ToggleType cloak(EntityData ed) {
        return ToggleType.create(CLOAK, ed);
    }
    public static ToggleType xradar(EntityData ed) {
        return ToggleType.create(XRADAR, ed);
    }
}
