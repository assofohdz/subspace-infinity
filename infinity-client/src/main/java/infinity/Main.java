// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity;

import com.simsilica.thread.JobState;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.BasicProfilerState;
import com.jme3.app.DebugKeysAppState;
import com.jme3.app.SimpleApplication;
import com.jme3.app.StatsAppState;
import com.jme3.app.state.ScreenshotAppState;
import com.jme3.math.ColorRGBA;
import com.jme3.system.AppSettings;

import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.HAlignment;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.OptionPanelState;
import com.simsilica.lemur.component.QuadBackgroundComponent;
import com.simsilica.lemur.style.Attributes;
import com.simsilica.lemur.style.BaseStyles;
import com.simsilica.lemur.style.Styles;
import com.simsilica.state.CommandConsoleState;
import com.simsilica.state.DebugHudState;
import com.simsilica.state.MemoryDebugState;
import com.simsilica.state.MessageState;

import infinity.client.AvatarMovementFunctions;
import infinity.client.MainGameFunctions;
import infinity.client.states.HelpState;
import infinity.client.states.LightingTunerState;
import infinity.client.states.MainMenuState;
import infinity.client.view.DebugFunctions;
import infinity.client.view.ToolFunctions;

/**
 * The main demo for the network stuff.
 *
 * @author Paul Speed
 */
public class Main extends SimpleApplication {

  static Logger log = LoggerFactory.getLogger(Main.class);

  public Main() {
    super(
        new JobState("regularWorkers", 4, 1),
        new JobState("priorityWorkers", 4, 1),
        new StatsAppState(),
        new DebugKeysAppState(),
        new BasicProfilerState(false),
        new OptionPanelState(), // from Lemur
        new DebugHudState(),
        new MemoryDebugState(),
        new MainMenuState(),
        new MessageState(),
        new CommandConsoleState(),
        new ScreenshotAppState("", System.currentTimeMillis()));
  }

  // Canonical Java entry point + jME AppSettings (framework-typed) — both
  // PMD-flagged but neither narrowable without compromising the standard shape.
  @SuppressWarnings({"PMD.SignatureDeclareThrowsException", "PMD.LooseCoupling"})
  public static void main(final String... args) throws Exception {

    // Create logs directory if it doesn't exist
    new java.io.File("logs").mkdirs();

    // Set up global uncaught exception handler to log crashes
    Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
      if (log.isErrorEnabled()) {
        log.error("Uncaught exception in thread {}", thread.getName(), throwable);
      }
      // Give log4j time to flush
      try { Thread.sleep(100); } catch (InterruptedException ignored) {}
    });

    log.info("=== Subspace Infinity Starting ===");
    if (log.isInfoEnabled()) {
      log.info("Java version: {}", System.getProperty("java.version"));
      log.info("OS: {} {}", System.getProperty("os.name"), System.getProperty("os.arch"));
      log.info("Working directory: {}", System.getProperty("user.dir"));
    }

    // final Application app;

    final Main main = new Main();
    final AppSettings settings = new AppSettings(true);

    // Set some defaults that will get overwritten if
    // there were previously saved settings from the last time the user
    // ran.
    settings.setWidth(1280);
    settings.setHeight(720);
    settings.setVSync(false);
    settings.setSamples(8);

    settings.load("Subspace Infinity");
    settings.setTitle("Subspace Infinity");
    settings.setAudioRenderer(null); // disable audio - no OpenAL device in WSL2

    main.setSettings(settings);

    try {
      main.start();
    } catch (Exception e) {
      log.error("Fatal error during application startup", e);
      throw e;
    }
  }

  @Override
  public void simpleInitApp() {

    setPauseOnLostFocus(false);
    setDisplayFps(false);
    setDisplayStatView(false);

    GuiGlobals.initialize(this);

    GuiGlobals.getInstance().setCursorEventsEnabled(false);

    final GuiGlobals globals = GuiGlobals.getInstance();

    MainGameFunctions.initializeDefaultMappings(globals.getInputMapper());
    DebugFunctions.initializeDefaultMappings(globals.getInputMapper());
    ToolFunctions.initializeDefaultMappings(globals.getInputMapper());
    HelpState.initializeDefaultMappings(globals.getInputMapper());
    LightingTunerState.initializeDefaultMappings(globals.getInputMapper());
    AvatarMovementFunctions.initializeDefaultMappings(globals.getInputMapper());

    BaseStyles.loadGlassStyle();
    globals.getStyles().setDefaultStyle("glass");

    // Some manual styling for the debug HUD
    final Styles styles = globals.getStyles();

    Attributes attrs = styles.getSelector(DebugHudState.CONTAINER_ID, "glass");
    attrs.set("background", null);

    attrs = styles.getSelector(DebugHudState.NAME_ID, "glass");
    attrs.set("color", ColorRGBA.White);
    attrs.set("background", new QuadBackgroundComponent(new ColorRGBA(0, 0, 0, 0.5f)));
    attrs.set("textHAlignment", HAlignment.Right);
    attrs.set("insets", new Insets3f(0, 0, 0, 0));

    attrs = styles.getSelector(DebugHudState.VALUE_ID, "glass");
    attrs.set("color", ColorRGBA.White);
    attrs.set("background", new QuadBackgroundComponent(new ColorRGBA(0, 0, 0, 0.5f)));
    attrs.set("insets", new Insets3f(0, 0, 0, 0));

    // SkyState sky = stateManager.getState(SkyState.class);
    // sky.getGroundColor().set(0.3f, 0.5f, 0.1f, 1);
    // sky.setShowGroundDisc(true);

    // get a RuntimeMXBean reference
    final RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();

    // get the jvm's input arguments as a list of strings
    final List<String> listOfArguments = runtimeMxBean.getInputArguments();
    listOfArguments.forEach(s -> System.out.println("ARG:" + s));
  }

  @Override
  public void simpleUpdate(final float tpf) {
    if( tpf > 0.1 ) {
      log.warn("Long tpf:{}", tpf);
    }
  }
}
