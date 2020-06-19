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
package infinity.server;

import com.badlogic.gdx.ai.steer.proximities.InfiniteProximity;
import com.jme3.math.ColorRGBA;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.*;

import com.jme3.network.*;
import com.jme3.network.service.*;
import com.jme3.network.service.rmi.RmiHostedService;
import com.jme3.network.service.rmi.RmiRegistry;

import com.simsilica.es.*;
import com.simsilica.es.server.HostedEntityData;
import com.simsilica.es.server.EntityDataHostedService;
import com.simsilica.ethereal.EtherealHost;
import com.simsilica.ethereal.NetworkStateListener;
import com.simsilica.ext.mphys.Gravity;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.*;
import com.simsilica.sim.*;

import com.simsilica.mphys.PhysicsSpace;
import infinity.es.PointLightComponent;
import infinity.es.WeaponTypes;
import infinity.es.ship.Player;
import infinity.es.ship.ShipTypes;
import infinity.net.GameSession;
import infinity.net.GameSessionListener;
import infinity.sim.GameEntities;
import infinity.sim.InfinityMPhysSystem;
import infinity.sim.ShipDriver;
//import infinity.systems.WeaponSystem;

/**
 *
 *
 * @author Paul Speed
 */
public class GameSessionHostedService extends AbstractHostedConnectionService {

    static Logger log = LoggerFactory.getLogger(GameSessionHostedService.class);

    private static final String ATTRIBUTE_SESSION = "game.session";

    private GameSystemManager gameSystems;
    private EntityData ed;

    private RmiHostedService rmiService;

    private List<GameSessionImpl> players = new CopyOnWriteArrayList<>();

    public GameSessionHostedService(GameSystemManager gameSystems) {

        this.gameSystems = gameSystems;

        setAutoHost(true);
    }

    @Override
    protected void onInitialize(HostedServiceManager s) {
        // Grab the RMI service so we can easily use it later        
        this.rmiService = getService(RmiHostedService.class);
        if (rmiService == null) {
            throw new RuntimeException("GameSessionHostedService requires an RMI service.");
        }
    }

    @Override
    public void terminate(HostedServiceManager serviceManager) {
        super.terminate(serviceManager);
    }

    @Override
    public void start() {
        super.start();

        EntityDataHostedService eds = getService(EntityDataHostedService.class);
        if (eds == null) {
            throw new RuntimeException("AccountHostedService requires an EntityDataHostedService");
        }
        this.ed = eds.getEntityData();
    }

    @Override
    public void startHostingOnConnection(HostedConnection conn) {

        log.debug("startHostingOnConnection(" + conn + ")");

        GameSessionImpl session = new GameSessionImpl(conn);
        conn.setAttribute(ATTRIBUTE_SESSION, session);

        // Expose the session as an RMI resource to the client
        RmiRegistry rmi = rmiService.getRmiRegistry(conn);
        rmi.share(session, GameSession.class);

        players.add(session);
        session.initialize();
    }

    protected GameSessionImpl getGameSession(HostedConnection conn) {
        return conn.getAttribute(ATTRIBUTE_SESSION);
    }

    @Override
    public void stopHostingOnConnection(HostedConnection conn) {
        log.debug("stopHostingOnConnection(" + conn + ")");

        GameSessionImpl session = getGameSession(conn);
        if (session != null) {

            session.close();

            players.remove(session);

            // Clear the session so we know we are logged off
            conn.setAttribute(ATTRIBUTE_SESSION, null);

            // If we don't do that then we'll be notified twice when the
            // player logs off.  Once because we detect the connection shutting
            // down and again because the account service has notified us the
            // player has logged off.  This is ok because sometime there might
            // be a reason the player logs out of the game session but stays
            // connected.  We just need to cover the double-event case by
            // checking for an existing account session and then clearing it
            // when we've stopped hosting our service on it.
        }
    }

    /**
     * The connection-specific 'host' for the GameSession.
     */
    private class GameSessionImpl implements GameSession {

        private HostedConnection conn;
        private GameSessionListener callback;

        private EntityId avatarEntityId;

        private Vec3d spawnLoc = new Vec3d();

        private EntityId activation = null;
        private EntityId fireMain = null;
        private EntityId fireAlt = null;

        private boolean spawned;

        private EntityId test = null;
        private Vec3d lastViewLoc = new Vec3d();
        private Quatd lastViewOrient = new Quatd();
        private Vec3d relativeLoc = null;
        private PhysicsSpace phys;
        private InfinityMPhysSystem mphys;

        private ShipDriver driver;
        private EntityId playerEntityId;

        public GameSessionImpl(HostedConnection conn) {
            this.conn = conn;

            // Create the avatar entity for this player
            //this.avatarEntity = gameSystems.get(GameEntities.class).createAvatar(playerEntity);
            //This is the player
            
            this.phys = gameSystems.get(PhysicsSpace.class, true);
            this.mphys = gameSystems.get(InfinityMPhysSystem.class, true);
            
            this.playerEntityId = ed.createEntity();
            ed.setComponent(playerEntityId, new Name(conn.getAttribute("player")));
            
            //this.avatarEntity = ed.createEntity();
            
            avatarEntityId = GameEntities.createWarbird(playerEntityId, ed, 0, phys);

            ed.setComponent(avatarEntityId, new Player());
            
            System.out.println("avatarId(" + avatarEntityId.getId() + ")");

            log.info("createdAvatar:" + avatarEntityId);
        }

