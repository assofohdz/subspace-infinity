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

import com.jme3.network.HostedConnection;
import com.jme3.network.service.AbstractHostedConnectionService;
import com.jme3.network.service.HostedServiceManager;
import com.jme3.network.service.Service;
import com.jme3.network.service.rmi.RmiHostedService;
import com.jme3.network.service.rmi.RmiRegistry;
import com.simsilica.bpos.BodyPosition;
import com.simsilica.bpos.net.BodyVisibility;
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
import infinity.InfinityConstants;
import infinity.config.EngineConfig;
import infinity.es.arena.ArenaId;
import infinity.es.input.MovementInput;
import infinity.es.ship.Player;
import infinity.net.GameSession;
import infinity.net.GameSessionListener;
import infinity.settings.EngineConfigSystem;
import infinity.sim.GameEntities;
import infinity.systems.ArenaSystem;
import infinity.sim.util.InfinityRunTimeException;
import infinity.systems.ship.ConsumableSystem;
import infinity.systems.AvatarSystem;
import infinity.systems.MapSystem;
import infinity.systems.ship.WarpSystem;
import infinity.systems.ship.WeaponsFireSystem;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages the game session for a single client. This is a hosted service that is created for each
 * client that connects to the server. It is responsible for creating the game systems and managing
 * the game state for a single client.
 *
 * @author Asser Fahrenholz
 */
public final class GameSessionHostedService extends AbstractHostedConnectionService {

  private static final String ATTRIBUTE_SESSION = "game.session";
  private static final String ATTRIBUTE_AVATAR = "game.avatarEntity";
  static Logger log = LoggerFactory.getLogger(GameSessionHostedService.class);
  private final GameSystemManager gameSystems;
  private final List<GameSessionImpl> players = new CopyOnWriteArrayList<>();
  private EntityData ed;
  private RmiHostedService rmiService;

  /**
   * Creates a new GameSessionHostedService.
   *
   * @param gameSystems The GameSystemManager that will be used to manage the game systems for this
   *     session.
   */
  public GameSessionHostedService(final GameSystemManager gameSystems) {

    this.gameSystems = gameSystems;

    setAutoHost(true);
  }

