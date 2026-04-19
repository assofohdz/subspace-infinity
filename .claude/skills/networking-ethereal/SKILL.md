---
name: networking-ethereal
description: Implement multiplayer networking using SimEthereal for state synchronization. Use when working with client-server communication, entity sync, or network messages.
---

# Networking with SimEthereal

SimEthereal handles efficient network state synchronization between server and clients.

## Server Setup

### EtherealHost Configuration
```java
import com.simsilica.ethereal.EtherealHost;
import com.simsilica.ethereal.NetworkStateListener;

// Create the EtherealHost with protocol settings
EtherealHost ethereal = new EtherealHost(
    InfinityConstants.OBJECT_PROTOCOL,  // Protocol identifier
    InfinityConstants.ZONE_GRID,         // Zone grid configuration
    InfinityConstants.ZONE_RADIUS        // Zone visibility radius
);

// Enable large object support
ethereal.getZones().setSupportLargeObjects(true);

// Set time source from game systems
ethereal.setTimeSource(() -> systems.getStepTime().getUnlockedTime(System.nanoTime()));

// Add as service
server.getServices().addService(ethereal);
```

### ZoneNetworkSystem
Bridges physics updates to network zones:
```java
public class ZoneNetworkSystem<S extends AbstractShape> extends AbstractGameSystem {

    private final ZoneManager zones;
    private final PhysicsObserver physicsObserver = new PhysicsObserver();

    public ZoneNetworkSystem(ZoneManager zones) {
        this.zones = zones;
    }

    @Override
    protected void initialize() {
        MPhysSystem<S> system = getPhysicsSystem();
        system.addPhysicsListener(physicsObserver);
        system.getBinEntityManager().addObjectStatusListener(physicsObserver);
    }

    private class PhysicsObserver implements PhysicsListener<EntityId, S>, 
                                              ObjectStatusListener<S> {
        @Override
        public void startFrame(long frameTime, double stepSize) {
            zones.beginUpdate(frameTime);
        }

        @Override
        public void endFrame() {
            zones.endUpdate();
        }

        @Override
        public void update(RigidBody<EntityId, S> body) {
            boolean active = !body.isSleepy();
            zones.updateEntity(
                Long.valueOf(body.id.getId()), 
                active, 
                body.position, 
                body.orientation,
                body.getWorldBounds()
            );
        }
    }
}
```

### Getting NetworkStateListener
```java
EtherealHost host = server.getServices().getService(EtherealHost.class);
NetworkStateListener listener = host.getStateListener(conn);
```

## Client Setup

### EtherealClient Configuration
```java
import com.simsilica.ethereal.EtherealClient;
import com.simsilica.ethereal.TimeSource;

// Add EtherealClient as a client service
client.getServices().addServices(
    new RpcClientService(),
    new RmiClientService(),
    new EntityDataClientService(InfinityConstants.ES_CHANNEL),
    new EtherealClient(
        InfinityConstants.OBJECT_PROTOCOL,
        InfinityConstants.ZONE_GRID,
        InfinityConstants.ZONE_RADIUS
    ),
    new SharedObjectUpdater()
);

// Get synchronized time source
TimeSource timeSource = client.getServices()
    .getService(EtherealClient.class)
    .getTimeSource();
```

### ConnectionState Pattern
```java
public class ConnectionState extends BaseAppState {
    
    public TimeSource getTimeSource() {
        return getService(EtherealClient.class).getTimeSource();
    }
    
    public EntityData getEntityData() {
        return getService(EntityDataClientService.class).getEntityData();
    }
}
```

## Server Services

### Common Hosted Services
```java
server.getServices().addServices(
    new RpcHostedService(),
    new RmiHostedService(),
    new AccountHostedService(description),
    new GameSessionHostedService(systems),
    new EntityDataHostedService(InfinityConstants.ES_CHANNEL, ed),
    new ChatHostedService(InfinityConstants.CHAT_CHANNEL),
    new WorldHostedService(world, InfinityConstants.TERRAIN_CHANNEL)
);
```

### EntityUpdater System
Sends entity updates efficiently:
```java
// backgroundThread=true enables background processing
systems.addSystem(new EntityUpdater(
    server.getServices().getService(EntityDataHostedService.class), 
    true  // Use background thread
));
```

## Client Services

### Common Client Services
```java
client.getServices().addServices(
    new RpcClientService(),
    new RmiClientService(),
    new AccountClientService(),
    new GameSessionClientService(),
    new EntityDataClientService(InfinityConstants.ES_CHANNEL),
    new ChatClientService(InfinityConstants.CHAT_CHANNEL),
    new WorldClientService(InfinityConstants.TERRAIN_CHANNEL),
    new EtherealClient(protocol, zoneGrid, zoneRadius),
    new SharedObjectUpdater()
);
```

## Zone Architecture

### Zone Grid Configuration
```java
// In InfinityConstants
public static final int ZONE_GRID = 32;    // Grid cell size
public static final int ZONE_RADIUS = 2;   // Visibility radius in cells
```

### Zone Benefits
- Clients only receive updates for entities in their zone
- Reduces bandwidth for large game worlds
- Automatic interest management

## Input Handling

### Character Input Components
```java
// In infinity.es.input package
public class CharacterInput implements EntityComponent
public class MovementInput implements EntityComponent
```

### Server Processing Client Input
```java
EntitySet inputs = ed.getEntities(CharacterInput.class, Player.class);
if (inputs.applyChanges()) {
    for (Entity e : inputs) {
        CharacterInput input = e.get(CharacterInput.class);
        // Process input, update game state on server
    }
}
```

## State Synchronization Flow
1. Client sends input via components
2. Server processes input in game systems
3. Server updates entity state
4. MPhysSystem updates physics bodies
5. ZoneNetworkSystem captures position changes
6. EtherealHost packages and sends to relevant clients
7. EtherealClient receives and applies updates
8. Client-side systems visualize synchronized state
