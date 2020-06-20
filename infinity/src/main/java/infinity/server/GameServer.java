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

import java.io.*;

import org.slf4j.*;

import com.jme3.network.HostedConnection;
import com.jme3.network.Network;
import com.jme3.network.Server;
import com.jme3.network.serializing.Serializer;
import com.jme3.network.serializing.serializers.FieldSerializer;
import com.jme3.network.service.AbstractHostedService;
import com.jme3.network.service.HostedServiceManager;
import com.jme3.network.service.rmi.RmiHostedService;
import com.jme3.network.service.rpc.RpcHostedService;

import com.simsilica.ethereal.EtherealHost;
import com.simsilica.ethereal.NetworkStateListener;
import com.simsilica.ethereal.TimeSource;

import com.simsilica.es.*;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.es.server.EntityDataHostedService;
import com.simsilica.es.server.EntityUpdater; // from SiO2

import com.simsilica.es.common.*;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.GameLoop;
import com.simsilica.sim.GameSystemManager;
import com.simsilica.sim.common.*;

import com.simsilica.ext.mblock.*;
import com.simsilica.ext.mphys.*;
import com.simsilica.mblock.phys.*;
import com.simsilica.mphys.*;
import com.simsilica.mworld.*;
import com.simsilica.mworld.base.DefaultWorld;
import com.simsilica.mworld.db.*;

import com.simsilica.mworld.net.server.WorldHostedService;
import infinity.InfinityConstants;
import infinity.es.AudioType;
import infinity.es.BodyPosition;
import infinity.es.Flag;
import infinity.es.Frequency;
import infinity.es.Gold;
import infinity.es.LargeGridCell;
import infinity.es.LargeObject;
import infinity.es.MovementInput;
import infinity.es.Parent;
import infinity.es.PointLightComponent;
import infinity.es.TileType;
import infinity.es.ship.Player;
import infinity.es.ship.ShipType;
import infinity.server.chat.ChatHostedService;
//import infinity.sim.InfinityMPhysSystem;
import infinity.sim.PlayerDriver;
import infinity.systems.HealthSystem;
import infinity.systems.MovementSystem;
import infinity.systems.SettingsSystem;
import infinity.systems.ShipFrequencySystem;
import infinity.util.AdaptiveLoadingService;
//import infinity.systems.WeaponSystem;
import java.util.HashMap;

//import com.simsilica.sb.ai.*;
//import com.simsilica.sb.ai.steer.*;
//import com.simsilica.sb.es.*;
//import com.simsilica.sb.nav.NavGraph;
//import com.simsilica.sb.sim.*;
/**
 * The main GameServer that manages the back end game services, hosts
 * connections, etc..
 *
 * @author Paul Speed
 */
public class GameServer {

    static Logger log = LoggerFactory.getLogger(GameServer.class);

    private Server server;
    private GameSystemManager systems;
    private GameLoop loop;

    private String description;

