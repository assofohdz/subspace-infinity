// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.sim;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.simsilica.es.EntityId;

/**
 * Service for posting chat messages and registering chat-command handlers.
 *
 * @author Asser
 */
public interface ChatHostedPoster {

    /**
     * Posts a public message
     *
     * @param from        sender
     * @param messageType the message type (@see infinity.api.sim.MessageTypes in
     *                    the interface) project
     * @param message     the message
     */
    void postPublicMessage(String from, int messageType, String message);

    /**
     * Sends a private message
     *
     * @param from           sender
     * @param messageType    the message type (@see infinity.api.sim.MessageTypes in
     *                       the interface)
     * @param targetEntityId receiver
     * @param message        the message
     */
    void postPrivateMessage(String from, int messageType, EntityId targetEntityId, String message);

    /**
     * Sends a message to a team
     *
     * @param from            sender
     * @param messageType     the message type (@see infinity.api.sim.MessageTypes
     *                        in the interface)
     * @param targetFrequency the receiving team
     * @param message         the message
     */
    void postTeamMessage(String from, int messageType, int targetFrequency, String message);

    void registerPatternTriConsumer(Pattern pattern, String description, CommandTriFunction<EntityId, EntityId, Matcher, String> c);
    void registerPatternBiConsumer(Pattern pattern, String description, CommandBiFunction<EntityId, Matcher, String> c);

    void removePatternConsumer(Pattern pattern);

    void registerCommandConsumer(String cmd, String helptext, CommandFunction<Matcher, String> c);

    void removeCommandConsumer(String cmd);

}
