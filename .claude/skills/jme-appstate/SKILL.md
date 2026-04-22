---
name: jme-appstate
description: Create client-side application states using jME3 BaseAppState. Use when building UI screens, client rendering, input handling, or visual effects.
---

# Creating App States (jME3 Client)

Client-side states manage UI, rendering, and input using jME3's state system.

## Location
`infinity/src/main/java/infinity/`

## Requirements
1. Extend `com.jme3.app.state.BaseAppState`
2. Implement lifecycle methods properly
3. Clean up all resources in `cleanup()` - **CRITICAL: release EntitySets**
4. Include BSD 2-clause license header

## Template
```java
/*
 * Copyright (c) 2018, Asser Fahrenholz
 * All rights reserved.
 * [BSD 2-clause license...]
 */
package infinity;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;

public class MyState extends BaseAppState {

    @Override
    protected void initialize(final Application app) {
        // Called once when state is attached
        // Setup resources, load assets
    }

    @Override
    protected void cleanup(final Application app) {
        // Called once when state is detached
        // Release all resources and EntitySets!
    }

    @Override
    protected void onEnable() {
        // Called when state becomes active
        // Attach to scene graph, register listeners
    }

    @Override
    protected void onDisable() {
        // Called when state becomes inactive
        // Detach from scene graph, unregister listeners
    }

    @Override
    public void update(final float tpf) {
        // Per-frame updates (optional override)
    }
}
```

## The Visualization Pattern (from Zay-ES Wiki)
This is the first design pattern in ECS - syncing entities to visual spatials:
```java
public class VisualAppState extends BaseAppState {
    private EntityData ed;
    private EntitySet entities;
    private final Map<EntityId, Spatial> models = new HashMap<>();
    private SimpleApplication app;

    @Override
    protected void initialize(Application app) {
        this.app = (SimpleApplication) app;
        ed = getEntityData();  // Get from ConnectionState or similar
        entities = ed.getEntities(Position.class, Model.class);
    }

    @Override
    protected void cleanup(Application app) {
        // CRITICAL: Release EntitySet to prevent memory leak
        entities.release();
        entities = null;
    }

    @Override
    public void update(float tpf) {
        // Check for changes and handle them
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
            app.getRootNode().attachChild(s);
        }
    }

    private void updateModels(Set<Entity> changed) {
        for (Entity e : changed) {
            Spatial s = models.get(e.getId());
            updateModelSpatial(e, s);
        }
    }

    private void updateModelSpatial(Entity e, Spatial s) {
        Position p = e.get(Position.class);
        s.setLocalTranslation(p.getLocation());
    }

    private Spatial createVisual(Entity e) {
        Model model = e.get(Model.class);
        return assetManager.loadModel("Models/" + model.getName() + ".j3o");
    }
}
```

## With Lemur UI
```java
import com.simsilica.lemur.Container;
import com.simsilica.lemur.Button;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.GuiGlobals;

@Override
protected void initialize(final Application app) {
    Container window = new Container();
    window.addChild(new Label("Title"));
    
    Button btn = window.addChild(new Button("Click Me"));
    btn.addClickCommands(source -> {
        // Handle click
    });
    
    // Center on screen
    window.setLocalTranslation(
        (app.getCamera().getWidth() - window.getPreferredSize().x) / 2,
        (app.getCamera().getHeight() + window.getPreferredSize().y) / 2,
        0
    );
}

@Override
protected void onEnable() {
    ((SimpleApplication) getApplication()).getGuiNode().attachChild(window);
}

@Override
protected void onDisable() {
    window.removeFromParent();
}
```

## Getting Other States
```java
OtherState other = getState(OtherState.class);
```

## Accessing Entity Data (Client)
```java
ConnectionState conn = getState(ConnectionState.class);
EntityData ed = conn.getEntityData();
```

## Existing States Reference
- `MainMenuState` - main menu UI
- `SettingsState` - settings screen
- `HelpState` - help/controls screen
- `TimeState` - time management
- `PostProcessingState` - visual effects
