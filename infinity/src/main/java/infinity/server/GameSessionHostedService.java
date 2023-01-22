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

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.simsilica.bpos.net.BodyVisibility;
import infinity.systems.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.network.HostedConnection;
import com.jme3.network.service.AbstractHostedConnectionService;
import com.jme3.network.service.HostedServiceManager;
import com.jme3.network.service.Service;
import com.jme3.network.service.rmi.RmiHostedService;
import com.jme3.network.service.rmi.RmiRegistry;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.Name;
import com.simsilica.es.server.EntityDataHostedService;
import com.simsilica.es.server.HostedEntityData;
import com.simsilica.ethereal.EtherealHost;
import com.simsilica.ethereal.NetworkStateListener;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.GameSystemManager;

import infinity.es.input.MovementInput;
import infinity.es.ship.Player;
import infinity.net.GameSession;
import infinity.net.GameSessionListener;
import infinity.sim.GameEntities;

/**
 *
 *
 * @author Paul Speed
 */
public class GameSessionHostedService extends AbstractHostedConnectionService {

    static Logger log = LoggerFactory.getLogger(GameSessionHostedService.class);

    private static final String ATTRIBUTE_SESSION = "game.session";

    private final GameSystemManager gameSystems;
    private EntityData ed;

    private RmiHostedService rmiService;

    private final List<GameSessionImpl> players = new CopyOnWriteArrayList<>();

    public GameSessionHostedService(final GameSystemManager gameSystems) {

        this.gameSystems = gameSystems;

        setAutoHost(true);
    }

    @Override
    protected <T extends Service<HostedServiceManager>> T getService(final Class<T> type) {
        return super.getService(type);
    }

    @Override
    protected void onInitialize(final HostedServiceManager s) {
        // Grab the RMI service so we can easily use it later
        rmiService = getService(RmiHostedService.class);
        if (rmiService == null) {
            throw new RuntimeException("GameSessionHostedService requires an RMI service.");
        }
    }

    @Override
    public void terminate(final HostedServiceManager serviceManager) {
        super.terminate(serviceManager);
    }

    @Override
    public void start() {
        super.start();

        final EntityDataHostedService eds = getService(EntityDataHostedService.class);
        if (eds == null) {
            throw new RuntimeException("AccountHostedService requires an EntityDataHostedService");
        }
        ed = eds.getEntityData();
    }

    @Override
    public void startHostingOnConnection(final HostedConnection conn) {

        log.debug("startHostingOnConnection(" + conn + ")");

        final GameSessionImpl session = new GameSessionImpl(conn);
        conn.setAttribute(ATTRIBUTE_SESSION, session);

        // Expose the session as an RMI resource to the client
        final RmiRegistry rmi = rmiService.getRmiRegistry(conn);
        rmi.share(session, GameSession.class);

        players.add(session);
        session.initialize();
    }

    protected GameSessionImpl getGameSession(final HostedConnection conn) {
        return conn.getAttribute(ATTRIBUTE_SESSION);
    }

    @Override
    public void stopHostingOnConnection(final HostedConnection conn) {
        log.debug("stopHostingOnConnection(" + conn + ")");

        final GameSessionImpl session = getGameSession(conn);
        if (session != null) {

            session.close();

            players.remove(session);

            // Clear the session so we know we are logged off
            conn.setAttribute(ATTRIBUTE_SESSION, null);

            // If we don't do that then we'll be notified twice when the
            // player logs off. Once because we detect the connection shutting
            // down and again because the account service has notified us the
            // player has logged off. This is ok because sometime there might
            // be a reason the player logs out of the game session but stays
            // connected. We just need to cover the double-event case by
            // checking for an existing account session and then clearing it
            // when we've stopped hosting our service on it.
        }
    }

    /**
     * The connection-specific 'host' for the GameSession.
     */
    private class GameSessionImpl implements GameSession {

        private WarpSystem warpSys;
        private boolean selfSet = false;

        private final HostedConnection conn;
        private GameSessionListener callback;

        private final EntityId avatarEntityId;

        private final Vec3d spawnLoc = new Vec3d(20, 1, 20);

        // private final EntityId activation = null;
        // private final EntityId fireMain = null;
        // private final EntityId fireAlt = null;

        private boolean spawned;

        // private final EntityId test = null;
        private final Vec3d lastViewLoc = new Vec3d();
        private final Quatd lastViewOrient = new Quatd();
        // private final Vec3d relativeLoc = null;
        private PhysicsSpace<?, ?> phys;
        // private final MPhysSystem mphys;

        // private PlayerDriver driver;
        private final EntityId playerEntityId;
        // private final BinIndex binIndex;
        private final WeaponsSystem weaponsSystem;
        // private MapSystem mapSystem;

