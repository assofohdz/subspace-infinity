/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.sim;

import java.util.regex.Pattern;

/**
 *
 * @author Asser
 */
public interface CommandListener {

    /**
     * @return the regex command pattern
     */
    public abstract Pattern[] getCommandPatterns();

    /**
     * This command should be implemented by the module to receive and interpret
     * commands that matches the Pattern
     *
     * @param group the pattern that is to be interpreted
     */
    public abstract void interpretFullCommand(String group);
}
