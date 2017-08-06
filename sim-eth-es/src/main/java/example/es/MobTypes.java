package example.es;

import com.simsilica.es.EntityData;

public class MobTypes {

    
    public static final String MOB1 = "mob1";

    public static PhysicsMassType mob1(EntityData ed) {
        return PhysicsMassType.create(MOB1, ed);
    }
}
