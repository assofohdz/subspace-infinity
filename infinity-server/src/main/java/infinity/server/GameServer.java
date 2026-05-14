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
import com.simsilica.mblock.BlockTypeIndex;
import com.simsilica.mblock.config.DefaultBlockSet;
import com.simsilica.mblock.phys.Collider;
import com.simsilica.mblock.phys.MBlockCollisionSystem;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mblock.phys.collision.ColliderFactories;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mworld.World;
import com.simsilica.mworld.WorldGrids;
import infinity.sim.internal.InfinityDefaultLeafWorld;
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
import com.simsilica.bpos.LargeObject;
import infinity.es.MobType;
import infinity.es.Parent;
import infinity.es.PointLightComponent;
import infinity.es.ProbeInfo;
import infinity.es.arena.ArenaFootprint;
import infinity.es.RadarShapeInfo;
import infinity.es.ShapeNames;
import infinity.es.Speech;
import infinity.es.TileType;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.es.input.MovementInput;
import infinity.es.ship.CollidesWithLargeStatics;
import infinity.es.ship.Player;
import infinity.es.ship.RadarRange;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.internal.CubeFactory;
import infinity.sim.internal.InfinityEntityBodyFactory;
import infinity.sim.internal.InfinityPhysicsManager;
import infinity.sim.util.InfinityRunTimeException;
import infinity.systems.ship.ConsumableSystem;
import infinity.systems.ship.ProximityFuseSystem;
import infinity.systems.ship.RepelSystem;
import infinity.systems.ship.RocketBuffSystem;
import infinity.systems.ship.StatusDrainSystem;
import infinity.systems.ArenaMembershipSystem;
import infinity.systems.ArenaCommandsSystem;
import infinity.systems.ArenaSystem;
import infinity.systems.ChecksShipsSystem;
import infinity.systems.ChecksWorldSystem;
import infinity.systems.RegionSystem;
import infinity.systems.AvatarSystem;
import infinity.systems.ContactSystem;
import infinity.systems.ship.AntiwarpSystem;
import infinity.systems.ship.BombSystem;
import infinity.systems.ship.BulletSystem;
import infinity.systems.ship.BurstSystem;
import infinity.systems.ship.CloakSystem;
import infinity.systems.ship.EnergySystem;
import infinity.systems.ship.MineSystem;
import infinity.systems.ship.EnergyStatsSystem;
import infinity.systems.ship.RotationSystem;
import infinity.systems.ship.SpeedSystem;
import infinity.systems.ship.StealthSystem;
import infinity.systems.ship.ThrustSystem;
import infinity.systems.ship.XRadarSystem;
import infinity.systems.FrequencySystem;
import infinity.systems.GravitySystem;
import infinity.systems.InfinityTimeSystem;
import infinity.systems.MapSystem;
import infinity.systems.MovementInputSystem;
import infinity.systems.DeathPrizeSystem;
import infinity.systems.PrizeConsumptionSystem;
import infinity.systems.PrizeSpawnerSystem;
import infinity.systems.ServerTelemetrySystem;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.EngineConfigSystem;
import infinity.settings.GroovyShipLoader;
import infinity.systems.SettingsSystem;
import infinity.systems.ship.ShipSpawnSystem;
import infinity.systems.ship.WarpSystem;
import infinity.systems.ship.WeaponsFireAudioSystem;
import infinity.systems.ship.WeaponsFireEligibilitySystem;
import infinity.systems.ship.WeaponsProjectileSpawnSystem;
import infinity.systems.ship.WeaponsImpactSystem;
import infinity.systems.ship.WeaponsReaperSystem;
import infinity.systems.WorldSystem;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/** Backend game-services owner: networking, ECS, physics, the system registration list. */
public class GameServer {

  static Logger log = LoggerFactory.getLogger(GameServer.class);

  private final Server server;
  private final GameSystemManager systems;
  private final GameLoop loop;
  private final DefaultColumnDb colDb;

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

    // DelayService — gives SerializerRegistrationMessage a buffer of its own; see DelayService Javadoc.
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

