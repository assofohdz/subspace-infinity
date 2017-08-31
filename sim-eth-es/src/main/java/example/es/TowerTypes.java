package example.es;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;

public class TowerTypes {

    private static int MSEC = 1000*1000;
    public static final String TOWER1 = "tower1";

    public static TowerType tower1(EntityData ed, EntityId id) {
        // tower type specifications
        ed.setComponents(id, 
                new Range(10), 
                ProjectileTypes.bullet(ed),
                AttackMethodTypes.random(ed),
                new AttackVelocity(500),
                new AttackRate(1000*MSEC),
                new RotationSpeed(3.14),
                ProjectileTypes.bullet(ed));
   
        
        
        return TowerType.create(TOWER1, ed);
    }
}
