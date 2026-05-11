---
name: sio2-system
description: Create server-side game systems using SiO2 AbstractGameSystem (via the project's BaseInfinitySystem wrapper). Use when building systems that process entities, handle game logic, or manage server-side state.
---

# Creating Game Systems (SiO2)

Server-side systems process entities each frame using the SiO2 framework. The project wraps SiO2's `AbstractGameSystem` with `infinity.systems.BaseInfinitySystem`, which adds a `requireSystem(Class<T>)` helper — a non-null lookup that throws `IllegalStateException` with a greppable message if the dependency isn't registered. Use it for mandatory deps; fall back to raw `getSystem(...)` only for optional deps that legitimately may be absent (test fixtures, minimal harnesses).

## Location
`infinity-server/src/main/java/infinity/systems/`

## Official Zay-ES Rules of Thumb
From the wiki:
1. **Components are data only** - systems contain the logic
2. **Two systems should not produce the same component type for the same entities** (project further restricts via Replacement-as-Mutation — one canonical writer per component type, see `.claude/rules/replacement-as-mutation.md`)
3. You're not forced into a particular 'system' model - query entities when you want them

## Requirements
1. Extend `infinity.systems.BaseInfinitySystem` (which extends `com.simsilica.sim.AbstractGameSystem`)
2. Get `EntityData` via `requireSystem(EntityData.class)` in `initialize()`
3. Create `EntitySet` queries for needed components
4. **CRITICAL: Release all EntitySets in terminate()** - memory leak otherwise
5. Use `applyChanges()` pattern in `update()`
6. Include SPDX-only `BSD-3-Clause` license header

## Template
```java
// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.systems.BaseInfinitySystem;

public class MySystem extends BaseInfinitySystem {

    private EntityData ed;
    private EntitySet entities;

    @Override
    protected void initialize() {
        ed = requireSystem(EntityData.class);
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

## Common System Patterns (from Wiki)

### Decay System Pattern
Time-based entity removal:
```java
public class DecaySystem extends BaseInfinitySystem {
    private EntityData ed;
    private EntitySet decays;

    @Override
    protected void initialize() {
        ed = requireSystem(EntityData.class);
        decays = ed.getEntities(Decay.class);
    }

    @Override
    protected void terminate() {
        decays.release();
        decays = null;
    }

    @Override
    public void update(SimTime time) {
        decays.applyChanges();
        for (Entity e : decays) {
            Decay decay = e.get(Decay.class);
            if (decay.getPercent() >= 1.0) {
                ed.removeEntity(e.getId());
            }
        }
    }
}
```

### Movement System Pattern
```java
@Override
public void update(SimTime time) {
    bullets.applyChanges();
    for (Entity e : bullets) {
        Position position = e.get(Position.class);
        Speed speed = e.get(Speed.class);
        // Components are immutable - create new one
        e.set(new Position(
            position.getLocation().add(0, time.getTpf() * speed.getSpeed(), 0)
        ));
    }
}
```

### Collision System Pattern
```java
public class CollisionSystem extends BaseInfinitySystem {
    private EntitySet attackers;
    private EntitySet defenders;

    @Override
    protected void initialize() {
        ed = requireSystem(EntityData.class);
        attackers = ed.getEntities(Attack.class, CollisionShape.class, Position.class);
        defenders = ed.getEntities(Defense.class, CollisionShape.class, Position.class);
    }

    @Override
    public void update(SimTime time) {
        attackers.applyChanges();
        defenders.applyChanges();
        
        for (Entity attacker : attackers) {
            for (Entity defender : defenders) {
                if (hasCollision(attacker, defender)) {
                    handleCollision(attacker, defender);
                }
            }
        }
    }
}
```

## Getting Other Systems
```java
// Mandatory dependency — throws IllegalStateException with a greppable
// message if the dep isn't registered (preferred for everything that
// must be present for the system to function).
OtherSystem other = requireSystem(OtherSystem.class);

// Optional dependency — returns null when absent. Use only when the
// system has a legitimate null-handling path (test fixtures, minimal
// harnesses, lazy lookups guarded by `if (other != null)`).
OtherSystem maybeOther = getSystem(OtherSystem.class);
```

## Publishing Events
```java
import com.simsilica.event.EventBus;
EventBus.publish(EventType.MY_EVENT, new MyEvent(data));
```

## Using Filters
```java
import com.simsilica.es.Filters;

// Filter entities by component field value
EntitySet ships = ed.getEntities(
    Filters.fieldEquals(Model.class, "name", Model.SHIP),
    Model.class,
    Position.class
);
```

## Existing Systems Reference
- `AvatarSystem` - player ship management
- `DeathSystem` - death/respawn handling
- `DelaySystem` - timed component removal
- `ContactSystem` - collision handling
