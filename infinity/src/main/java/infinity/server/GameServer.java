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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

import com.jme3.network.HostedConnection;
import com.jme3.network.Network;
import com.jme3.network.Server;
import com.jme3.network.serializing.Serializer;
import com.jme3.network.serializing.serializers.FieldSerializer;
import com.jme3.network.service.AbstractHostedService;
import com.jme3.network.service.HostedServiceManager;
import com.jme3.network.service.rmi.RmiHostedService;
import com.jme3.network.service.rpc.RpcHostedService;
import com.simsilica.bpos.mphys.BodyPositionPublisher;
import com.simsilica.bpos.mphys.LargeGridIndexSystem;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.Name;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.es.common.Decay;
import com.simsilica.es.server.EntityDataHostedService;
import com.simsilica.es.server.EntityUpdater;
import com.simsilica.ethereal.EtherealHost;
import com.simsilica.ethereal.NetworkStateListener;
import com.simsilica.ext.mblock.BlocksResourceShapeFactory;
import com.simsilica.ext.mblock.SphereFactory;
import com.simsilica.ext.mphys.EntityBodyFactory;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeFactory;
import com.simsilica.ext.mphys.ShapeFactoryRegistry;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.BlockType;
import com.simsilica.mblock.BlockTypeIndex;
import com.simsilica.mblock.config.DefaultBlockSet;
import com.simsilica.mblock.phys.Collider;
import com.simsilica.mblock.phys.collision.CubeCollider;
import com.simsilica.mblock.phys.MBlockCollisionSystem;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mblock.phys.collision.ColliderFactories;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mworld.World;
import com.simsilica.mworld.WorldGrids;
import infinity.sim.InfinityDefaultLeafWorld;
import com.simsilica.mworld.db.ColumnDbLeafDbAdapter;
import com.simsilica.mworld.db.LeafDb;
import com.simsilica.mworld.net.server.WorldHostedService;
import com.simsilica.sim.GameLoop;
import com.simsilica.sim.GameSystemManager;
import com.simsilica.sim.common.DecaySystem;
import infinity.InfinityConstants;
import infinity.ai.MobSystem;
import infinity.es.AudioType;
import infinity.es.Flag;
import infinity.es.Frequency;
import infinity.es.Gold;
import infinity.es.Parent;
import infinity.es.PointLightComponent;
import infinity.es.ShapeNames;
import infinity.es.TileType;
import infinity.es.input.MovementInput;
import infinity.es.ship.Player;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.CubeFactory;
import infinity.sim.InfinityEntityBodyFactory;
import infinity.sim.InfinityPhysicsManager;
import infinity.sim.util.InfinityRunTimeException;
import infinity.systems.ActionSystem;
import infinity.systems.ArenaSystem;
import infinity.systems.RegionSystem;
import infinity.systems.AvatarSystem;
import infinity.systems.ContactSystem;
import infinity.systems.EnergySystem;
import infinity.systems.FrequencySystem;
import infinity.systems.GravitySystem;
import infinity.systems.InfinityTimeSystem;
import infinity.systems.MapSystem;
import infinity.systems.MapTypes;
import infinity.systems.MovementSystem;
import infinity.systems.PrizeSystem;
import infinity.systems.SettingsSystem;
import infinity.systems.WarpSystem;
import infinity.systems.WeaponsSystem;
import infinity.systems.WorldSystem;
import infinity.util.AdaptiveLoadingService;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * The main GameServer that manages the back end game services, hosts connections, etc..
 *
 * @author Paul Speed
 */
public class GameServer {

  static Logger log = LoggerFactory.getLogger(GameServer.class);

  private final Server server;
  private final GameSystemManager systems;
  private final GameLoop loop;
  private final DefaultColumnDb colDb;