    public GameServer(int port, String description) throws IOException {
        this.description = description;

        // Make sure we are running with a fresh serializer registry
        Serializer.initialize();

        this.systems = new GameSystemManager();
        this.loop = new GameLoop(systems);

        // Create the SpiderMonkey server and setup our standard
        // initial hosted services 
        this.server = Network.createServer(InfinityConstants.NAME,
                InfinityConstants.PROTOCOL_VERSION,
                port, port);

        // Create a separate channel to do chat stuff so it doesn't interfere
        // with any real game stuff.
        server.addChannel(port + 1);

        // And a separate channel for ES stuff
        server.addChannel(port + 2);

        // And a separate channel for terrain stuff
        server.addChannel(port + 3);

        // Adding a delay for the connectionAdded right after the serializer registration
        // service gets to run let's the client get a small break in the buffer that should
        // generally prevent the RpcCall messages from coming too quickly and getting processed
        // before the SerializerRegistrationMessage has had a chance to process.
        server.getServices().addService(new DelayService());

        ChatHostedService chp = new ChatHostedService(InfinityConstants.CHAT_CHANNEL);

        server.getServices().addServices(new RpcHostedService(),
                new RmiHostedService(),
                //new GameSessionHostedService(systems),
                //new AccountHostedService(description),
                //new WorldHostedService(DemoConstants.TERRAIN_CHANNEL),
                chp
        );

        // Add the SimEtheral host that will serve object sync updates to
        // the clients. 
        EtherealHost ethereal = new EtherealHost(InfinityConstants.OBJECT_PROTOCOL,
                InfinityConstants.ZONE_GRID,
                InfinityConstants.ZONE_RADIUS);
        ethereal.getZones().setSupportLargeObjects(true);
        ethereal.setTimeSource(new TimeSource() {
            @Override
            public long getTime() {
                return systems.getStepTime().getUnlockedTime(System.nanoTime());
            }
        });
        server.getServices().addService(ethereal);

        // Setup our entity data and the hosting service
        // Make the EntityData available to other systems
        final DefaultEntityData ed = new DefaultEntityData();
        systems.register(EntityData.class, ed);
        server.getServices().addService(new EntityDataHostedService(InfinityConstants.ES_CHANNEL, ed));

        // Just create a test world for now
        //LeafDb leafDb = new LeafDbCache(new TestLeafDb()); 
        LeafDb leafDb = new LeafDbCache(new EmptyLeafDb());

        World world = new DefaultWorld(leafDb);
        systems.register(World.class, world);
        server.getServices().addService(new WorldHostedService(world, InfinityConstants.TERRAIN_CHANNEL));

        // Add the game session service last so that it has access to everything else
        server.getServices().addService(new GameSessionHostedService(systems));

        systems.addSystem(new LargeGridIndexSystem());

        // Add it to the game systems so that we send updates properly
        systems.addSystem(new EntityUpdater(server.getServices().getService(EntityDataHostedService.class)));

        // Add some standard systems
        systems.addSystem(new DecaySystem());

        // Need a shape factory to turn ShapeInfo components into 
        // MBlockShapes. 
        ShapeFactoryRegistry<MBlockShape> shapeFactory = new ShapeFactoryRegistry<>();
        shapeFactory.registerFactory(ShapeInfo.create("sphere", 1, ed), new SphereFactory());

        shapeFactory.registerFactory(ShapeInfo.create("warbird", 1, ed), new SphereFactory());
        shapeFactory.registerFactory(ShapeInfo.create("javelin", 1, ed), new SphereFactory());
        shapeFactory.registerFactory(ShapeInfo.create("spider", 1, ed), new SphereFactory());
        shapeFactory.registerFactory(ShapeInfo.create("leviathan", 1, ed), new SphereFactory());
        shapeFactory.registerFactory(ShapeInfo.create("terrier", 1, ed), new SphereFactory());
        shapeFactory.registerFactory(ShapeInfo.create("lancaster", 1, ed), new SphereFactory());
        shapeFactory.registerFactory(ShapeInfo.create("weasel", 1, ed), new SphereFactory());
        shapeFactory.registerFactory(ShapeInfo.create("shark", 1, ed), new SphereFactory());

        shapeFactory.setDefaultFactory(new BlocksResourceShapeFactory(ed));
        systems.register(ShapeFactory.class, shapeFactory);

        // And give that to an EntityBodyFactory, for the moment without any
        // customization
        // Override the default body factory behavior so that we can apply the
        // upright driver as needed.
        HashMap<EntityId, PlayerDriver> map = new HashMap<>();
        /*
        EntityBodyFactory<MBlockShape> bodyFactory = new EntityBodyFactory<MBlockShape>(ed,
                InfinityConstants.DEFAULT_GRAVITY, shapeFactory) {
            @Override
            protected RigidBody<EntityId, MBlockShape> createRigidBody(EntityId id,
                    SpawnPosition pos,
                    ShapeInfo info,
                    Mass mass,
                    Gravity gravity) {
                RigidBody<EntityId, MBlockShape> result = super.createRigidBody(id, pos, info, mass, gravity);

                //If the entity is a player, we add a driver, so the player can control his ship
                if (getComponent(id, Player.class) != null) {
                    PlayerDriver driver = new PlayerDriver<EntityId, MBlockShape>();
                    result.setControlDriver(driver);
                    map.put(id, driver);
                }
                return result;
            }
        };*/
        EntityBodyFactory<MBlockShape> bodyFactory = new EntityBodyFactory<MBlockShape>(ed, Gravity.ZERO.getLinearAcceleration(), shapeFactory);

        MPhysSystem mphys = new MPhysSystem<MBlockShape>(InfinityConstants.PHYSICS_GRID, bodyFactory);

        //mphys.setDriverIndex(map);

        mphys.setCollisionSystem(new MBlockCollisionSystem<EntityId>(leafDb));

        //mphys.addPhysicsListener(new PositionUpdater(ed));
        //systems.register(InfinityMPhysSystem.class, mphys);
        systems.register(MPhysSystem.class, mphys);
        systems.register(PhysicsSpace.class, mphys.getPhysicsSpace());
        systems.register(EntityBodyFactory.class, bodyFactory);

        //Subspace Infinity Specific Systems:-->
        //systems.register(WeaponSystem.class, new WeaponSystem());
        systems.register(HealthSystem.class, new HealthSystem());
        systems.register(ShipFrequencySystem.class, new ShipFrequencySystem(chp));
        systems.register(MovementSystem.class, new MovementSystem());

        AssetLoaderService assetLoader = new AssetLoaderService();
        //systems.register(AssetLoaderService.class, assetLoader);

        AdaptiveLoadingService adaptiveLoader = new AdaptiveLoadingService(systems);
        //systems.register(AdaptiveLoadingService.class, adaptiveLoader);
        server.getServices().addService(assetLoader);
        systems.register(SettingsSystem.class, new SettingsSystem(assetLoader, adaptiveLoader));
        //<--

        // The physics system will need some way to load physics collision shapes
        //CollisionShapes shapes = systems.register(CollisionShapes.class, 
        //                                          new DefaultCollisionShapes(ed));
        //BulletSystem bullet = new BulletSystem();
        ////bullet.addPhysicsObjectListener(new PositionPublisher(ed));
        ////bullet.addPhysicsObjectListener(new DebugPhysicsListener(ed));
        //bullet.addEntityCollisionListener(new DefaultContactPublisher(ed) {
        //        /**
        //         *  Overridden to give some extra contact decay time so the
        //         *  debug visualization always has a chance to see them. 
        //         */
        //        @Override
        //        protected EntityId createEntity( Contact c ) {
        //            EntityId result = ed.createEntity();
        //            ed.setComponents(result, c,
        //                             Decay.duration(systems.getStepTime().getTime(),
        //                                            systems.getStepTime().toSimTime(0.1))
        //                             );
        //            return result;                
        //        }               
        //    });
        //systems.register(BulletSystem.class, bullet);
        //systems.register(Names.class, new DefaultNames());
        //systems.register(BasicEnvironment.class, new BasicEnvironment());
        //systems.register(GameEntities.class, new GameEntities());
//systems.register(MovementSystem.class, new MovementSystem());
        //systems.register(BehaviorSystem.class, new BehaviorSystem());
        //systems.register(SteeringSystem.class, new SteeringSystem());
        //systems.addSystem(new RoomContainmentSystem());
        //systems.register(PickingSystem.class, new PickingSystem());
        //systems.addSystem(new ActivationSystem());
        //systems.addSystem(new TrackSystem());
        //systems.addSystem(new SpawnSystem());
        // Add any hosted services that require those systems to already
        // exist
        // Add a system that will forward physics changes to the Ethereal 
        // zone manager       
        systems.addSystem(new ZoneNetworkSystem(ethereal.getZones()));

        // And the system that will publish the BodyPosition components 
        systems.addSystem(new BodyPositionPublisher());

        // Register some custom serializers
        registerSerializers();

        //NavGraph navGraph = new NavGraph();
        //systems.register(NavGraph.class, navGraph);
        //
        //// Add a system for creating the basic "world" entities
        ////systems.addSystem(new BasicEnvironment());
        //GameLevelSystem levels = systems.register(GameLevelSystem.class, new GameLevelSystem());
        //File levelDefFile = new File("samples/base.leveldef");
        //File levelFile = new File("samples/test1.level");
        //if( !levelDefFile.exists() ) {
        //    levelDefFile = new File("../samples/base.leveldef");
        //    levelFile = new File("../samples/test1.level"); 
        //} 
        //levels.setLevelDef(levelDefFile);
        //levels.setLevelFile(levelFile);
        //log.info("Initializing game systems...");
        // Initialize the game system manager to prepare to start later
        //systems.initialize();        
    }

