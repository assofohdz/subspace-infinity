/*
 * $Id$
 *
 * Copyright (c) 2018, Simsilica, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package infinity;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.badlogic.gdx.Application;

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
import infinity.client.view.DebugFunctions;
import infinity.client.view.ToolFunctions;

/**
 * The main demo for the network stuff.
 *
 * @author Paul Speed
 */
public class Main extends SimpleApplication {

    static Logger log = LoggerFactory.getLogger(Main.class);

    // private static Grid grid = new Grid(32, 0, 32);

    public static void main(String... args) throws Exception {

        Application app;

        Main main = new Main();
        AppSettings settings = new AppSettings(true);

        // Set some defaults that will get overwritten if
        // there were previously saved settings from the last time the user
        // ran.
        settings.setWidth(1280);
        settings.setHeight(720);
        settings.setVSync(true);

        settings.load("MOSS-Network Demo");
        settings.setTitle("MOSS-Network Demo");

        main.setSettings(settings);

        main.start();
    }

    public Main() {
        super(new StatsAppState(), new DebugKeysAppState(), new BasicProfilerState(false),
                // new FlyCamAppState(),
                // new CameraMovementState(),
                new OptionPanelState(), // from Lemur
                // new HelpState(),
                new DebugHudState(), new MemoryDebugState(), new MainMenuState(), new MessageState(),
                new CommandConsoleState(),
                // new LightingState(),
                // new SkyState(),
                // new SkySettingsState(),
                // new PostProcessingState(),
                // new GridState(grid),
                // new SettingsState(),
                new ScreenshotAppState("", System.currentTimeMillis()));
    }

    public void simpleInitApp() {

        setPauseOnLostFocus(false);
        setDisplayFps(false);
        setDisplayStatView(false);

        GuiGlobals.initialize(this);
        GuiGlobals.getInstance().setCursorEventsEnabled(false);

        GuiGlobals globals = GuiGlobals.getInstance();

        MainGameFunctions.initializeDefaultMappings(globals.getInputMapper());
        // CameraMovementFunctions.initializeDefaultMappings(globals.getInputMapper());
        // SettingsState.initializeDefaultMappings(globals.getInputMapper());
        DebugFunctions.initializeDefaultMappings(globals.getInputMapper());
        ToolFunctions.initializeDefaultMappings(globals.getInputMapper());
        HelpState.initializeDefaultMappings(globals.getInputMapper());
        AvatarMovementFunctions.initializeDefaultMappings(globals.getInputMapper());

        BaseStyles.loadGlassStyle();
        globals.getStyles().setDefaultStyle("glass");

        // Some manual styling for the debug HUD
        Styles styles = globals.getStyles();

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
        RuntimeMXBean runtimeMxBean = ManagementFactory.getRuntimeMXBean();

        // get the jvm's input arguments as a list of strings
        List<String> listOfArguments = runtimeMxBean.getInputArguments();
        listOfArguments.forEach(s -> System.out.println("ARG:" + s));
    }

    @Override
    public void simpleUpdate(float tpf) {
        // log.info("simpleUpdate()");
    }
}
