---
name: lemur-ui
description: Build user interfaces using Lemur UI framework. Use when creating menus, HUD elements, buttons, labels, or other GUI components.
---

# Lemur UI Framework

Lemur is the UI framework for building game interfaces. Documentation from official wiki.

## Initialization (from Wiki)
```java
// In simpleInitApp() or app initialization
GuiGlobals.initialize(this);

// Load the 'glass' style (requires Groovy dependency)
BaseStyles.loadGlassStyle();

// Set 'glass' as default style
GuiGlobals.getInstance().getStyles().setDefaultStyle("glass");
```

## Positioning and Size (from Wiki)
**Important**: GUI elements are positioned by their upper left corner and grow **down and right**. This is a compromise between OpenGL (0,0 at lower left) and traditional UI (0,0 at upper left).

```java
// Put window at upper area - elements grow DOWN from this point
myWindow.setLocalTranslation(300, 300, 0);
```

## Class Hierarchy (from Wiki)
```
Spatial
  └── Panel (base: background, border, insets, alpha)
        ├── Container (adds layout support)
        └── Label (adds text, icon, font, color)
              └── Button (adds click commands, highlight)
```

## Base GUI Elements

### Panel Properties
- `background`: Background component (QuadComponent)
- `border`: Border component (underneath background)
- `insets`: Insets3f - space around element within parent (like CSS margin)
- `alpha`: Fade in/out entire hierarchy

### Container
```java
// Create with default SpringGridLayout
Container myWindow = new Container();
guiNode.attachChild(myWindow);

// Or with specific layout
Container c = new Container(new BorderLayout());
```

### Label Properties
- `text`: Display text
- `icon`: Icon component
- `font`, `fontSize`: Text rendering
- `textVAlignment`: VAlignment.Top/Bottom/Center
- `textHAlignment`: HAlignment.Left/Right/Center
- `color`: Text color
- `shadowColor`, `shadowOffset`: Text shadow

```java
Label label = new Label("Hello, World.");
label.setFontSize(16);
label.setColor(ColorRGBA.White);
```

### Button
Extends Label with click support.
```java
Button clickMe = myWindow.addChild(new Button("Click Me"));
clickMe.addClickCommands(new Command<Button>() {
    @Override
    public void execute(Button source) {
        System.out.println("Clicked!");
    }
});

// Or with lambda
clickMe.addClickCommands(source -> System.out.println("Clicked!"));
```

**Button Actions**: ButtonAction.Down, Up, Click, HighlightOn, HighlightOff

### TextField Properties
- `text`: Entered text value
- `singleLine`: true (default) or false for multiline
- `preferredWidth`: Width hint for layouts

```java
TextField field = new TextField("default");
String value = field.getText();
```

## Layouts (from Wiki)

### SpringGridLayout (default)
Row-major by default. Children added to grid cells.
```java
Container c = new Container();  // SpringGridLayout by default

// Explicit grid positions (row, column)
c.addChild(new Label("Foo"), 0, 0);
c.addChild(new Label("Bar"), 1, 0);

// Shortcut - auto-increments row
c.addChild(new Label("Foo"));
c.addChild(new Label("Bar"));

// Two-column layout - specify column only
c.addChild(new Label("Name:"));
c.addChild(new TextField("value"), 1);  // column 1
c.addChild(new Label("Age:"));
c.addChild(new TextField("25"), 1);     // column 1
```

### BorderLayout
```java
Container c = new Container(new BorderLayout());
c.addChild(new Label("Top"), Position.North);
c.addChild(new Label("Bottom"), Position.South);
c.addChild(new Label("Center"), Position.Center);
c.addChild(new Label("Left"), Position.West);
c.addChild(new Label("Right"), Position.East);
```

## Composite Elements (from Wiki)

### Slider
```java
Slider slider = new Slider();  // x-axis default
slider.setDelta(1);  // increment amount
double value = slider.getModel().getValue();
```

### ProgressBar
```java
ProgressBar progress = new ProgressBar();
progress.setProgressPercent(0.5);  // 50%
progress.setMessage("Loading...");
```

### ListBox (proto)
```java
ListBox<String> listBox = new ListBox<>();
listBox.getModel().add("Item 1");
listBox.getModel().add("Item 2");
```

### OptionPanelState (proto)
```java
// Add to state manager once
stateManager.attach(new OptionPanelState());

// Show modal dialogs
getState(OptionPanelState.class).show("Title", "Message");
getState(OptionPanelState.class).showError("Error", "Something went wrong");
```

## Styling (from Wiki)

### Element IDs
Define style hierarchy: `container.contained.contained`
```java
// Create with ElementId
Container window = new Container(new ElementId("mywindow"));
Button btn = new Button("Click", new ElementId("mywindow.button"));
```

### Selectors (Groovy style language)
```groovy
// Style all elements of 'glass' style
selector('glass') {
    fontSize = 20
}

// Style specific element ID
selector('button', 'glass') {
    color = color(0.5, 0.75, 0.75, 0.85)
    background = new QuadBackgroundComponent(color(0, 1, 0, 1))
}

// Containment selector - only buttons inside sliders
selector('slider', 'button', 'glass') {
    fontSize = 10
}
```

### Style in Code
```java
Styles styles = GuiGlobals.getInstance().getStyles();
Attributes attrs = styles.getSelector("button", "glass");
attrs.set("color", new ColorRGBA(0.5f, 0.75f, 0.75f, 0.85f));
attrs.set("fontSize", 14);
```

## Project-Specific Patterns

### ActionButton with CallMethodAction
```java
import com.simsilica.lemur.ActionButton;
import com.simsilica.lemur.CallMethodAction;

ActionButton btn = new ActionButton(
    new CallMethodAction("Button Text", this, "methodName")
);
btn.setInsets(new Insets3f(5, 10, 5, 10));
window.addChild(btn);

// Method called when clicked
protected void methodName() {
    log.info("Button clicked");
}
```

### Menu State Pattern
```java
public class MainMenuState extends BaseAppState {
    private Container mainWindow;

    @Override
    protected void initialize(Application app) {
        mainWindow = new Container();
        // Build UI...
    }

    @Override
    protected void onEnable() {
        ((SimpleApplication) getApplication()).getGuiNode().attachChild(mainWindow);
        GuiGlobals.getInstance().requestCursorEnabled(this);
    }

    @Override
    protected void onDisable() {
        mainWindow.removeFromParent();
        GuiGlobals.getInstance().releaseCursorEnabled(this);
    }
}
```

## Project Assets
- Styles in `infinity/assets/Interface/`