  /**
   * Creates a new GameServer that will listen on the specified port.
   *
   * @param port The port to listen on.
   * @param description The description of the server.
   * @throws IOException If there was a problem creating the server.
   */
  public GameServer(final int port, @SuppressWarnings("unused") final String description)
      throws IOException {
    // Make sure we are running with a fresh serializer registry
    Serializer.initialize();

    systems = new GameSystemManager();
    loop = new GameLoop(systems);

    // Create the SpiderMonkey server and set up our standard
    // initial hosted services
    server =
        Network.createServer(
            InfinityConstants.NAME, InfinityConstants.PROTOCOL_VERSION, port, port);

    // Create a separate channel to do chat stuff, so it doesn't interfere
    // with any real game stuff.
    server.addChannel(port + 1);

    // And a separate channel for ES stuff
    server.addChannel(port + 2);

    // And a separate channel for terrain stuff
    server.addChannel(port + 3);

    // Adding a delay for the connectionAdded right after the serializer
    // registration
    // service gets to run lets the client get a small break in the buffer that
    // should
    // generally prevent the RpcCall messages from coming too quickly and getting
    // processed
    // before the SerializerRegistrationMessage has had a chance to process.
    server.getServices().addService(new DelayService());

    InfinityChatHostedService chp = new InfinityChatHostedService(InfinityConstants.CHAT_CHANNEL);

    server
        .getServices()
        .addServices(
            new RpcHostedService(),
            new RmiHostedService(),
            // new GameSessionHostedService(systems),
            new AccountHostedService(description),
            // new WorldHostedService(DemoConstants.TERRAIN_CHANNEL),
            chp);

    server.getServices().getService(InfinityChatHostedService.class).setAutoHost(true);

    // Add the SimEtheral host that will serve object sync updates to
    // the clients.
    final EtherealHost ethereal =
        new EtherealHost(
            InfinityConstants.OBJECT_PROTOCOL,
            InfinityConstants.ZONE_GRID,
            InfinityConstants.ZONE_RADIUS);
    ethereal.getZones().setSupportLargeObjects(true);
    ethereal.setTimeSource(() -> systems.getStepTime().getUnlockedTime(System.nanoTime()));
    server.getServices().addService(ethereal);

    // Set up our entity data and the hosting service
    // Make the EntityData available to other systems
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    server.getServices().addService(new EntityDataHostedService(InfinityConstants.ES_CHANNEL, ed));

    colDb = new DefaultColumnDb(new File("world.db"));
    colDb.initialize();
    LeafDb leafDb = new ColumnDbLeafDbAdapter(colDb);

    // LeafDb leafDb = new LeafDbCache(new EmptyLeafDb());
    World world = new InfinityDefaultLeafWorld(leafDb, 10);

    systems.register(World.class, world);
    server
        .getServices()
        .addService(new WorldHostedService(world, InfinityConstants.TERRAIN_CHANNEL));

    // Add the game session service last so that it has access to everything else
    server.getServices().addService(new GameSessionHostedService(systems));

    systems.addSystem(new LargeGridIndexSystem(WorldGrids.TILE_GRID));

    // Add it to the game systems so that we send updates properly
    // Use backgroundThread=true to enable the background processing thread
    systems.addSystem(
        new EntityUpdater(server.getServices().getService(EntityDataHostedService.class), true));

    // Add some standard systems
    systems.addSystem(new DecaySystem());

    // We'll need the block set in order to have physics collision
    // information.  Eventually we'll want to do this differently... probably.
    if (!BlockTypeIndex.isInitialized()) {
      DefaultBlockSet.initializeBlockTypes();
      DefaultBlockSet.initializeFluidTypes();
    }

    // Expand BlockTypeIndex to accommodate tile types (100-289)
    expandBlockTypeIndexForTiles();

    // Set up the physics space
    ShapeFactoryRegistry<MBlockShape> shapeFactory = new ShapeFactoryRegistry<>();

    registerShapeFactories(shapeFactory, ed);

    systems.register(ShapeFactory.class, shapeFactory);

    // And give that to an EntityBodyFactory where we can manage how bodies are created
    InfinityEntityBodyFactory bodyFactory =
        new InfinityEntityBodyFactory(ed, InfinityConstants.NO_GRAVITY, shapeFactory);

    MPhysSystem<MBlockShape> mBlockShapeMPhysSystem =
        new MPhysSystem<>(WorldGrids.LEAF_GRID, bodyFactory);
    systems.register(InfinityEntityBodyFactory.class, bodyFactory);
    systems.register(EntityBodyFactory.class, bodyFactory);

    // Create colliders, expanding for tile types (which have null/passthrough colliders)
    Collider[] baseColliders = new ColliderFactories(true).createColliders(BlockTypeIndex.getTypes());
    Collider[] colliders = expandCollidersForTiles(baseColliders);
    mBlockShapeMPhysSystem.setCollisionSystem(new MBlockCollisionSystem<>(world, colliders));

    systems.register(InfinityChatHostedService.class, chp);

    systems.register(MPhysSystem.class, mBlockShapeMPhysSystem);
    systems.register(PhysicsSpace.class, mBlockShapeMPhysSystem.getPhysicsSpace());
    systems.register(
        InfinityPhysicsManager.class,
        new InfinityPhysicsManager(mBlockShapeMPhysSystem.getPhysicsSpace()));

    // Subspace Infinity Specific Systems:-->
    // Set up contacts to be filtered first:
    ContactSystem<EntityId, MBlockShape> contactSystem = new ContactSystem<>();
    systems.register(ContactSystem.class, contactSystem);
    mBlockShapeMPhysSystem.getPhysicsSpace().setContactDispatcher(contactSystem);
    // Then add gamesystems:
    systems.register(EnergySystem.class, new EnergySystem());
    systems.register(AvatarSystem.class, new AvatarSystem());
    systems.register(MovementSystem.class, new MovementSystem());
    systems.register(MobSystem.class, new MobSystem());
    systems.register(WeaponsSystem.class, new WeaponsSystem());
    systems.register(ActionSystem.class, new ActionSystem());
    systems.register(ArenaSystem.class, new ArenaSystem());
    systems.register(RegionSystem.class, new RegionSystem());
    systems.register(PrizeSystem.class, new PrizeSystem(mBlockShapeMPhysSystem.getPhysicsSpace()));
    systems.register(GravitySystem.class, new GravitySystem());
    systems.register(InfinityTimeSystem.class, new InfinityTimeSystem());

    final AssetLoaderService assetLoader = new AssetLoaderService();
    server.getServices().addService(assetLoader);
    systems.register(AssetLoaderService.class, assetLoader);

    final AdaptiveLoadingService adaptiveLoader = new AdaptiveLoadingService(systems);
    server.getServices().addService(adaptiveLoader);
    systems.register(AdaptiveLoadingService.class, adaptiveLoader);

    systems.register(SettingsSystem.class, new SettingsSystem());
    systems.register(MapSystem.class, new MapSystem());
    systems.register(WarpSystem.class, new WarpSystem());
    systems.register(FrequencySystem.class, new FrequencySystem());

    systems.register(WorldSystem.class, new WorldSystem());
    // systems.register(DoorSystem.class, new DoorSystem());

    systems.register(BasicEnvironment.class, new BasicEnvironment());
    // <--

    // Add a system that will forward physics changes to the Ethereal
    // zone manager
    systems.register(
        ZoneNetworkSystem.class, new ZoneNetworkSystem<MBlockShape>(ethereal.getZones()));

    // And the system that will publish the BodyPosition components
    systems.addSystem(new BodyPositionPublisher<>());

    // Register some custom serializers
    registerSerializers();
  }

