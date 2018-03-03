package example.sim;

import com.simsilica.sim.AbstractGameSystem;
import java.util.regex.Pattern;
import org.ini4j.Ini;

/**
 *
 * @author Asser
 */
public abstract class BaseGameModule extends AbstractGameSystem{

    private Ini settings;

    public BaseGameModule(Ini settings) {
        this.settings = settings;
    }

    /**
     * @return the settings that came with the module (if any)
     */
    protected Ini getSettings() {
        return settings;
    }
}
