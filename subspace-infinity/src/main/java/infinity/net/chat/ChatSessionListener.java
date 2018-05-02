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
package infinity.net.chat;

import com.jme3.network.service.rmi.Asynchronous;

/**
 * The asynchronous callbacks the server-side chat service uses to send
 * information to the client.
 *
 * @author Paul Speed
 */
public interface ChatSessionListener {

    /**
     * Called when a new player has joined the chat.
     *
     * @param clientId the client id
     * @param playerName the player name
     */
    @Asynchronous
    public void playerJoined(int clientId, String playerName);

    /**
     * Called when a player has sent a message to the chat.
     * @param clientId the client id
     * @param playerName the player name
     * @param message the message
     */
    @Asynchronous
    public void newMessage(int clientId, String playerName, String message);

    /**
     * Called when an existing player has left the chat.
     * @param clientId the client id
     * @param playerName the player name
     */
    @Asynchronous
    public void playerLeft(int clientId, String playerName);

}
