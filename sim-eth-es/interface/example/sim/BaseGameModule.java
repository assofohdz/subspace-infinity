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
    private final ChatHostedPoster chp;
    private final AccountManager am;

    /**
     *
     * @param settings
     * @param chp
     * @param am
     */
    public BaseGameModule(Ini settings, ChatHostedPoster chp, AccountManager am) {
        this.settings = settings;
        this.chp = chp;
        this.am = am;
    }

    /**
     * @return the settings that came with the module (if any)
     */
    protected Ini getSettings() {
        return settings;
    }

    public ChatHostedPoster getChp() {
        return chp;
    }

    public AccountManager getAm() {
        return am;
    }
}
