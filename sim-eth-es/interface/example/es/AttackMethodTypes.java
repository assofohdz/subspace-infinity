/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;

/**
 *
 * @author ss
 */
public class AttackMethodTypes implements EntityComponent{
    public static final String RANDOM = "random"; //Fast moving projectile
    public static final String POINTDIRECTION = "pointDirection"; //Slower moving, hardr hitting
    

    
    public static AttackMethodType random(EntityData ed) {
        return AttackMethodType.create(RANDOM, ed);
    }

    public static AttackMethodType pointDirection(EntityData ed) {
        return AttackMethodType.create(POINTDIRECTION, ed);
    }
}
