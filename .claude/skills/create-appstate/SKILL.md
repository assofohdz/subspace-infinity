---
name: create-appstate
description: Create client-side application states using jME3 BaseAppState. Use when building UI screens, client rendering, input handling, or visual effects.
---

# Creating App States (jME3 Client)

Client-side states manage UI, rendering, and input using jME3's state system.

## Location
`infinity/src/main/java/infinity/`

## Requirements
1. Extend `com.jme3.app.state.BaseAppState`
2. Implement lifecycle methods properly
3. Clean up all resources in `cleanup()`
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
        // Release all resources
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
