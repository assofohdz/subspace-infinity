package example.sim;

import com.simsilica.sim.AbstractGameSystem;
import org.ini4j.Ini;

/**
 *
 * @author Asser
 */
public abstract class BaseGameModule extends AbstractGameSystem {

    private Ini settings;

    public BaseGameModule(Ini settings) {

        this.settings = settings;
    }
    
    public BaseGameModule(){
        
    }

    protected Ini getSettings() {
        return settings;
    }
}