    // ADR 0001 canonical writers MUST register BEFORE DecaySystem. The
    // Change-entity drain pattern relies on the writer's update running
    // first: tick N writer.add → apply + cache (target, delta); tick N reaper
    // destroys the Decay-bound Change entity; tick N+1 writer.remove →
    // reverse from cache. Reaper-before-writer ordering would let the
    // entity vanish before the writer cached the tuple, breaking
    // reverse-on-expiry for every temporary buff (rocket-thrust, super,
    // future status toggles).
    systems.register(EnergySystem.class, new EnergySystem());
    systems.register(EnergyStatsSystem.class, new EnergyStatsSystem());
    // Movement (ADR 0001 wave 1) — Rotation/Speed/Thrust Continuous-half writers.
    // SpeedSystem/ThrustSystem bypass their normal SpeedStats.max / ThrustStats.max
    // clamp for Decay-bound (temporary) deltas because rocket-buff RocketSpeed /
    // RocketThrust legally exceed the per-ship caps; the bypass is what makes
    // the buff visible. Stats records are spawn-only (ShipSpawnSystem) so no
    // *StatsSystem needed in this wave.
    systems.register(RotationSystem.class, new RotationSystem());
    systems.register(SpeedSystem.class, new SpeedSystem());
    systems.register(ThrustSystem.class, new ThrustSystem());
    // Status family (ADR 0001 wave 3a) — Cloak/Stealth/XRadar/Antiwarp Continuous-
    // half writers. 2-level aspect (Continuous + Stats only; no Live pool), so
    // no *StatsSystem — ShipStatusProjector handles spawn-time projection.
    systems.register(CloakSystem.class, new CloakSystem());
    systems.register(StealthSystem.class, new StealthSystem());
    systems.register(XRadarSystem.class, new XRadarSystem());
    systems.register(AntiwarpSystem.class, new AntiwarpSystem());
    // Weapon levels (ADR 0001 wave 4a) — Bomb/Bullet/Mine/Burst Continuous-half
    // writers. 2-level aspect; ShipWeaponsProjector handles spawn-time.
    systems.register(BombSystem.class, new BombSystem());
    systems.register(BulletSystem.class, new BulletSystem());
    systems.register(MineSystem.class, new MineSystem());
    systems.register(BurstSystem.class, new BurstSystem());
    // Inventory (ADR 0001 wave 4b) — Brick/Decoy/Portal/Repel/Rocket/Thor
    // Continuous-half writers (count clamps at 0 below and *Stats.max above).
    // RepelCountSystem is split from the impulse-applying RepelSystem
    // registered with the physics stack (option-(b) per its class Javadoc).
    systems.register(infinity.systems.ship.BrickSystem.class, new infinity.systems.ship.BrickSystem());
    systems.register(infinity.systems.ship.DecoySystem.class, new infinity.systems.ship.DecoySystem());
    systems.register(infinity.systems.ship.PortalSystem.class, new infinity.systems.ship.PortalSystem());
    systems.register(infinity.systems.ship.RepelCountSystem.class, new infinity.systems.ship.RepelCountSystem());
    systems.register(infinity.systems.ship.RocketSystem.class, new infinity.systems.ship.RocketSystem());
    systems.register(infinity.systems.ship.ThorSystem.class, new infinity.systems.ship.ThorSystem());
    systems.register(infinity.systems.ship.GravBombSystem.class, new infinity.systems.ship.GravBombSystem());

    // DecaySystem registers AFTER the ADR 0001 writers above (see ordering comment).
    systems.addSystem(new DecaySystem());
    // Per-component reaper for Jitter (deadline-on-component, not Decay-driven).
    systems.addSystem(new infinity.systems.JitterReaperSystem());

    // We'll need the block set in order to have physics collision
    // information.  Eventually we'll want to do this differently... probably.
    if (!BlockTypeIndex.isInitialized()) {
      DefaultBlockSet.initializeBlockTypes();
      DefaultBlockSet.initializeFluidTypes();
    }

