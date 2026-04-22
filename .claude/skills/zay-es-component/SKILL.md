---
name: zay-es-component
description: Create Zay-ES EntityComponent classes for the ECS architecture. Use when creating new components, data holders, or entity attributes.
---

# Creating Zay-ES Components

Components are immutable data containers for the Entity-Component-System.

## Location
`api/src/infinity/es/`

## Official Zay-ES Rules of Thumb
From the official wiki:
1. **Components are data only** - no logic in components
2. **Two systems should not produce the same component type for the same entities**
3. **Immutability helps threading** - immutable components allow systems to run in different threads without synchronization

## Requirements
1. Implement `com.simsilica.es.EntityComponent`
2. Fields must be `private final` (immutable)
3. Must have no-arg constructor (serialization)
4. Must have constructor with all fields
5. Only getters, no setters
6. Optionally implement `toString()` for debugging
7. Include BSD 2-clause license header

## Template
```java
/*
 * Copyright (c) 2018, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package infinity.es;

import com.simsilica.es.EntityComponent;

public class MyComponent implements EntityComponent {

    private final int value;
    private final String name;

    public MyComponent() {
        // Required for serialization
    }

    public MyComponent(final int value, final String name) {
        this.value = value;
        this.name = name;
    }

    public int getValue() {
        return value;
    }

    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + "[value=" + value + ", name=" + name + "]";
    }
}
```

## Common Component Patterns

### Tag Component (marker, no data)
```java
public class Player implements EntityComponent {
    public Player() { }
}
```

### Model/Type Component (with static constants)
```java
public class Model implements EntityComponent {
    private final String name;
    
    public static final String SHIP = "Ship";
    public static final String BULLET = "Bullet";
    public static final String INVADER = "Invader";
    
    public Model(String name) {
        this.name = name;
    }
    
    public String getName() { return name; }
}
```

### Decay Component (time-based removal)
```java
public class Decay implements EntityComponent {
    private final long start;
    private final long delta;

    public Decay(long deltaMillis) {
        this.start = System.nanoTime();
        this.delta = deltaMillis * 1000000;
    }

    public double getPercent() {
        long time = System.nanoTime();
        return (double)(time - start) / delta;
    }
}
```

### Position Component
```java
public class Position implements EntityComponent {
    private final Vector3f location;

    public Position(Vector3f location) {
        this.location = location;
    }

    public Vector3f getLocation() {
        return location;
    }
}
```

## Existing Components Reference
- `Gold`, `Bounty` - integer value components
- `ShapeInfo` - entity visual shape
- `Frequency` - team assignment
- `Dead` - marks entity as dead
- `Buff` - temporary effects
- `WeaponType` - weapon configuration
- `Delay` - time-based decay