  /**
   * Allow running a basic dedicated server from the command line using the default port. If we want
   * something more advanced, then we should break it into a separate class with a proper shell and
   * so on.
   */
  public static void main(final String... args) throws Exception {

    final StringWriter sOut = new StringWriter();
    try (PrintWriter out = new PrintWriter(sOut)) {
      boolean hasDescription = false;
      for (int i = 0; i < args.length; i++) {
        if ("-m".equals(args[i])) {
          out.println(args[++i]);
          hasDescription = true;
        }
      }
      if (!hasDescription) {
        // Put a default description in
        out.println("Dedicated Server");
        out.println();
        out.println("In game:");
        out.println("WASD + mouse to move");
        out.println("Enter to open chat bar");
        out.println("F5 to toggle stats");
        out.println("Esc to open in-game help");
        out.println("PrtScrn to save a screen shot");
      }

      out.flush();
      final String desc = sOut.toString();

      final GameServer gs = new GameServer(InfinityConstants.DEFAULT_PORT, desc);
      gs.start();

      final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
      String line;
      while ((line = in.readLine()) != null) {
        if (line.length() == 0) {
          continue;
        }
        if ("exit".equals(line)) {
          break;
        } else if ("stats".equals(line)) {
          gs.logStats();
        } else {
          log.error(String.format("Unknown command:%s", line));
        }
      }
      gs.close();
    }
  }

  private void registerShapeFactories(
      ShapeFactoryRegistry<MBlockShape> shapeFactory, EntityData ed) {
    // Need a shape factory to turn ShapeInfo components into

    SphereFactory sphereFactory = new SphereFactory();
    CubeFactory cubeFactory = new CubeFactory();
    // MBlockShapes.
    shapeFactory.registerFactory(ShapeNames.SHIP_WARBIRD, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.SHIP_JAVELIN, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.SHIP_SHARK, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.SHIP_LANCASTER, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.SHIP_LEVI, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.SHIP_SPIDER, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.SHIP_TERRIER, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.SHIP_WEASEL, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BOMBL1, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BOMBL2, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BOMBL3, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BOMBL4, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BULLETL1, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BULLETL2, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BULLETL3, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.BULLETL4, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.OVER1, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.OVER2, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.OVER5, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.PRIZE, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.WORMHOLE, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.WARP, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.FLAG, sphereFactory);
    shapeFactory.registerFactory(ShapeNames.THOR, sphereFactory);

    // Register the cube factories
    shapeFactory.registerFactory(ShapeNames.ARENA, cubeFactory);
    shapeFactory.registerFactory(ShapeNames.DOOR, cubeFactory);

    shapeFactory.setDefaultFactory(new BlocksResourceShapeFactory());
  }

