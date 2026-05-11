# Subspace Infinity - Copilot Instructions

## Project Overview
Subspace Infinity is a JMonkeyEngine 3 game using an Entity-Component-System architecture. It's a modular multiplayer game server/client.

## Technology Stack
- **JMonkeyEngine 3** - 3D game engine
- **Zay-ES** - Entity Component System (`com.simsilica.es`)
- **SiO2** - Game systems framework (`com.simsilica.sim`)
- **SimEthereal** - Networking library
- **Lemur** - UI framework
- **Moss** - Physics library (custom, built from source)
- **Gradle** - Build system
- **Java 21** - Runtime (targets Java 17+ bytecode)

## Project Structure
- `api/` - Shared interfaces, components, and types
- `infinity/` - Main game client/server application
- `modules/` - Extension modules
- `libs/m2/` - Local Maven repository for dependencies

## Coding Patterns

### Components (Zay-ES)
Components are immutable data holders:
```java
package infinity.es;

import com.simsilica.es.EntityComponent;

public class MyComponent implements EntityComponent {
    private final int value;
    
    public MyComponent() { } // Required no-arg constructor
    
    public MyComponent(int value) {
        this.value = value;
    }
    
    public int getValue() { return value; }
}
```

### Game Systems (SiO2)
Server-side systems extend `AbstractGameSystem`:
```java
public class MySystem extends AbstractGameSystem {
    private EntityData ed;
    private EntitySet entities;
    
    @Override
    protected void initialize() {
        ed = getSystem(EntityData.class);
        entities = ed.getEntities(Component1.class, Component2.class);
    }
    
    @Override
    protected void terminate() {
        entities.release();
        entities = null;
    }
    
    @Override
    public void update(SimTime time) {
        if (entities.applyChanges()) {
            for (Entity e : entities.getAddedEntities()) { /* handle */ }
            for (Entity e : entities.getChangedEntities()) { /* handle */ }
            for (Entity e : entities.getRemovedEntities()) { /* handle */ }
        }
    }
    
    @Override public void start() { }
    @Override public void stop() { }
}
```

### App States (jME3)
Client-side states extend `BaseAppState`:
```java
public class MyState extends BaseAppState {
    @Override protected void initialize(Application app) { }
    @Override protected void cleanup(Application app) { }
    @Override protected void onEnable() { }
    @Override protected void onDisable() { }
}
```

### Module Pattern
Game modules extend `BaseGameModule` (from api):
```java
public class MyModule extends BaseGameModule {
    // Module-specific initialization
}
```

## Key Conventions
- Components go in `infinity.es` package
- Systems go in `infinity.systems` package  
- Client states go in `infinity` package
- Use SPDX-only `BSD-3-Clause` license header on all source files (`// SPDX-License-Identifier: BSD-3-Clause` then `// Copyright (c) 2018-2026 Asser Fahrenholz`)
- Use `final` for method parameters
- EntitySets MUST be released in `terminate()`
- Components should be immutable when possible
- Use `EventBus` for cross-system communication

## Build Commands
- `./gradlew build` - Build all modules
- `./gradlew :infinity-client:run` - Run the game
- `./gradlew clean` - Clean build outputs

## Dependencies
Moss physics library must be built from source:
```bash
cd ~/github/assofohdz/moss
./gradlew publishToMavenLocal
```