        public void initialize() {
            log.info("GameSessionImpl.initialize()");
            if (getCallback(false) != null) {
                getCallback(true).setAvatar(avatarEntityId);
            } else {
                // Apparently when we call initialize to soon we don't have the delegate
                // yet.  So this model only works with a separate login step.
                log.warn("No game session callback registered so can't send avatar entity.");
            }

            // Setup to start using SimEthereal synching
            EtherealHost ethereal = getService(EtherealHost.class);
            ethereal.startHostingOnConnection(conn);
            ethereal.setConnectionObject(conn, avatarEntityId.getId(), spawnLoc);
            EntityDataHostedService eds = getService(EntityDataHostedService.class);

            // Setup a filter for BodyPosition components to match what
            // SimEthereal says is visible for the client.
            HostedEntityData hed = eds.getHostedEntityData(conn);
            if (hed == null) {
                throw new RuntimeException("Can't get hosted entity data for:" + conn);
            }
            //hed.registerEntityVisibility(new BodyVisibility(ethereal.getStateListener(conn)));
            hed.registerComponentVisibility(new BodyVisibility(ethereal.getStateListener(conn)));


            //This is the players default ship:
            //shipEntity = ed.createEntity();
            //avatarEntity = GameEntities.createShip(fireAlt, ed, settings, 0);
            /*ed.setComponents(avatarEntityId,
                    new SpawnPosition(phys.getGrid(), new Vec3d(0, 0, 0)),
                    ShapeInfo.create("warbird", 1, ed),
                    new Mass(10),
                    ShipTypes.warbird(ed),
                    new Player(),
                    new PointLightComponent(ColorRGBA.White, 500f, new Vec3d(0, 5, 0)),
                    Gravity.ZERO);
*/
            
            
            
            log.info("GameSessionImpl.initialized()");
        }

        public void close() {
            log.debug("Closing game session for:" + conn);
            // Remove our physics body
            ////physics.removeBody(shipEntity);
            // Physics body is now removed as a side-effect of the entity
            // going away.

            // Remove the ship we created
            //ed.removeEntity(shipEntity);
        }

        @Override
        public EntityId getAvatar() {
            return avatarEntityId;
        }

        @Override
        public void setView(Quatd rotation, Vec3d location) {
//log.info("setView(" + rotation + ", " + location + ")");        
            if (!spawned) {
                spawned = true;
            }

            // Force our viewpoint to the network view.
            // This is a bit of a hack and not officially supported to keep
            // resetting yourself... but it works.
            NetworkStateListener nsl = getService(EtherealHost.class).getStateListener(conn);
            //nsl.setMaxMessageSize(2000);
            if (nsl != null) {
                nsl.setSelf(avatarEntityId.getId(), location);
            }

            lastViewLoc.set(location);
            lastViewOrient.set(rotation);
        }
        
        /*        
        public void spawn() {
log.info("spawn():" + avatarEntity);        
            // Place the player in the appropriate part of the level and
            // let SimEthereal know about it.

            Vec3d spawnLoc = gameSystems.get(GameLevelSystem.class).getSpawnPoint(playerEntity);
log.info("spawn location:" + spawnLoc);

            // Give the avatar its spawn location
            ed.setComponent(avatarEntity, new SpawnPosition(spawnLoc, 0));

            //Vec3d spawnLoc = new Vec3d(); // for the moment

            // Setup to start using SimEthereal synching
            getService(EtherealHost.class).startHostingOnConnection(conn);
            getService(EtherealHost.class).setConnectionObject(conn, avatarEntity.getId(), spawnLoc);                   
        }*/
        protected GameSessionListener getCallback(boolean failFast) {
            if (callback == null) {
                RmiRegistry rmi = rmiService.getRmiRegistry(conn);
                callback = rmi.getRemoteObject(GameSessionListener.class);
                if (callback == null) {
                    if (failFast) {
                        throw new RuntimeException("Unable to locate client callback for GameSessionListener");
                    } else {
                        log.warn("Unable to locate client callback for GameSessionListener");
                    }
                }
            }
            return callback;
        }

        @Override
        public void move(Vec3d movementForces) {
            if (log.isTraceEnabled()) {
                log.trace("movementForces(" + movementForces + ")");
            }
            ShipDriver driver = mphys.getDriver(avatarEntityId);
            if (driver != null) {//Should only happen if client side is faster than server side (or debugging)
                driver.applyMovementState(movementForces);
            }
        }

        @Override
        public void createTile(String tileSet, double x, double y) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void removeTile(double x, double y) {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void attackGuns() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void attackBomb() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            /*
            if (log.isTraceEnabled()) {
                log.trace("attackBomb");
            }
            gameSystems.get(WeaponSystem.class).sessionAttack(avatarEntity, WeaponTypes.BOMB);*/
        }

        @Override
        public void attackGravityBomb() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void placeMine() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void attackBurst() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void attackThor() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void repel() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void warp() {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public void chooseShip(byte ship) {
            //phys.morph(avatarEntityId, shape;
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
        
        
    }
}
