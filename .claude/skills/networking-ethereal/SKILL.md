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

## Avatar Position: Single Source of Truth

The client has a fixed camera at `(0, distance, 0)` — the "world moves around the player" illusion is achieved by translating `viewRoot` nodes in `ModelViewState` and `LocalViewState` by `-(avatarPos - centerWorld)`. The avatar ship spatial is rendered as a child of that same `viewRoot` at `(interpolatedPos - centerWorld)`, so its on-screen position reduces to `interpolatedPos - avatarPos`. **When those two positions diverge by even a fraction each frame, the avatar visibly wobbles (microstutter).**

### Two position sources exist — don't mix them
| Source | Where it comes from | Timing | Smoothness |
|--------|--------------------|--------|------------|
| **A. RMI** | `session.getPlayerLocation()` (synchronous RMI) | Server authoritative, polled each frame | Network-jittered, uninterpolated |
| **B. SimEthereal `BodyPosition`** | `bodyPos.getFrame(timeSource.getTime()).getPosition(t, true)` | ~100 ms look-back, buffered over 12 frames | Smoothly interpolated |

Source B must drive **both** the `viewRoot` translation and the avatar ship spatial — otherwise they desynchronize.

### Current implementation (fixed 2026-04-22)
`AvatarMovementState` feeds `posHolder` from Source B via `getInterpolatedAvatarPosition()`, falling back to Source A only until the SimEthereal buffer is populated. Downstream consumers (`ModelViewState`, `LocalViewState`, anything else reading `posRef`) automatically see the interpolated position without modification.

Key code: `AvatarMovementState.getInterpolatedAvatarPosition()` — watches the avatar entity for `BodyPosition`, initializes the shared 12-frame buffer, and reads `getFrame(timeSource.getTime()).getPosition(t, true)` each tick.

### Regression signal
If avatar microstutter returns, first check whether anything re-introduced Source A into the viewRoot translation path:
- `posHolder` being set from `session.getPlayerLocation()` without going through interpolation
- A second `VersionedHolder<Vec3d>` driving `viewRoot` from a different source
- `timeSource` or `ed` becoming null in `AvatarMovementState.initialize()` (would silently fall through to the RMI fallback)

The ship spatial itself (in `ModelViewState.Body.update`) already uses Source B — the regression is almost always on the viewRoot side.

### Why not use Source A everywhere?
RMI-polled positions are uninterpolated and subject to network jitter. Other ships (non-local) only exist as SimEthereal-synced `BodyPosition` components — they can't use RMI. Unifying on Source B means the local avatar follows the same smoothing pipeline as everyone else.