        public GameSessionImpl(final HostedConnection conn) {
            this.conn = conn;

            phys = gameSystems.get(PhysicsSpace.class, true);
            // mphys = gameSystems.get(MPhysSystem.class, true);
            weaponsSystem = gameSystems.get(WeaponsSystem.class, true);
            // this.mapSystem = gameSystems.get(MapSystem.class, true);

            // binIndex = phys.getBinIndex();

            playerEntityId = ed.createEntity();
            ed.setComponent(playerEntityId, new Name(conn.getAttribute("player")));

            avatarEntityId = GameEntities.createShip(spawnLoc, ed, playerEntityId, phys, 0, AvatarSystem.WARBIRD);

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
                // yet. So this model only works with a separate login step.
                log.warn("No game session callback registered so can't send avatar entity.");
            }

            // Setup to start using SimEthereal synching
            final EtherealHost ethereal = getService(EtherealHost.class);
            ethereal.startHostingOnConnection(conn);
            ethereal.setConnectionObject(conn, avatarEntityId.getId(), spawnLoc);
            final EntityDataHostedService eds = getService(EntityDataHostedService.class);

            // Setup a filter for BodyPosition components to match what
            // SimEthereal says is visible for the client.
            final HostedEntityData hed = eds.getHostedEntityData(conn);
            if (hed == null) {
                throw new RuntimeException("Can't get hosted entity data for:" + conn);
            }
            // hed.registerEntityVisibility(new
            // BodyVisibility(ethereal.getStateListener(conn)));
            hed.registerComponentVisibility(new BodyVisibility(ethereal.getStateListener(conn)));

            this.phys = gameSystems.get(PhysicsSpace.class, true);

            log.info("GameSessionImpl.initialized()");

            warpSys = gameSystems.get(WarpSystem.class);
        }

        public void close() {
            log.debug("Closing game session for:" + conn);
            // Remove our physics body
            //// physics.removeBody(shipEntity);
            // Physics body is now removed as a side-effect of the entity
            // going away.

            // Remove the ship we created
            // ed.removeEntity(shipEntity);
        }

        @Override
        public EntityId getPlayer() {
            return playerEntityId;
        }

        @Override
        public EntityId getAvatar() {
            return avatarEntityId;
        }

        @Override
        public void setView(final Quatd rotation, final Vec3d location) {
//log.info("setView(" + rotation + ", " + location + ")");
            if (!spawned) {
                spawned = true;
            }

            // Force our viewpoint to the network view.
            // This is a bit of a hack and not officially supported to keep
            // resetting yourself... but it works.
            final NetworkStateListener nsl = getService(EtherealHost.class).getStateListener(conn);
            // nsl.setMaxMessageSize(2000);
            if (nsl != null) {
                //&& !selfSet) {
                nsl.setSelf(avatarEntityId.getId(), location);
                //log.debug("Setting NSL self location to: "+location);
                //selfSet = true;
            }

            lastViewLoc.set(location);
            lastViewOrient.set(rotation);
        }

        @Override
        public void setMovementInput( MovementInput input ) {
            ed.setComponent(avatarEntityId, input);
        }

        protected GameSessionListener getCallback(final boolean failFast) {
            if (callback == null) {
                final RmiRegistry rmi = rmiService.getRmiRegistry(conn);
                callback = rmi.getRemoteObject(GameSessionListener.class);
                if (callback == null) {
                    if (failFast) {
                        throw new RuntimeException("Unable to locate client callback for GameSessionListener");
                    }
                    log.warn("Unable to locate client callback for GameSessionListener");
                }
            }
            return callback;
        }

        @Override
        public void move(final MovementInput movementForces) {
            ed.setComponent(avatarEntityId, movementForces);
        }

        @Override
        public void action(final byte actionInput) {
            if (actionInput == ActionSystem.WARP){
                warpSys.requestWarpToCenter(avatarEntityId);
            }
            return;
        }

        @Override
        public void attack(final byte attackInput) {
            weaponsSystem.sessionAttack(avatarEntityId, attackInput);
        }

        @Override
        public void avatar(final byte avatarInput) {
            return;
        }

        @Override
        public void toggle(final byte toggleInput) {
            return;
        }

        @Override
        public void map(final byte mapInput, final Vec3d coords) {
            switch (mapInput) {
            case MapSystem.CREATE:
                // mapSystem.sessionCreateTile(coords.x, coords.z);
                break;
            case MapSystem.DELETE:
                // mapSystem.sessionRemoveTile(coords.x, coords.z);
                break;
            case MapSystem.READ:

                break;
            case MapSystem.UPDATE:

                break;
            default:
                throw new AssertionError();
            }
        }

    }
}