    // Expand BlockTypeIndex to accommodate tile types (100-289)
    BlockTypeExpander.expandBlockTypeIndex();

    // Set up the physics space
    ShapeFactoryRegistry<MBlockShape> shapeFactory = new ShapeFactoryRegistry<>();

    registerShapeFactories(shapeFactory);

    systems.register(ShapeFactory.class, shapeFactory);

    // And give that to an EntityBodyFactory where we can manage how bodies are created
    InfinityEntityBodyFactory bodyFactory =
        new InfinityEntityBodyFactory(ed, InfinityConstants.NO_GRAVITY, shapeFactory);

    MPhysSystem<MBlockShape> mBlockShapeMPhysSystem =
        new MPhysSystem<>(
            WorldGrids.LEAF_GRID, InfinityConstants.PHYSICS_BIN_RADIUS,
            WorldGrids.TILE_GRID, InfinityConstants.LARGE_BIN_RADIUS,
            bodyFactory);
    // Route LargeObject-tagged entities (e.g. arena ghost-cubes that span many fine bins)
    // to the coarse static-only index. Must be set before MPhysSystem.initialize() runs.
    mBlockShapeMPhysSystem.setLargeEntitySelector(
        id -> ed.getComponent(id, LargeObject.class) != null);
    systems.register(InfinityEntityBodyFactory.class, bodyFactory);
    systems.register(EntityBodyFactory.class, bodyFactory);

    // Create colliders, expanding for tile types (which have null/passthrough colliders)
    Collider[] baseColliders = new ColliderFactories(true).createColliders(BlockTypeIndex.getTypes());
    Collider[] colliders = BlockTypeExpander.expandCollidersForTiles(baseColliders);
    mBlockShapeMPhysSystem.setCollisionSystem(new MBlockCollisionSystem<>(world, colliders));

    systems.register(InfinityChatHostedService.class, chp);

