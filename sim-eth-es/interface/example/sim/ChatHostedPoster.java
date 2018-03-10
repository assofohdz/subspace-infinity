/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package example.sim;

import com.simsilica.es.EntityId;
import java.util.regex.Pattern;

/**
 *
 * @author Asser
 */
public interface ChatHostedPoster {

    /**
     *
     * @param from
     * @param messageType
     * @param message
     */
    public void postPublicMessage(String from, int messageType, String message);

    /**
     *
     * @param from
     * @param messageType
     * @param targetEntityId
     * @param message
     */
    public void postPrivateMessage(String from, int messageType, EntityId targetEntityId, String message);

    /**
     *
     * @param from
     * @param messageType
     * @param targetFrequency
     * @param message
     */
    public void postTeamMessage(String from, int messageType, int targetFrequency, String message);

    public void registerPatternBiConsumer(Pattern pattern, String description, CommandConsumer c);
    
    public void removePatternConsumer(Pattern pattern);
    
    public void registerCommandConsumer(String cmd, String helptext, CommandConsumer c);
    
    public void removeCommandConsumer(String cmd);
    
}
