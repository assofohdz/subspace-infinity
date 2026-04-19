---
name: physics-moss
description: Integrate Moss physics library for collision detection and physics simulation. Use when working with physics bodies, shapes, collisions, or forces.
---

# Physics with Moss

Moss is the custom physics library for Subspace Infinity.

## Building Moss
Moss must be built from source and published locally:
```bash
cd ~/github/assofohdz/moss
./gradlew publishToMavenLocal
```

## Key Concepts

### Shapes
Physics shapes define collision geometry:
- `AbstractShape` - base class
- Shapes are identified by `ShapeInfo` component

### Bodies
Physics bodies have:
- Position and rotation
- Linear/angular velocity
- Mass and inertia

### Contact System
The `ContactSystem` handles collision detection and response:
```java
public class ContactSystem<K, S extends AbstractShape> extends AbstractGameSystem
```

## Integration with ECS

### Position Synchronization
Physics positions sync to entity positions via components.

### Shape Assignment
```java
ed.setComponent(entityId, new ShapeInfo(shapeName, mass));
```

### Velocity
```java
ed.setComponent(entityId, new AttackVelocity(vx, vy, vz));
```

## Physics Constants
Defined in `CorePhysicsConstants`:
- Gravity settings
- Collision categories
- Update rates

## Common Patterns

### Creating Physics Entity
```java
EntityId entity = ed.createEntity();
ed.setComponents(entity,
    new Position(x, y, z),
    new ShapeInfo(ShapeNames.SHIP, mass),
    new Velocity(0, 0, 0)
);
```

### Applying Forces
Forces are typically applied by setting velocity components or through the physics system directly.
