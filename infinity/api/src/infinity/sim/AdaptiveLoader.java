/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.sim;

import java.io.IOException;

import org.ini4j.Ini;

/**
 *
 * @author Asser Fahrenholz
 */
public interface AdaptiveLoader {

    /**
     * Used to load settings. Could be used to load the original server.cfg
     * file. 
     *
     * @param settingsFileName the file
     * @return the Ini object containing the settings
     * @throws java.io.IOException if something goes wrong
     */
    public Ini loadSettings(String settingsFileName) throws IOException;

    public boolean validateSettings(Ini settings) throws IOException;

}
