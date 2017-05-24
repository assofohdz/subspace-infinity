/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.es;

import com.simsilica.es.EntityComponent;
import java.util.HashSet;

/**
 *
 * @author Asser
 */
public class Delay implements EntityComponent {

    long scheduledTime;
    HashSet<EntityComponent> componentSet;

    public Delay(long scheduledTime, HashSet<EntityComponent> componentSet) {
        this.componentSet = componentSet;
        this.scheduledTime = scheduledTime;
    }

    public HashSet<EntityComponent> getComponentSet() {
        return componentSet;
    }

    public long getScheduledTime() {
        return scheduledTime;
    }

}