    protected void registerSerializers() {
        //Serializer.registerClass(Name.class, new FieldSerializer());

        Serializer.registerClass(SpawnPosition.class, new FieldSerializer());
        Serializer.registerClass(BodyPosition.class, new FieldSerializer());
        Serializer.registerClass(ShapeInfo.class, new FieldSerializer());
        Serializer.registerClass(Mass.class, new FieldSerializer());
        //Serializer.registerClass(ObjectType.class, new FieldSerializer());
        //Serializer.registerClass(Position.class, new FieldSerializer());
        //Serializer.registerClass(ModelInfo.class, new FieldSerializer());
        //Serializer.registerClass(SphereShape.class, new FieldSerializer());
        Serializer.registerClass(Quatd.class, new FieldSerializer());
        Serializer.registerClass(Vec3d.class, new FieldSerializer());

        Serializer.registerClass(LargeObject.class, new FieldSerializer());
        Serializer.registerClass(LargeGridCell.class, new FieldSerializer());

        //Serializer.registerClass(Parent.class, new FieldSerializer());
        //Serializer.registerClass(Mobility.class, new FieldSerializer());
        //Serializer.registerClass(CharacterAction.class, new FieldSerializer());
        //Serializer.registerClass(LitBy.class, new FieldSerializer());
        //Serializer.registerClass(OverheadLight.class, new FieldSerializer());*/
        Serializer.registerClass(Name.class, new FieldSerializer());

        Serializer.registerClass(ShipType.class, new FieldSerializer());
        Serializer.registerClass(Frequency.class, new FieldSerializer());
        Serializer.registerClass(Flag.class, new FieldSerializer());
        Serializer.registerClass(Gold.class, new FieldSerializer());
        Serializer.registerClass(AudioType.class, new FieldSerializer());
        Serializer.registerClass(Parent.class, new FieldSerializer());
        Serializer.registerClass(TileType.class, new FieldSerializer());
        Serializer.registerClass(PointLightComponent.class, new FieldSerializer());
        Serializer.registerClass(Decay.class, new FieldSerializer());
        Serializer.registerClass(MovementInput.class, new FieldSerializer());
    }

