---
name: moss-physics
description: Integrate Moss physics library for collision detection and physics simulation. Use when working with physics bodies, shapes, collisions, or forces.
---

# Physics with Moss

Moss is the custom physics library for Subspace Infinity, integrated via `sio2-mphys`.

## Building Moss
Moss must be built from source and published locally:
```bash
cd ~/github/assofohdz/moss
./gradlew publishToMavenLocal
```

## Core Classes

### MPhysSystem
The main physics system that manages the physics space:
```java
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;

// Get the physics system
MPhysSystem<MBlockShape> physics = (MPhysSystem<MBlockShape>) getSystem(MPhysSystem.class, true);

// Get the physics space for direct manipulation
PhysicsSpace<EntityId, MBlockShape> space = physics.getPhysicsSpace();
```

### RigidBody Access
```java
// Get rigid body from physics space
RigidBody<EntityId, MBlockShape> rb = physics.getPhysicsSpace()
    .getBinIndex()
    .getRigidBody(entityId);

// Get static body
StaticBody<EntityId, MBlockShape> sb = physics.getPhysicsSpace()
    .getBinIndex()
    .getStaticBody(entityId);
```

### Body Initializer
Register custom initialization for physics bodies:
```java
// In your system's initialize()
physics.getBodyFactory().addDynamicInitializer(new MyBodyInitializer());

// Initializer class
public class MyBodyInitializer implements BodyInitializer<EntityId, MBlockShape> {
    @Override
    public void initialize(RigidBody<EntityId, MBlockShape> body) {
        // Custom initialization
    }
}
```

## Contact System

### Implementing ContactListener
```java
public class ContactSystem<K, S extends AbstractShape> extends AbstractGameSystem
    implements ContactListener<EntityId, MBlockShape> {

    private MPhysSystem<?> physics;

    @Override
    protected void initialize() {
        physics = getSystem(MPhysSystem.class);
        physics.getPhysicsSpace().getContactDispatcher().addListener(this);
    }

    @Override
    public void newContact(Contact contact) {
        RigidBody<EntityId, MBlockShape> bodyOne = contact.body1;
        AbstractBody<EntityId, MBlockShape> bodyTwo = contact.body2;

        if (bodyTwo != null) {
            EntityId one = bodyOne.id;
            EntityId two = bodyTwo.id;
            
            // Filter contacts
            if (shouldDisable(one, two)) {
                contact.disable();
                return;
            }
        }
        
        // Set restitution for bouncing
        contact.restitution = 1.0f;

        // CAUTION — body-vs-static friction: do NOT set contact.friction on body-vs-static
        // contacts when the body is a sphere (ship). The resolver computes r × impulse torque
        // at the contact point, which wrongly rotates the ship heading. Instead, keep
        // contact.friction = 0.0 and damp body1.linearVelocity's tangential component
        // manually after the step. See ContactSystem for the reference implementation.
    }
}
```

### Category-Based Collision Filtering
```java
// Use CollisionCategory component with CategoryFilter
CollisionCategory category = ed.getComponent(entityId, CollisionCategory.class);
CategoryFilter filter = category.getFilter();

// Check if collision allowed
if (!filterTwo.isAllowed(filterOne)) {
    contact.disable();
}
```

## Physics Listeners

### Registering Listeners
```java
MPhysSystem<S> system = getPhysicsSystem();
system.addPhysicsListener(myPhysicsObserver);
system.getBinEntityManager().addObjectStatusListener(myStatusListener);
```

### PhysicsListener Interface
```java
public class PhysicsObserver implements PhysicsListener<EntityId, S>, 
                                         ObjectStatusListener<S> {
    @Override
    public void startFrame(long frameTime, double stepSize) {
        // Called at start of physics frame
    }

    @Override
    public void endFrame() {
        // Called at end of physics frame
    }

    @Override
    public void update(RigidBody<EntityId, S> body) {
        // Called when body position updates
        boolean active = !body.isSleepy();
        Vec3d position = body.position;
        Quatd orientation = body.orientation;
    }

    @Override
    public void objectLoaded(EntityId id, RigidBody<EntityId, S> body) {
        // Body became active
    }

    @Override
    public void objectUnloaded(EntityId id, RigidBody<EntityId, S> body) {
        // Body deactivated
    }
}
```

## Movement and Drivers

### MobDriver Pattern
For AI-controlled physics entities:
```java
public class MobDriver implements ControlDriver<MBlockShape> {
    private final MPhysSystem<MBlockShape> physics;
    private final EntityId mob;
    
    public MobDriver(MPhysSystem<MBlockShape> physics, EntityId mob) {
        this.physics = physics;
        this.mob = mob;
    }
    
    // Implement control methods for steering
}
```

### Movement Settings
```java
public class MovementSettings {
    public float groundImpulse = 40;
    public float airImpulse = 25;
    public float movementSpeed = 2;
}
```

## Shape Factory

### Registering Shape Factories
```java
ShapeFactoryRegistry<MBlockShape> shapeFactory = new ShapeFactoryRegistry<>();
registerShapeFactories(shapeFactory, ed);
systems.register(ShapeFactory.class, shapeFactory);
```

## Integration with Zay-ES

### Physics Components
- `ShapeInfo` - defines shape and mass
- `CollisionCategory` - collision filtering
- `Parent` - parent-child relationships (skip collisions)
- `AttackVelocity` - projectile velocities

### Creating Physics Entity
```java
EntityId entity = ed.createEntity();
ed.setComponents(entity,
    new Position(x, y, z),
    new ShapeInfo(ShapeNames.SHIP, mass),
    new CollisionCategory(CategoryFilter.PLAYER)
);
```