    // Coarse large-static pass body filter: only CollidesWithLargeStatics-tagged
    // bodies (ships at spawn) generate pairs with arena ghost-cubes; everything
    // else skips before narrow phase. Per-frame contact-gen still scales with
    // (ships × loaded arenas); the next throttle if STAT_CONTACTS shows pressure
    // is a penetration discriminator (drop pairs with penetration < shipRadius).
    mBlockShapeMPhysSystem.getPhysicsSpace().setLargeStaticCollisionFilter(
        body -> ed.getComponent(body.id, CollidesWithLargeStatics.class) != null);

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
    // ContactSystem is the ADR-0003 Channel C dispatcher (see its class Javadoc).
    // The seven systems below register as ContactListeners from their initialize();
    // GameSystemManager initializes systems in registration order, so the order
    // they appear in this method is the order they fire on each contact:
    //   1. WeaponsImpactSystem    — projectile vs body / world; kill-credit
    //   2. ConsumableSystem       — Thor projectile-vs-body
    //   3. ArenaMembershipSystem  — ship enters/leaves arena ghost-cube
    //   4. PrizeConsumptionSystem — ship-vs-prize pickup (split from PrizeSystem
    //                               per P2-k; occupies the original PrizeSystem
    //                               slot for ordering)
    //   5. GravitySystem          — gravity-well wormhole proximity
    //   6. WarpSystem             — wormhole / warp-tile teleport
    //   7. FrequencySystem        — frequency change on touch
    // ORDERING CONTRACT: WeaponsImpactSystem MUST register BEFORE
    // PrizeConsumptionSystem so kill-credit logic runs before any prize-
    // consumption side-effects on the same contact frame (per ADR-0003 Channel
    // C bar item #2). Reordering breaks that silently — there is no runtime
    // check.
    // Game systems.
    systems.register(AvatarSystem.class, new AvatarSystem());
    systems.register(MovementInputSystem.class, new MovementInputSystem());
    systems.register(MobSystem.class, new MobSystem());
    // WeaponsReaperSystem must register BEFORE WeaponsImpactSystem — Impact.initialize() looks it up.
    // WeaponsFireSystem (546 lines) split into 3 Use-Case systems per P2-k:
    //   WeaponsFireEligibilitySystem    — energy/cooldown/level gate; emits
    //                                     FireRequest Channel A intent.
    //   WeaponsProjectileSpawnSystem    — drains FireRequest; calls
    //                                     WeaponFactory.create*.
    //   WeaponsFireAudioSystem          — drains FireRequest; calls GameSounds;
    //                                     LAST drainer destroys the holder.
    systems.register(WeaponsFireEligibilitySystem.class, new WeaponsFireEligibilitySystem());
    systems.register(WeaponsProjectileSpawnSystem.class, new WeaponsProjectileSpawnSystem());
    systems.register(WeaponsFireAudioSystem.class, new WeaponsFireAudioSystem());
    systems.register(WeaponsReaperSystem.class, new WeaponsReaperSystem());
    systems.register(WeaponsImpactSystem.class, new WeaponsImpactSystem());
    systems.register(ConsumableSystem.class, new ConsumableSystem());
    systems.register(RocketBuffSystem.class, new RocketBuffSystem());
    systems.register(StatusDrainSystem.class, new StatusDrainSystem());
    systems.register(ProximityFuseSystem.class, new ProximityFuseSystem());
    systems.register(RepelSystem.class, new RepelSystem());
    systems.register(ArenaSystem.class, new ArenaSystem());
    // ArenaCommandsSystem must register AFTER ArenaSystem — its initialize() looks it up.
    systems.register(ArenaCommandsSystem.class, new ArenaCommandsSystem());
    systems.register(ArenaMembershipSystem.class, new ArenaMembershipSystem());
    systems.register(RegionSystem.class, new RegionSystem());
    systems.register(ChecksShipsSystem.class, new ChecksShipsSystem());
    systems.register(ChecksWorldSystem.class, new ChecksWorldSystem());
    // PrizeSystem (805 lines) split into 3 Use-Case systems per P2-k:
    //   PrizeSpawnerSystem        — arena periodic spawning + cap-scaling.
    //   PrizeConsumptionSystem    — prize-on-contact dispatch (Contact listener,
    //                               registered AFTER WeaponsImpactSystem for
    //                               kill-credit-before-prize-consumption order
    //                               per ContactSystem class Javadoc).
    //   DeathPrizeSystem          — drains PrizeSpawnIntent Channel A holders
    //                               emitted by EnergySystem on the death-edge;
    //                               requires PrizeSpawnerSystem (shared per-arena
    //                               selector cache + negative-roll logic).
    systems.register(
        PrizeSpawnerSystem.class,
        new PrizeSpawnerSystem(mBlockShapeMPhysSystem.getPhysicsSpace()));
    systems.register(
        PrizeConsumptionSystem.class,
        new PrizeConsumptionSystem(mBlockShapeMPhysSystem.getPhysicsSpace()));
    systems.register(
        DeathPrizeSystem.class,
        new DeathPrizeSystem(mBlockShapeMPhysSystem.getPhysicsSpace()));
    systems.register(GravitySystem.class, new GravitySystem());
    systems.register(InfinityTimeSystem.class, new InfinityTimeSystem());

    final AssetLoaderService assetLoader = new AssetLoaderService();
    server.getServices().addService(assetLoader);
    systems.register(AssetLoaderService.class, assetLoader);

    systems.register(SettingsSystem.class, new SettingsSystem());
    systems.register(EngineConfigSystem.class, new EngineConfigSystem());
    final ConfigRegistrySystem configRegistry = new ConfigRegistrySystem();
    systems.register(ConfigRegistrySystem.class, configRegistry);
    systems.register(GroovyShipLoader.class, new GroovyShipLoader(configRegistry));
    systems.register(ShipSpawnSystem.class, new ShipSpawnSystem());
    systems.register(MapSystem.class, new MapSystem());
    systems.register(WarpSystem.class, new WarpSystem());
    systems.register(FrequencySystem.class, new FrequencySystem());

    systems.register(WorldSystem.class, new WorldSystem());

