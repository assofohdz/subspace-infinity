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

package example.net.server;

import org.slf4j.*;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.network.HostedConnection;
import com.jme3.network.MessageConnection;
import com.jme3.network.Server;
import com.jme3.network.serializing.Serializer;
import com.jme3.network.serializing.serializers.FieldSerializer;
import com.jme3.network.service.AbstractHostedConnectionService;
import com.jme3.network.service.HostedServiceManager;
import com.jme3.network.service.rmi.RmiHostedService;
import com.jme3.network.service.rmi.RmiRegistry;

import com.simsilica.event.EventBus;
import com.simsilica.sim.GameSystemManager;

import example.net.GameSession;
import example.net.GameSessionListener;
import example.sim.ShipDriver;
import example.sim.SimplePhysics;

/**
 *  Provides game session management for connected players.  This is where
 *  all of the game-session-specific state is organized and managed on behalf
 *  of a client.  The game systems are concerned with the state of 'everyone'
 *  but a game session is specific to a player.
 *
 *  @author    Paul Speed
 */
public class GameSessionHostedService extends AbstractHostedConnectionService {

    static Logger log = LoggerFactory.getLogger(GameSessionHostedService.class);

    private static final String ATTRIBUTE_SESSION = "game.session";

    private GameSystemManager gameSystems;
    private SimplePhysics physics;

    private RmiHostedService rmiService;
    private AccountObserver accountObserver = new AccountObserver();

    private List<GameSessionImpl> players = new CopyOnWriteArrayList<>();
 
    public GameSessionHostedService( GameSystemManager gameSystems ) {
    
        this.gameSystems = gameSystems;
    
        // We do not autohost because we want to host only when the
        // player is actually logged on.
        setAutoHost(false);
        
        // Make sure that quaternions are registered with the serializer
        Serializer.registerClass(Quaternion.class, new FieldSerializer());
    }

    
    @Override
    protected void onInitialize( HostedServiceManager s ) {
        
        // Grab the RMI service so we can easily use it later        
        this.rmiService = getService(RmiHostedService.class);
        if( rmiService == null ) {
            throw new RuntimeException("GameSessionHostedService requires an RMI service.");
        }
 
        // Register ourselves to listen for global account events
        EventBus.addListener(accountObserver, AccountEvent.playerLoggedOn, AccountEvent.playerLoggedOff);
    }

    @Override
    public void start() {
        super.start();
            
        // Get the physics system... it's not available yet when onInitialize() is called.
        physics = gameSystems.get(SimplePhysics.class);
        if( physics == null ) {
            throw new RuntimeException("GameSessionHostedService requires a SimplePhysics system.");
        }
    }
 
    @Override
    public void startHostingOnConnection( HostedConnection conn ) {
        
        log.debug("startHostingOnConnection(" + conn + ")");
    
        GameSessionImpl session = new GameSessionImpl(conn);
        conn.setAttribute(ATTRIBUTE_SESSION, session);
        
        // Expose the session as an RMI resource to the client
        RmiRegistry rmi = rmiService.getRmiRegistry(conn);
        rmi.share(session, GameSession.class);
        
        players.add(session);
        
        // Notify all of our sessions
        for( GameSessionImpl player : players ) {
            player.playerJoined(session);          
        }   
    }
 
    protected GameSessionImpl getGameSession( HostedConnection conn ) {
        return conn.getAttribute(ATTRIBUTE_SESSION);
    }
    
    @Override   
    public void stopHostingOnConnection( HostedConnection conn ) {
        log.debug("stopHostingOnConnection(" + conn + ")");
        
        GameSessionImpl session = getGameSession(conn);
        if( session != null ) {
            players.remove(session);
 
            // Notify all of our sessions
            for( GameSessionImpl player : players ) {
                player.playerLeft(session);          
            }
            
            // Clear the session so we know we are logged off
            conn.setAttribute(ATTRIBUTE_SESSION, null);
            
            // If we don't do that then we'll be notified twice when the
            // player logs off.  Once because we detect the connection shutting
            // down and again because the account service has notified us the
            // player has logged off.  This is ok because sometime there might
            // be a reason the player logs out of the game session but stays
            // connected.  We just need to cover the double-event case by
            // checkint for an existing account session and then clearing it
            // when we've stopped hosting our service on it.
        }   
    }

    private class AccountObserver {
        
        public void onPlayerLoggedOn( AccountEvent event ) {
            log.debug("onPlayerLoggedOn()");
            startHostingOnConnection(event.getConnection());            
        }
        
        public void onPlayerLoggedOff( AccountEvent event ) {
            log.debug("onPlayerLoggedOff()");
            stopHostingOnConnection(event.getConnection());   
        }
    }
 
    /**
     *  The connection-specific 'host' for the GameSession.
     */ 
    private class GameSessionImpl implements GameSession {
 
        private HostedConnection conn;
        private GameSessionListener callback;
        private int bodyId;
        private ShipDriver shipDriver;
        
        public GameSessionImpl( HostedConnection conn ) {
            this.conn = conn;
            
            // Create a ship for the player
            this.shipDriver = new ShipDriver(); 
            this.bodyId = physics.createBody(shipDriver);
        }
 
        public void close() {
            // Remove out physics body
            physics.removeBody(bodyId);
        }
 
        public void playerJoined( GameSessionImpl player ) {            
            getCallback().playerJoined(player.conn.getId(),
                                       AccountHostedService.getPlayerName(player.conn),
                                       player.bodyId);
        } 

        public void playerLeft( GameSessionImpl player ) {
            getCallback().playerLeft(player.conn.getId(),
                                     AccountHostedService.getPlayerName(player.conn),
                                     player.bodyId);
        } 
 
        protected GameSessionListener getCallback() {
            if( callback == null ) {
                RmiRegistry rmi = rmiService.getRmiRegistry(conn);
                callback = rmi.getRemoteObject(GameSessionListener.class);
                if( callback == null ) {
                    throw new RuntimeException("Unable to locate client callback for GameSessionListener");
                }
            }
            return callback;
        } 
 
        @Override   
        public void move( Quaternion rotation, Vector3f thrust ) {
            if( log.isTraceEnabled() ) {
                log.trace("move(" + rotation + ", " + thrust + ")");
            }
            
            // Need to forward this to the game world
            shipDriver.applyMovementState(rotation, thrust);
        }
    }    
}


