/*
 * $Id$
 *
 * Copyright (c) 2018, Simsilica, LLC
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

package infinity.client;

import com.google.common.base.MoreObjects;

import com.simsilica.event.EventType;

/**
 * Events that are published on the event bus for different client related state
 * changes.
 *
 * @author Paul Speed
 */
public class ClientEvent {

    /**
     * Indicates that the client has fully connected.
     */
    public static EventType<ClientEvent> clientConnected = EventType.create("ClientConnected", ClientEvent.class);

    /**
     * The game session has officially started, any visits back to the lobby will be
     * for death/respawn.
     */
    public static EventType<ClientEvent> sessionStarted = EventType.create("SessionStarted", ClientEvent.class);

    /**
     * Indicates that the game session has ended.
     */
    public static EventType<ClientEvent> sessionEnded = EventType.create("SessionEnded", ClientEvent.class);

    /**
     * Indicates that the client connection has been disconnected for some reason.
     */
    public static EventType<ClientEvent> clientDisconnected = EventType.create("ClientDisconnected", ClientEvent.class);

    public ClientEvent() {
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(getClass().getSimpleName()).toString();
    }
}
