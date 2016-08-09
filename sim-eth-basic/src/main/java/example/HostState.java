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

package example;

import java.io.*;

import org.slf4j.*;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.network.ConnectionListener;
import com.jme3.network.HostedConnection;
import com.jme3.network.Server;

import com.simsilica.lemur.core.VersionedHolder;
import com.simsilica.state.DebugHudState;
import com.simsilica.state.DebugHudState.Location;

import example.net.server.GameServer;

/**
 *  Manages the game server when hosting a game.
 *
 *  @author    Paul Speed
 */
public class HostState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(HostState.class);

    private GameServer gameServer;

    private VersionedHolder<String> hostingState;
    private VersionedHolder<String> connectionCount;
    
    private ConnectionListener connectionListener = new ConnectionObserver();
    
    public HostState( int port, String description ) {
        try {
            this.gameServer = new GameServer(port, description);
            gameServer.getServer().addConnectionListener(connectionListener);
        } catch( IOException e ) {
            throw new RuntimeException("Error creating server", e);
        }       
    }

    @Override   
    protected void initialize( Application app ) {
        // We'll manage the server itself as part of the app state
        // lifecycle so that we can use enabled state for GUI elements
        // if we want.  Plus, the server will not be reusable once closed
        // and so neither will the state.  That fits better with the detach()
        // lifecycle than it does with enabled/disabled.
        gameServer.start();

        hostingState = getState(DebugHudState.class).createDebugValue("Hosting", Location.Right);
        hostingState.setObject("Online");
        
        connectionCount = getState(DebugHudState.class).createDebugValue("Connections", Location.Right);
        resetConnectionCount();
    }    

    @Override   
    protected void cleanup( Application app ) {
        gameServer.close();
        hostingState.setObject("Offline");
        
        // And remove the debug messages anyway
        getState(DebugHudState.class).removeDebugValue("Hosting");
        getState(DebugHudState.class).removeDebugValue("Connections");
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
    
        public void connectionAdded( Server server, HostedConnection conn ) {
            resetConnectionCount();
        }
        
        public void connectionRemoved( Server server, HostedConnection conn ) {
            resetConnectionCount();
        }
    }
    
}
