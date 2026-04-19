---
name: create-component
description: Create Zay-ES EntityComponent classes for the ECS architecture. Use when creating new components, data holders, or entity attributes.
---

# Creating Zay-ES Components

Components are immutable data containers for the Entity-Component-System.

## Location
`api/src/infinity/es/`

## Requirements
1. Implement `com.simsilica.es.EntityComponent`
2. Fields must be `private final` (immutable)
3. Must have no-arg constructor (serialization)
4. Must have constructor with all fields
5. Only getters, no setters
6. Include BSD 2-clause license header

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
}
```

## Existing Components Reference
- `Gold`, `Bounty` - integer value components
- `ShapeInfo` - entity visual shape
- `Frequency` - team assignment
- `Dead` - marks entity as dead
- `Buff` - temporary effects
- `WeaponType` - weapon configuration