  protected void registerSerializers() {
    Serializer.registerClass(SpawnPosition.class, new FieldSerializer());
    Serializer.registerClass(com.simsilica.bpos.BodyPosition.class, new FieldSerializer());
    Serializer.registerClass(ShapeInfo.class, new FieldSerializer());
    Serializer.registerClass(Mass.class, new FieldSerializer());
    Serializer.registerClass(MovementInput.class, new FieldSerializer());
    Serializer.registerClass(Quatd.class, new FieldSerializer());
    Serializer.registerClass(Vec3d.class, new FieldSerializer());
    Serializer.registerClass(com.simsilica.bpos.LargeObject.class, new FieldSerializer());
    Serializer.registerClass(com.simsilica.bpos.LargeGridCell.class, new FieldSerializer());
    Serializer.registerClass(Name.class, new FieldSerializer());
    Serializer.registerClass(Frequency.class, new FieldSerializer());
    Serializer.registerClass(Flag.class, new FieldSerializer());
    Serializer.registerClass(Gold.class, new FieldSerializer());
    Serializer.registerClass(AudioType.class, new FieldSerializer());
    Serializer.registerClass(Parent.class, new FieldSerializer());
    Serializer.registerClass(TileType.class, new FieldSerializer());
    Serializer.registerClass(PointLightComponent.class, new FieldSerializer());
    Serializer.registerClass(Decay.class, new FieldSerializer());
    Serializer.registerClass(Player.class, new FieldSerializer());
    Serializer.registerClass(MovementInput.class, new FieldSerializer());
  }

  public Server getServer() {
    return server;
  }

  public GameSystemManager getSystems() {
    return systems;
  }

  /** Starts the systems and begins accepting remote connections. */
  public void start() {
    log.info("Starting game server...");
    server.start();
    loop.start(true);
    log.info("Game server started.");
  }

  /**
   * Kicks all current connection, closes the network host, stops all systems, and finally
   * terminates them. The GameServer is not restartable at this point.
   */
  public void close(final String kickMessage) {
    log.info(String.format("Stopping game server...%s", kickMessage));
    loop.stop();

    if (kickMessage != null) {
      for (final HostedConnection conn : server.getConnections()) {
        conn.close(kickMessage);
      }
    }
    server.close();

    // The GameLoop dying should have already stopped the game systems
    if (systems.isInitialized()) {
      systems.stop();
      systems.terminate();
    }
    colDb.terminate();

    log.info("Game server stopped.");
  }

  /**
   * Closes the network host, stops all systems, and finally terminates them. The GameServer is not
   * restartable at this point.
   */
  public void close() {
    close(null);
  }

  /** Logs the current connection statistics for each connection. */
  public void logStats() {

    final EtherealHost host = server.getServices().getService(EtherealHost.class);

    for (final HostedConnection conn : server.getConnections()) {
      log.info(String.format("Client[%d] address:%s", conn.getId(), conn.getAddress()));
      final NetworkStateListener listener = host.getStateListener(conn);
      if (listener == null) {
        log.info(String.format("[%d] No stats", conn.getId()));
        continue;
      }
      log.info(
          String.format(
              "[%d] Ping time: %s ms",
              conn.getId(), listener.getConnectionStats().getAveragePingTime() / 1000000.0));
      final String miss =
          String.format("%.02f", Double.valueOf(listener.getConnectionStats().getAckMissPercent()));
      log.info(String.format("[%d] Ack miss: %s%%", conn.getId(), miss));
      log.info(
          String.format(
              "[%d] Average msg size: %d bytes",
              conn.getId(), listener.getConnectionStats().getAverageMessageSize()));
    }
  }

  /** Base block type index for flat 2D tiles. Must match BlockGeometryIndex.TILE_TYPE_BASE. */
  private static final int TILE_TYPE_BASE = 100;

  /** Total number of tiles in the Subspace tileset. */
  private static final int TILE_COUNT = 190;

  /** Required size for BlockTypeIndex array to hold all tiles. */
  private static final int REQUIRED_ARRAY_SIZE = TILE_TYPE_BASE + TILE_COUNT;

