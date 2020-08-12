/* 
 * Copyright (c) 2018, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package infinity.sim;

import java.util.regex.Pattern;

import com.simsilica.es.EntityId;

/**
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
    public void postPublicMessage(String from, int messageType, String message);

    /**
     * Sends a private message
     * 
     * @param from           sender
     * @param messageType    the message type (@see infinity.api.sim.MessageTypes in
     *                       the interface)
     * @param targetEntityId receiver
     * @param message        the message
     */
    public void postPrivateMessage(String from, int messageType, EntityId targetEntityId, String message);

    /**
     * Sends a message to a team
     *
     * @param from            sender
     * @param messageType     the message type (@see infinity.api.sim.MessageTypes
     *                        in the interface)
     * @param targetFrequency the receiving team
     * @param message         the message
     */
    public void postTeamMessage(String from, int messageType, int targetFrequency, String message);

    public void registerPatternBiConsumer(Pattern pattern, String description, CommandConsumer c);

    public void removePatternConsumer(Pattern pattern);

    public void registerCommandConsumer(String cmd, String helptext, CommandConsumer c);

    public void removeCommandConsumer(String cmd);

}
