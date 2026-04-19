---
name: lemur-ui
description: Build user interfaces using Lemur UI framework. Use when creating menus, HUD elements, buttons, labels, or other GUI components.
---

# Lemur UI Framework

Lemur is the UI framework for building game interfaces.

## Basic Components

### Container
```java
import com.simsilica.lemur.Container;

Container window = new Container();
```

### Labels
```java
import com.simsilica.lemur.Label;

Label label = new Label("Text");
window.addChild(label);
```

### Buttons
```java
import com.simsilica.lemur.Button;

Button btn = new Button("Click Me");
btn.addClickCommands(source -> {
    System.out.println("Clicked!");
});
window.addChild(btn);
```

### Text Fields
```java
import com.simsilica.lemur.TextField;

TextField field = new TextField("default");
String value = field.getText();
```

### Checkboxes
```java
import com.simsilica.lemur.Checkbox;

Checkbox check = new Checkbox("Enable");
boolean isChecked = check.isChecked();
```

## Layout

### SpringGridLayout (default)
```java
// Vertical stack (default)
Container vertical = new Container();

// Horizontal with explicit layout
Container horizontal = new Container(new SpringGridLayout(Axis.X, Axis.Y));
```

### Positioning
```java
// Center on screen
float x = (cam.getWidth() - window.getPreferredSize().x) / 2;
float y = (cam.getHeight() + window.getPreferredSize().y) / 2;
window.setLocalTranslation(x, y, 0);
```

## Styling

### GuiGlobals
```java
import com.simsilica.lemur.GuiGlobals;

// Initialize in app setup
GuiGlobals.initialize(app);

// Load styles
GuiGlobals.getInstance().loadStyles("Interface/styles.groovy");
```

### Focus
```java
GuiGlobals.getInstance().requestFocus(component);
```

## Integration with AppState

```java
public class MyUIState extends BaseAppState {
    private Container window;
    
    @Override
    protected void initialize(Application app) {
        window = new Container();
        // Build UI
    }
    
    @Override
    protected void onEnable() {
        ((SimpleApplication) getApplication()).getGuiNode().attachChild(window);
    }
    
    @Override
    protected void onDisable() {
        window.removeFromParent();
    }
    
    @Override
    protected void cleanup(Application app) {
        window = null;
    }
}
```

## Project UI Assets
- Styles in `infinity/assets/Interface/`
- Custom components extend Lemur base classes
