---
name: debug-ecs
description: Debug Entity-Component-System issues including EntitySet problems, memory leaks, and component queries. Use when troubleshooting ECS bugs or entity processing issues.
---

# Debugging ECS Issues

Common Entity-Component-System problems and solutions.

## EntitySet Not Updating

**Symptom**: Entities added but not showing in set

**Checks**:
1. Is `applyChanges()` called before iterating?
```java
// WRONG
for (Entity e : entities) { }

// RIGHT
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
// Filter by field value
ComponentFilter<ShapeInfo> filter = 
    FieldFilter.create(ShapeInfo.class, "shapeName", ShapeNames.SHIP);

// Use filter in query
EntitySet ships = ed.getEntities(filter, ShapeInfo.class, Position.class);
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
```

## Entity Not Found

```java
// Get entity with specific components
Entity e = ed.getEntity(entityId, Component1.class, Component2.class);
if (e == null) {
    // Entity doesn't exist OR doesn't have those components
}

// Check if entity exists at all
EntityId id = ...;
// Any component check will tell you
if (ed.getComponent(id, SomeComponent.class) != null) {
    // Entity exists with that component
}
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
