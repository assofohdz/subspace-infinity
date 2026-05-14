/*
 * $Id$
 *
 * Copyright (c) 2016, Simsilica, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.client.chat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.network.MessageConnection;
import com.jme3.network.service.AbstractClientService;
import com.jme3.network.service.ClientServiceManager;
import com.jme3.network.service.rmi.RmiClientService;

import infinity.net.chat.ChatSession;
import infinity.net.chat.ChatSessionListener;

/** Client-side service providing access to the chat server. */
public class ChatClientService extends AbstractClientService implements ChatSession {

    static Logger log = LoggerFactory.getLogger(ChatClientService.class);

    private RmiClientService rmiService;
    private final int channel;
    private ChatSession delegate;

    private final ChatSessionCallback sessionCallback = new ChatSessionCallback();
    private final List<ChatSessionListener> listeners = new CopyOnWriteArrayList<>();

    public ChatClientService() {
        this(MessageConnection.CHANNEL_DEFAULT_RELIABLE);
    }

    public ChatClientService(final int channel) {
        this.channel = channel;
    }

    @Override
    public void sendMessage(final String message) {
        getDelegate().sendMessage(message);
    }

    @Override
    public List<String> getPlayerNames() {
        return getDelegate().getPlayerNames();
    }

    // Called on the networking thread; not safe for visualization mutations.
    public void addChatSessionListener(final ChatSessionListener l) {
        listeners.add(l);
    }

    public void removeChatSessionListener(final ChatSessionListener l) {
        listeners.remove(l);
    }

    @Override
    protected void onInitialize(final ClientServiceManager s) {
        log.debug("onInitialize({})", s);
        rmiService = getService(RmiClientService.class);
        if (rmiService == null) {
            throw new IllegalStateException("ChatClientService requires RMI service");
        }
        log.debug("Sharing session callback.");
        rmiService.share((byte) channel, sessionCallback, ChatSessionListener.class);
    }

    @Override
    public void start() {
        log.debug("start()");
        super.start();
    }

    private ChatSession getDelegate() {
        // Lazy lookup decouples from the connection lifecycle — caller doesn't
        // need to coordinate with onInitialize()/start() to use this service.
        if (delegate == null) {
            delegate = rmiService.getRemoteObject(ChatSession.class);
            log.debug("delegate:{}", delegate);
            if (delegate == null) {
                throw new IllegalStateException("No chat session found");
            }
        }
        return delegate;
    }

    // Shared with the server over RMI for notifications.
    private class ChatSessionCallback implements ChatSessionListener {

        @Override
        public void playerJoined(final int clientId, final String name) {
            if (log.isTraceEnabled()) {
                log.trace("playerJoined(" + clientId + ", " + name + ")");
            }
            for (final ChatSessionListener l : listeners) {
                l.playerJoined(clientId, name);
            }
        }

        @Override
        public void newMessage(final int clientId, final String name, final String message) {
            if (log.isTraceEnabled()) {
                log.trace("newMessage(" + clientId + ", " + name + ", " + message + ")");
            }
            for (final ChatSessionListener l : listeners) {
                l.newMessage(clientId, name, message);
            }
        }

        @Override
        public void playerLeft(final int clientId, final String name) {
            if (log.isTraceEnabled()) {
                log.trace("playerLeft(" + clientId + ", " + name + ")");
            }
            for (final ChatSessionListener l : listeners) {
                l.playerLeft(clientId, name);
            }
        }
    }
}
