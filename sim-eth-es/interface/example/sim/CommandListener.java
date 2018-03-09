/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.sim;

import com.simsilica.es.EntityId;
import java.util.HashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 *
 * @author Asser
 */
public interface CommandListener {

    /**
     * @return the regex command pattern
     */
    //public abstract HashMap<Pattern, Consumer<String>> getPatternConsumers();

    public abstract HashMap<Pattern, CommandConsumer> getPatternBiConsumers();
    
    
}