    systems.register(BasicEnvironment.class, new BasicEnvironment());

    // Add a system that will forward physics changes to the Ethereal
    // zone manager
    systems.register(
        ZoneNetworkSystem.class, new ZoneNetworkSystem<MBlockShape>(ethereal.getZones()));

    // And the system that will publish the BodyPosition components
    systems.addSystem(new BodyPositionPublisher<>());

    // Periodic server telemetry — fires logStats() every 10s.
    systems.register(ServerTelemetrySystem.class, new ServerTelemetrySystem(this));

    // Register some custom serializers
    registerSerializers();
  }

  @SuppressWarnings({"PMD.SignatureDeclareThrowsException", "PMD.AssignmentInOperand"})
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
          log.error("Unknown command:{}", line);
        }
      }
      gs.close();
    }
  }

  private void registerShapeFactories(final ShapeFactoryRegistry<MBlockShape> shapeFactory) {
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

  protected final void registerSerializers() {
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
    Serializer.registerClass(ArenaId.class, new FieldSerializer());
    Serializer.registerClass(ArenaMap.class, new FieldSerializer());
    Serializer.registerClass(RadarRange.class, new FieldSerializer());
    Serializer.registerClass(RadarShapeInfo.class, new FieldSerializer());
    Serializer.registerClass(ArenaFootprint.class, new FieldSerializer());
    // Client-visible components — required by per-component network sync; see components.md.
    Serializer.registerClass(MobType.class, new FieldSerializer());
    Serializer.registerClass(ProbeInfo.class, new FieldSerializer());
    Serializer.registerClass(Speech.class, new FieldSerializer());
    Serializer.registerClass(infinity.es.Hidden.class, new FieldSerializer());
    Serializer.registerClass(infinity.es.Jitter.class, new FieldSerializer());
  }

  public Server getServer() {
    return server;
  }

  public GameSystemManager getSystems() {
    return systems;
  }

  public void start() {
    log.info("Starting game server...");
    server.start();
    loop.start(true);
    log.info("Game server started.");
  }

  /** Kicks all clients, stops systems, terminates — not restartable. */
  public void close(final String kickMessage) {
    log.info("Stopping game server...{}", kickMessage);
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

  public void close() {
    close(null);
  }

  public void logStats() {

    final EtherealHost host = server.getServices().getService(EtherealHost.class);

    for (final HostedConnection conn : server.getConnections()) {
      if (log.isInfoEnabled()) {
        log.info(String.format("Client[%d] address:%s", conn.getId(), conn.getAddress()));
      }
      final NetworkStateListener listener = host.getStateListener(conn);
      if (listener == null) {
        if (log.isInfoEnabled()) {
          log.info(String.format("[%d] No stats", conn.getId()));
        }
        continue;
      }
      if (log.isInfoEnabled()) {
        log.info(
            String.format(
                "[%d] Ping time: %s ms",
                conn.getId(), listener.getConnectionStats().getAveragePingTime() / 1000000.0));
      }
      final String miss =
          String.format("%.02f", Double.valueOf(listener.getConnectionStats().getAckMissPercent()));
      if (log.isInfoEnabled()) {
        log.info(String.format("[%d] Ack miss: %s%%", conn.getId(), miss));
        log.info(
            String.format(
                "[%d] Average msg size: %d bytes",
                conn.getId(), listener.getConnectionStats().getAverageMessageSize()));
      }
    }
  }

  /** SpiderMonkey workaround: a per-connection sleep keeps RpcCall messages from sharing a buffer with SerializerRegistrationMessage. */
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
      // no-op: DelayService only reacts on connectionAdded; no init-time wiring needed.
    }

    @Override
    public void start() {
      // no-op: DelayService is purely event-driven via connectionAdded; nothing to start.
    }

    @Override
    public void connectionAdded(final Server server, final HostedConnection hc) {
      // Just in case
      super.connectionAdded(server, hc);
      log.debug("DelayService.connectionAdded({})", hc);
      safeSleep(500);
      log.debug("DelayService.delay done");
    }
  }
}
