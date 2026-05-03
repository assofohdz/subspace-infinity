---
name: create-module
description: Create game modules extending BaseGameModule for server extensions. Use when building pluggable server functionality or game mode extensions.
---

# Creating Game Modules

Modules are pluggable extensions that add server functionality.

## Location
`modules/src/main/java/infinity/modules/`

## Requirements
1. Extend `infinity.sim.BaseGameModule`
2. BaseGameModule itself extends AbstractGameSystem
3. Follow same patterns as game systems
4. Include SPDX-only `BSD-3-Clause` license header

## Template
```java
// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.sim.BaseGameModule;

public class MyModule extends BaseGameModule {

    private EntityData ed;
    private EntitySet entities;

    @Override
    protected void initialize() {
        ed = getSystem(EntityData.class);
        entities = ed.getEntities(/* components */);
        
        // Register event listeners
        // Initialize module state
    }

    @Override
    protected void terminate() {
        entities.release();
        entities = null;
    }

    @Override
    public void update(final SimTime time) {
        if (entities.applyChanges()) {
            for (final Entity e : entities.getAddedEntities()) {
                // Handle new
            }
            for (final Entity e : entities.getChangedEntities()) {
                // Handle changed
            }
            for (final Entity e : entities.getRemovedEntities()) {
                // Handle removed
            }
        }
    }

    @Override
    public void start() {
        // Game started
    }

    @Override
    public void stop() {
        // Game stopped
    }
}
```

## Module Philosophy
From the developer guide:
- Modules are the building blocks for extending server functionality
- Similar to ASSS modules but data-oriented instead of object-oriented
- Networking is abstracted by SimEthereal

## Registering Modules
Modules must be registered with the game manager during server setup.
