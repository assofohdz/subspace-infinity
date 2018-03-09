/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.sim;

import com.simsilica.es.EntityId;
import java.util.function.BiConsumer;

/**
 *
 * @author Asser
 */
public class CommandConsumer {

    private final int accessLevelRequired;
    private final BiConsumer<EntityId, String> consumer;

    public CommandConsumer(int accessLevelRequired, BiConsumer<EntityId, String> consumer) {
        this.accessLevelRequired = accessLevelRequired;
        this.consumer = consumer;
    }

    public int getAccessLevelRequired() {
        return accessLevelRequired;
    }

    public BiConsumer<EntityId, String> getConsumer() {
        return consumer;
    }
    
    
}