  public static EntityId getAvatarEntity(final HostedConnection conn) {
    Object o = conn.getAttribute(GameSessionHostedService.ATTRIBUTE_AVATAR);
    Long eId = (Long) o;
    return new EntityId(eId);
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
      throw new InfinityRunTimeException("GameSessionHostedService requires an RMI service.");
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
      throw new InfinityRunTimeException("AccountHostedService requires an EntityDataHostedService");
    }
    ed = eds.getEntityData();
  }

  @Override
  public void startHostingOnConnection(final HostedConnection conn) {

    log.debug("startHostingOnConnection({})", conn);

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
    log.debug("stopHostingOnConnection({})", conn);

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

  /** The connection-specific 'host' for the GameSession. */
  private class GameSessionImpl implements GameSession {

    private final HostedConnection conn;
    private final EntityId avatarEntityId;
    // Resolved at session-create time from zone.conf [ZoneEnterSpawn] X/Z.
    // arena.conf [Spawn] is reserved for in-arena respawn (ship change, death) and
    // is read off the ship's *current* ArenaId — which doesn't exist at connect time.
    private final Vec3d spawnLoc;
    // private final EntityId test = null;
    private final Vec3d lastViewLoc = new Vec3d();
    private final Quatd lastViewOrient = new Quatd();
    // private PlayerDriver driver;
    private final EntityId playerEntityId;

    // private final EntityId activation = null;
    // private final EntityId fireMain = null;
    // private final EntityId fireAlt = null;
    // private final BinIndex binIndex;
    private final WeaponsFireSystem weaponsFireSystem;
    private WarpSystem warpSys;
    private ConsumableSystem actionSys;
    private AvatarSystem avatarSys;
    private GameSessionListener callback;
    // private final MPhysSystem mphys;
    private boolean spawned;
    // private final Vec3d relativeLoc = null;
    // private MapSystem mapSystem;

    public GameSessionImpl(final HostedConnection conn) {
      this.conn = conn;

      final PhysicsSpace<?, ?> phys = gameSystems.get(PhysicsSpace.class, true);
      // mphys = gameSystems.get(MPhysSystem.class, true);
      weaponsFireSystem = gameSystems.get(WeaponsFireSystem.class, true);
      // this.mapSystem = gameSystems.get(MapSystem.class, true);

      // binIndex = phys.getBinIndex();

      // Engine-tier ship collision radius — slice s6-ship-radius lifted this
      // out of a legacy hardcoded constant (mirrors the projectile-radius
      // slice's threading shape).
      final EngineConfig engineCfg = gameSystems.get(EngineConfigSystem.class, true).get();

      this.spawnLoc = resolveInitialSpawn();

      playerEntityId = ed.createEntity();
      // Player name may not be set yet if login hasn't happened
      String playerName = conn.getAttribute("player");
      if (playerName == null) {
        playerName = "Player-" + conn.getId();
      }
      ed.setComponent(playerEntityId, new Name(playerName));

      avatarEntityId =
          GameEntities.createPlayerShip(
              spawnLoc, ed, playerEntityId, phys, 0, AvatarSystem.WARBIRD, engineCfg.shipRadius());

      // Resolve initial arena from the spawn coord. Null is tolerated — ship spawns
      // in no-arena void (no ShipConfig projection until it crosses into an arena).
      final ArenaId initialArena =
          gameSystems.get(ArenaSystem.class, true).findArenaAt(spawnLoc);
      if (initialArena != null) {
        ed.setComponent(avatarEntityId, initialArena);
      }

      ed.setComponent(avatarEntityId, new Player());

      conn.setAttribute(ATTRIBUTE_AVATAR, avatarEntityId.getId());

      if (log.isInfoEnabled()) {
        log.info("avatarId({})", avatarEntityId.getId());
      }

      log.info("createdAvatar:{}", avatarEntityId);
    }

    /**
     * Pick the player's initial spawn by reading the zone-scope
     * {@code enterSpawn} pointer, then translating that arena's arena-local
     * {@code [Spawn]} per-team data (or legacy {@code arena.groovy spawn x, z})
     * to a world coord via {@link ArenaSystem#getArenaSpawn(String, int)}.
     *
     * <p>Unifies the per-arena {@code [Spawn]} as the single source of truth for
     * spawn coords across both flows: connect-time (this method, picking which
     * arena via the zone pointer) and in-arena respawn ({@code
     * AvatarSystem.requestShipChange}, picking which arena from the ship's own
     * {@link ArenaId}).
     *
     * <p>Falls back to world origin if {@code zone.groovy} is missing, the
     * {@code enterSpawn} directive is absent, or the named arena isn't loaded
     * yet at session-connect time. {@code ArenaMembershipSystem} will reconcile
     * {@link ArenaId} on the next tick once the ship contacts a sensor.
     */
    private Vec3d resolveInitialSpawn() {
      final ArenaSystem arenas = gameSystems.get(ArenaSystem.class, true);
      final String arenaName = arenas.getZoneConfig().enterSpawnArena();
      if (arenaName == null || arenaName.isBlank()) {
        log.warn("zone.groovy has no enterSpawn arena; spawning at world origin");
        return new Vec3d(0, InfinityConstants.GAMEPLAY_Y, 0);
      }
      // Connect-time freq is always 0 — the player hasn't been assigned a
      // frequency yet (Frequency component is set by the team-balancing
      // path, which runs after spawn). Maps to team0 for typed
      // SpawnConfig arenas; legacy `arena.groovy spawn x, z` arenas
      // ignore the freq.
      final Vec3d spawn = arenas.getArenaSpawn(arenaName, 0);
      if (spawn == null) {
        log.warn("zone.groovy enterSpawn='{}' not loaded; spawning at world origin", arenaName);
        return new Vec3d(0, InfinityConstants.GAMEPLAY_Y, 0);
      }
      return spawn;
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
        throw new InfinityRunTimeException("Can't get hosted entity data for:" + conn);
      }
      // hed.registerEntityVisibility(new
      // BodyVisibility(ethereal.getStateListener(conn)));
      hed.registerComponentVisibility(new BodyVisibility(ethereal.getStateListener(conn)));

      log.info("GameSessionImpl.initialized()");

      warpSys = gameSystems.get(WarpSystem.class);
      actionSys = gameSystems.get(ConsumableSystem.class);
      avatarSys = gameSystems.get(AvatarSystem.class);
    }

    public void close() {
      log.debug("Closing game session for:{}", conn);
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
    public Vec3d getPlayerLocation() {
      return ed.getComponent(avatarEntityId, BodyPosition.class).getLastLocation();
    }

    @Override
    public EntityId getAvatar() {
      return avatarEntityId;
    }

    @Override
    public void setView(final Quatd rotation, final Vec3d location) {
      // log.info("setView(" + rotation + ", " + location + ")");
      if (!spawned) {
        spawned = true;
      }

      // Force our viewpoint to the network view.
      // This is a bit of a hack and not officially supported to keep
      // resetting yourself... but it works.
      final NetworkStateListener nsl = getService(EtherealHost.class).getStateListener(conn);
      // nsl.setMaxMessageSize(2000);
      if (nsl != null) {
        // && !selfSet) {
        nsl.setSelf(avatarEntityId.getId(), location);
        // log.debug("Setting NSL self location to: "+location);
        // selfSet = true;
      }

      lastViewLoc.set(location);
      lastViewOrient.set(rotation);
    }

    @Override
    public void setMovementInput(MovementInput input) {
      ed.setComponent(avatarEntityId, input);
    }

    protected GameSessionListener getCallback(final boolean failFast) {
      if (callback == null) {
        final RmiRegistry rmi = rmiService.getRmiRegistry(conn);
        callback = rmi.getRemoteObject(GameSessionListener.class);
        if (callback == null) {
          if (failFast) {
            throw new IllegalStateException("Unable to locate client callback for GameSessionListener");
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
      switch(actionInput){
        case ConsumableSystem.WARP:
          warpSys.warpToCenter(avatarEntityId);
          return;
        case ConsumableSystem.FIRETHOR:
          actionSys.sessionAct(avatarEntityId, ConsumableSystem.FIRETHOR);
          return;
        case ConsumableSystem.REPEL:
          actionSys.sessionAct(avatarEntityId, ConsumableSystem.REPEL);
          return;
        default:
          throw new IllegalStateException("Unexpected: " + actionInput);
      }
    }

    @Override
    public void attack(final byte attackInput) {
      weaponsFireSystem.sessionAttack(avatarEntityId, attackInput);
    }

    @Override
    public void avatar(final byte avatarInput) {
      avatarSys.requestShipChange(avatarEntityId, avatarInput);
    }

    @Override
    public void toggle(final byte toggleInput) {
      // TODO Auto-generated method stub
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
