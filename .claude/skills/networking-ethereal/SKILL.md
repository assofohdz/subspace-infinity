---
name: networking-ethereal
description: Implement multiplayer networking using SimEthereal for state synchronization. Use when working with client-server communication, entity sync, or network messages.
---

# Networking with SimEthereal

SimEthereal handles network synchronization between server and clients.

## Key Concepts

### Zone Network System
`ZoneNetworkSystem` manages networked entity state:
```java
public class ZoneNetworkSystem<S extends AbstractShape> extends AbstractGameSystem
```

### State Synchronization
- Server authoritative
- Clients receive entity state updates
- Position interpolation handled automatically

## Client Connection

### ConnectionState
Client-side state managing server connection:
```java
ConnectionState conn = getState(ConnectionState.class);
EntityData ed = conn.getEntityData();
```

### Shared EntityData
Both client and server use Zay-ES EntityData, but:
- Server has full EntityData
- Client has synchronized view

## Network Messages

### Custom Messages
Define message types for game-specific communication.

### Character Input
Player input is sent via `CharacterInput` component:
```java
// In infinity.es.input
public class CharacterInput implements EntityComponent
public class MovementInput implements EntityComponent
```

## Common Patterns

### Server Processing Client Input
```java
EntitySet inputs = ed.getEntities(CharacterInput.class, Player.class);
if (inputs.applyChanges()) {
    for (Entity e : inputs) {
        CharacterInput input = e.get(CharacterInput.class);
        // Process input, update game state
    }
}
```

### Sending Updates to Clients
Entity component changes on server automatically sync to clients via SimEthereal's zone system.

## Zone Architecture
- Zones define areas of interest
- Clients only receive updates for entities in their zone
- Reduces bandwidth for large game worlds
