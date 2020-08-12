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

package infinity;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.network.ConnectionListener;
import com.jme3.network.HostedConnection;
import com.jme3.network.Server;

import com.simsilica.es.EntityData;
import com.simsilica.lemur.core.VersionedHolder;
import com.simsilica.lemur.core.VersionedObject;
import com.simsilica.sim.GameSystemManager;

import infinity.server.GameServer;

/**
 * Manages the game server when hosting a game.
 *
 * @author Paul Speed
 */
public class HostState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(HostState.class);

    private GameServer gameServer;
    private int port;

    // Used to enable/disable some UI elements until we move them
    // to their own state
    private boolean singlePlayer;

    private GameSystemManager systems;
    private EntityData ed;

    private VersionedHolder<String> hostingState = new VersionedHolder<>("");
    private VersionedHolder<String> connectionCount = new VersionedHolder<>("");

    private ConnectionListener connectionListener = new ConnectionObserver();

    public HostState(int port, String description) {
        this(port, description, false);
    }

    public HostState(int port, String description, boolean singlePlayer) {
        try {
            this.singlePlayer = singlePlayer;
            this.port = port;
            this.gameServer = new GameServer(port, description);
            this.systems = gameServer.getSystems();
            this.ed = systems.get(EntityData.class);
            gameServer.getServer().addConnectionListener(connectionListener);
        } catch (IOException e) {
            throw new RuntimeException("Error creating server", e);
        }
    }

    public int getPort() {
        return port;
    }

    public GameSystemManager getSystems() {
        return systems;
    }

    public GameServer getGameServer() {
        return gameServer;
    }

    public VersionedObject<String> getHostingState() {
        return hostingState;
    }

    public VersionedObject<String> getConnectionCount() {
        return connectionCount;
    }

    /**
     * Starts the actual game session once the host has decided enough players have
     * joined.
     */
    public void startGame() {
        log.info("startGame()");

        // Need to look up the game session service and let it know the game
        // has started 'for real'
        // Or we can let some system know and it can send a game event that
        // the service is watching for?
    }

    @Override
    protected void initialize(Application app) {

        // Just double checking we aren't double-hosting because of some
        // bug
        if (getState(HostState.class) != this) {
            throw new RuntimeException("More than one HostState is not allowed.");
        }
        // We'll manage the server itself as part of the app state
        // lifecycle so that we can use enabled state for GUI elements
        // if we want. Plus, the server will not be reusable once closed
        // and so neither will the state. That fits better with the detach()
        // lifecycle than it does with enabled/disabled.
        gameServer.start();

        hostingState.setObject("Online");

        resetConnectionCount();

        // super.initialize(app);
    }

    @Override
    protected void cleanup(Application app) {
        gameServer.close("Shutting down.");
        hostingState.setObject("Offline");

        gameServer = null;
    }

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    protected void resetConnectionCount() {
        connectionCount.setObject(String.valueOf(gameServer.getServer().getConnections().size()));
    }

    private class ConnectionObserver implements ConnectionListener {

        public void connectionAdded(Server server, HostedConnection conn) {
            resetConnectionCount();
        }

        public void connectionRemoved(Server server, HostedConnection conn) {
            resetConnectionCount();
        }
    }
}
