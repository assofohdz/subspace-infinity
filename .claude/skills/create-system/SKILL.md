---
name: create-system
description: Create server-side game systems using SiO2 AbstractGameSystem. Use when building systems that process entities, handle game logic, or manage server-side state.
---

# Creating Game Systems (SiO2)

Server-side systems process entities each frame using the SiO2 framework.

## Location
`infinity/src/main/java/infinity/systems/`

## Requirements
1. Extend `com.simsilica.sim.AbstractGameSystem`
2. Get `EntityData` via `getSystem(EntityData.class)` in `initialize()`
3. Create `EntitySet` queries for needed components
4. **CRITICAL: Release all EntitySets in terminate()** - memory leak otherwise
5. Use `applyChanges()` pattern in `update()`
6. Include BSD 2-clause license header

## Template
```java
/*
 * Copyright (c) 2018, Asser Fahrenholz
 * All rights reserved.
 * [BSD 2-clause license...]
 */
package infinity.systems;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

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
        // MUST release EntitySets to prevent memory leaks
        entities.release();
        entities = null;
    }

    @Override
    public void update(final SimTime time) {
        // Apply changes first - returns true if set changed
        if (entities.applyChanges()) {
            // Handle newly added entities
            for (final Entity e : entities.getAddedEntities()) {
                handleAdded(e);
            }
            // Handle entities whose components changed
            for (final Entity e : entities.getChangedEntities()) {
                handleChanged(e);
            }
            // Handle removed entities
            for (final Entity e : entities.getRemovedEntities()) {
                handleRemoved(e);
            }
        }

        // Process all entities each frame if needed
        for (final Entity e : entities) {
            process(e, time);
        }
    }

    private void handleAdded(final Entity e) {
        // Entity just gained required components
    }

    private void handleChanged(final Entity e) {
        // Entity's component values changed
    }

    private void handleRemoved(final Entity e) {
        // Entity lost required components or was removed
    }

    private void process(final Entity e, final SimTime time) {
        // Per-frame processing
    }

    @Override
    public void start() {
        // Called when game starts
    }

    @Override
    public void stop() {
        // Called when game stops
    }
}
```

## Getting Other Systems
```java
OtherSystem other = getSystem(OtherSystem.class);
```

## Publishing Events
```java
import com.simsilica.event.EventBus;
EventBus.publish(EventType.MY_EVENT, new MyEvent(data));
```

## Existing Systems Reference
- `AvatarSystem` - player ship management
- `DeathSystem` - death/respawn handling
- `DelaySystem` - timed component removal
- `ContactSystem` - collision handling
