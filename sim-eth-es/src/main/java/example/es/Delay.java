package example.es;

import com.simsilica.es.EntityComponent;
import java.util.HashSet;

/**
 * Represents a time-to-live for an entity.
 *
 * @author Asser Fahrenholz
 */
public class Delay implements EntityComponent {

    private long start;
    private long delta;
    private HashSet<EntityComponent> delayedComponents;
    
    public Delay(long deltaMillis, HashSet<EntityComponent> delayedComponents) {
        this.start = System.nanoTime();
        this.delta = deltaMillis * 1000000;
        this.delayedComponents = delayedComponents;
    }

    public double getPercent() {
        long time = System.nanoTime();
        return (double) (time - start) / delta;
    }

    public HashSet<EntityComponent> getDelayedComponents() {
        return delayedComponents;
    }

    @Override
    public String toString() {
        return "Delay[" + (delta / 1000000.0) + " ms]";
    }
}
