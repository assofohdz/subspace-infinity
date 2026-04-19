---
name: debug-ecs
description: Debug Entity-Component-System issues including EntitySet problems, memory leaks, and component queries. Use when troubleshooting ECS bugs or entity processing issues.
---

# Debugging ECS Issues

Common Entity-Component-System problems and solutions from official Zay-ES patterns.

## Official Rules of Thumb
From the Zay-ES wiki:
1. **Components are data only** - no logic in components
2. **Two systems should not produce the same component type for the same entities**
3. **Immutable components help threading** - allows systems to run without synchronization

## EntitySet Not Updating

**Symptom**: Entities added but not showing in set

**Checks**:
1. Is `applyChanges()` called before iterating?
```java
// WRONG
for (Entity e : entities) { }

// RIGHT - call applyChanges first
entities.applyChanges();
for (Entity e : entities) { }
```

2. Are you querying correct components?
```java
// This only returns entities with BOTH components
entities = ed.getEntities(Component1.class, Component2.class);
```

3. Is the component actually set?
```java
// Debug: check if component exists
MyComponent c = ed.getComponent(entityId, MyComponent.class);
System.out.println("Component: " + c);
```

## The Visualization Pattern (from Wiki)
The standard pattern for syncing entities to visual spatials:
```java
public class VisualAppState extends AbstractAppState {
    private EntityData ed;
    private EntitySet entities;
    private final Map<EntityId, Spatial> models = new HashMap<>();

    @Override
    public void initialize(AppStateManager stateManager, Application app) {
        ed = getEntityData();
        entities = ed.getEntities(Position.class, Model.class);
    }

    @Override
    public void cleanup() {
        entities.release();  // CRITICAL!
        entities = null;
    }

    @Override
    public void update(float tpf) {
        if (entities.applyChanges()) {
            removeModels(entities.getRemovedEntities());
            addModels(entities.getAddedEntities());
            updateModels(entities.getChangedEntities());
        }
    }

    private void removeModels(Set<Entity> removed) {
        for (Entity e : removed) {
            Spatial s = models.remove(e.getId());
            s.removeFromParent();
        }
    }

    private void addModels(Set<Entity> added) {
        for (Entity e : added) {
            Spatial s = createVisual(e);
            models.put(e.getId(), s);
            updateModelSpatial(e, s);
            rootNode.attachChild(s);
        }
    }

    private void updateModels(Set<Entity> changed) {
        for (Entity e : changed) {
            Spatial s = models.get(e.getId());
            updateModelSpatial(e, s);
        }
    }
}
```

## Memory Leaks

**Symptom**: Growing memory, eventual OOM

**Cause**: EntitySets not released

```java
// WRONG - leak!
@Override
protected void terminate() {
    // forgot to release
}

// RIGHT
@Override
protected void terminate() {
    entities.release();
    entities = null;
}
```

## Component Filter Issues

```java
// Filter by field value using Filters class
import com.simsilica.es.Filters;

EntitySet ships = ed.getEntities(
    Filters.fieldEquals(Model.class, "name", Model.SHIP),
    Model.class,
    Position.class
);

// Or with FieldFilter directly
ComponentFilter<ShapeInfo> filter = 
    FieldFilter.create(ShapeInfo.class, "shapeName", ShapeNames.SHIP);
EntitySet filtered = ed.getEntities(filter, ShapeInfo.class, Position.class);
```

## Concurrent Modification

**Symptom**: ConcurrentModificationException or missed updates

**Cause**: Modifying entities during iteration

```java
// WRONG
for (Entity e : entities) {
    ed.removeEntity(e.getId()); // Modifies during iteration!
}

// RIGHT - collect first, then modify
List<EntityId> toRemove = new ArrayList<>();
for (Entity e : entities) {
    toRemove.add(e.getId());
}
for (EntityId id : toRemove) {
    ed.removeEntity(id);
}

// OR use streams with forEach
entities.stream().forEach(e -> {
    // Safe to modify here because forEach handles iteration
    ed.removeEntity(e.getId());
});
```

## The Decay Pattern (from Wiki)
Time-based entity removal:
```java
public class DecaySystem extends AbstractGameSystem {
    private EntitySet decays;

    @Override
    protected void initialize() {
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

## Entity Not Found

```java
// Get entity with specific components
Entity e = ed.getEntity(entityId, Component1.class, Component2.class);
if (e == null) {
    // Entity doesn't exist OR doesn't have those components
}

// Check if entity exists at all
if (ed.getComponent(id, SomeComponent.class) != null) {
    // Entity exists with that component
}
```

## Setting Multiple Components
```java
EntityId entity = ed.createEntity();
ed.setComponents(entity,
    new Position(new Vector3f(0, 0, 0)),
    new Model(Model.SHIP),
    new CollisionShape(1.0f),
    new Attack(10)
);
```

## Debugging Commands

```java
// Print all entities with a component
public void debugEntities() {
    EntitySet all = ed.getEntities(MyComponent.class);
    all.applyChanges();
    for (Entity e : all) {
        System.out.println(e.getId() + ": " + e.get(MyComponent.class));
    }
    all.release();
}
```