  /**
   * Expands the BlockTypeIndex array to accommodate tile types (100-289). Tile slots reuse the
   * INVISIBLE_BLOCK_TYPE's BlockType so {@link com.simsilica.mblock.MaskUtils#recalculateSideMasks}
   * treats them as solid cubes and computes non-zero side masks — without that, collider lookups
   * succeed but {@link com.simsilica.mblock.phys.collision.CubeCollider#getSphereContact} early-
   * returns because dirMask == 0. Rendering is unaffected: the client's BlockGeometryIndex
   * registers its own per-tile visual BlockTypes.
   */
  private void expandBlockTypeIndexForTiles() {
    try {
      BlockType[] currentTypes = BlockTypeIndex.getTypes();
      if (currentTypes.length >= REQUIRED_ARRAY_SIZE) {
        log.info("BlockTypeIndex already has sufficient size: {}", currentTypes.length);
        return;
      }

      BlockType[] expandedTypes = Arrays.copyOf(currentTypes, REQUIRED_ARRAY_SIZE);
      BlockType solidStandin = BlockTypeIndex.get(MapSystem.INVISIBLE_BLOCK_TYPE);
      if (solidStandin == null) {
        throw new InfinityRunTimeException(
            "INVISIBLE_BLOCK_TYPE (" + MapSystem.INVISIBLE_BLOCK_TYPE
                + ") has no registered BlockType; cannot set up tile masks");
      }
      for (int i = TILE_TYPE_BASE; i < REQUIRED_ARRAY_SIZE; i++) {
        expandedTypes[i] = solidStandin;
      }

      Field typesField = BlockTypeIndex.class.getDeclaredField("types");
      typesField.setAccessible(true);
      typesField.set(null, expandedTypes);

      Field typeCountField = BlockTypeIndex.class.getDeclaredField("typeCount");
      typeCountField.setAccessible(true);
      typeCountField.set(null, REQUIRED_ARRAY_SIZE);

      log.info("Expanded BlockTypeIndex from {} to {} types for tile support",
          currentTypes.length, REQUIRED_ARRAY_SIZE);
    } catch (Exception e) {
      throw new InfinityRunTimeException("Failed to expand BlockTypeIndex for tiles", e);
    }
  }

  /**
   * Installs colliders for tile block types. Visible tiles get a unit-cube collider; flyover
   * (173-175) and flyunder (176-190) stay null so ships pass through. The incoming array is
   * already sized to {@link #REQUIRED_ARRAY_SIZE} because
   * {@link #expandBlockTypeIndexForTiles()} ran first, so we only need to fill in the tile slots.
   */
  private Collider[] expandCollidersForTiles(final Collider[] baseColliders) {
    Collider[] expanded = baseColliders.length >= REQUIRED_ARRAY_SIZE
        ? baseColliders
        : Arrays.copyOf(baseColliders, REQUIRED_ARRAY_SIZE);

    Collider solid = new CubeCollider();
    int installed = 0;
    for (int tileId = 1; tileId <= TILE_COUNT; tileId++) {
      boolean passThrough =
          (tileId >= MapTypes.vieFlyOverStart && tileId <= MapTypes.vieFlyOverEnd)
              || (tileId >= MapTypes.vieFlyUnderStart && tileId <= MapTypes.vieFlyUnderEnd);
      if (!passThrough) {
        expanded[TILE_TYPE_BASE + tileId - 1] = solid;
        installed++;
      }
    }
    log.info("Installed {} solid tile colliders ({}-{} minus flyover/flyunder); array size {}",
        installed, TILE_TYPE_BASE, TILE_TYPE_BASE + TILE_COUNT - 1, expanded.length);
    return expanded;
  }

  /**
   * This works around a limitation in SpiderMonkey that can cause problems for the
   * SerializationRegistryService if there are other messages in the same buffer as the registry
   * update call. This adds a slight delay to connections in the hopes that the messages will end up
   * in separate buffers.
   */
  private static class DelayService extends AbstractHostedService {

    private void safeSleep(final long ms) {
      try {
        Thread.sleep(ms);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new InfinityRunTimeException("Checked exceptions are lame", e);
      }
    }

    @Override
    protected void onInitialize(final HostedServiceManager serviceManager) {
      // Auto-generated method stub
    }

    @Override
    public void start() {
      // Auto-generated method stub
    }

    @Override
    public void connectionAdded(final Server server, final HostedConnection hc) {
      // Just in case
      super.connectionAdded(server, hc);
      log.debug(String.format("DelayService.connectionAdded(%s)", hc));
      safeSleep(500);
      log.debug("DelayService.delay done");
    }
  }
}