    public Server getServer() {
        return server;
    }

    public GameSystemManager getSystems() {
        return systems;
    }

    /**
     * Starts the systems and begins accepting remote connections.
     */
    public void start() {
        log.info("Starting game server...");
        //systems.start();
        server.start();
        loop.start();
        log.info("Game server started.");
    }

    /**
     * Kicks all current connection, closes the network host, stops all systems,
     * and finally terminates them. The GameServer is not restartable at this
     * point.
     */
    public void close(String kickMessage) {
        log.info("Stopping game server..." + kickMessage);
        loop.stop();

        if (kickMessage != null) {
            for (HostedConnection conn : server.getConnections()) {
                conn.close(kickMessage);
            }
        }
        server.close();

        // The GameLoop dying should have already stopped the game systems
        if (systems.isInitialized()) {
            systems.stop();
            systems.terminate();
        }
        log.info("Game server stopped.");
    }

    /**
     * Closes the network host, stops all systems, and finally terminates them.
     * The GameServer is not restartable at this point.
     */
    public void close() {
        close(null);
    }

    /**
     * Logs the current connection statistics for each connection.
     */
    public void logStats() {

        EtherealHost host = server.getServices().getService(EtherealHost.class);

        for (HostedConnection conn : server.getConnections()) {
            log.info("Client[" + conn.getId() + "] address:" + conn.getAddress());
            NetworkStateListener listener = host.getStateListener(conn);
            if (listener == null) {
                log.info("[" + conn.getId() + "] No stats");
                continue;
            }
            log.info("[" + conn.getId() + "] Ping time: " + (listener.getConnectionStats().getAveragePingTime() / 1000000.0) + " ms");
            String miss = String.format("%.02f", listener.getConnectionStats().getAckMissPercent());
            log.info("[" + conn.getId() + "] Ack miss: " + miss + "%");
            log.info("[" + conn.getId() + "] Average msg size: " + listener.getConnectionStats().getAverageMessageSize() + " bytes");
        }
    }

    /**
     * Allow running a basic dedicated server from the command line using the
     * default port. If we want something more advanced then we should break it
     * into a separate class with a proper shell and so on.
     */
    public static void main(String... args) throws Exception {

        StringWriter sOut = new StringWriter();
        PrintWriter out = new PrintWriter(sOut);
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

        out.close();
        String desc = sOut.toString();

        GameServer gs = new GameServer(InfinityConstants.DEFAULT_PORT, desc);
        gs.start();

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
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
                System.err.println("Unknown command:" + line);
            }
        }

        gs.close();
    }

    /**
     * This works around a limitation in SpiderMonkey that can cause problems
     * for the SerializationRegistryService if there are other messages in the
     * same buffer as the registry update call. This adds a slight delay to
     * connections in the hopes that the messages will end up in separate
     * buffers.
     */
    private class DelayService extends AbstractHostedService {

        private void safeSleep(long ms) {
            try {
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                throw new RuntimeException("Checked exceptions are lame", e);
            }
        }

        @Override
        protected void onInitialize(HostedServiceManager serviceManager) {
        }

        @Override
        public void start() {
        }

        @Override
        public void connectionAdded(Server server, HostedConnection hc) {
            // Just in case
            super.connectionAdded(server, hc);
            log.debug("DelayService.connectionAdded(" + hc + ")");
            safeSleep(500);
            log.debug("DelayService.delay done");
        }
    }
}
