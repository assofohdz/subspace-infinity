/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.sim;

import com.jme3.network.service.AbstractHostedService;
import org.ini4j.Ini;

/**
 *
 * @author Asser
 */
public abstract class BaseGameService extends AbstractHostedService {

    private Ini settings;

    public BaseGameService(Ini settings) {
        this.settings = settings;
    }

    /**
     * @return the settings that came with the module (if any)
     */
    protected Ini getSettings() {
        return settings;
    }
}
