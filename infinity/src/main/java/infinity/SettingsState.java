/*
 * $Id$
 *
 * Copyright (c) 2016, Simsilica, LLC
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

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.KeyInput;

import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.TabbedPanel;
import com.simsilica.lemur.component.BorderLayout;
import com.simsilica.lemur.input.FunctionId;
import com.simsilica.lemur.input.InputMapper;
import com.simsilica.lemur.style.ElementId;

import infinity.client.view.InfinityCameraState;

/**
 *
 *
 * @author Paul Speed
 */
public class SettingsState extends BaseAppState {

    public static final FunctionId F_SETTINGS = new FunctionId("Show Settings");

    private Container mainWindow;
    private Container mainContents;

    private TabbedPanel tabs;

    // private final boolean originalCursorEventsEnabled = false;

    public SettingsState() {
        setEnabled(false);
    }

    public static void initializeDefaultMappings(final InputMapper inputMapper) {
        inputMapper.map(F_SETTINGS, KeyInput.KEY_TAB);
    }

    public void toggleEnabled() {
        setEnabled(!isEnabled());
    }

    public TabbedPanel getParameterTabs() {
        return tabs;
    }

    @Override
    protected void initialize(final Application app) {
        GuiGlobals.getInstance().getInputMapper().addDelegate(F_SETTINGS, this, "toggleEnabled");

        mainWindow = new Container(new BorderLayout(), new ElementId("window"), "glass");
        mainWindow.addChild(new Label("Settings", mainWindow.getElementId().child("title.label"), "glass"),
                BorderLayout.Position.North);
        mainWindow.setLocalTranslation(10, app.getCamera().getHeight() - 10, 0);

        mainContents = mainWindow.addChild(
                new Container(mainWindow.getElementId().child("contents.container"), "glass"),
                BorderLayout.Position.Center);

        tabs = new TabbedPanel("glass");
        mainContents.addChild(tabs);

        /*
         * PhysicsState physics = getState(PhysicsState.class); ShootState shoot =
         * getState(ShootState.class); Container physicsSettings = new Container();
         * tabs.addTab("Physics", physicsSettings); //physicsSettings.addChild(new
         * Label("Projectiles", new ElementId("window.title.label")));
         * //physicsSettings.addChild(shoot.getSettings()); physicsSettings.addChild(new
         * Label("Simulation", new ElementId("window.title.label")));
         * physicsSettings.addChild(physics.getSimulationSettings());
         * physicsSettings.addChild(new Label("New Bodies", new
         * ElementId("window.title.label")));
         * physicsSettings.addChild(physics.getBodySettings());
         * physicsSettings.addChild(new Label("Contacts", new
         * ElementId("window.title.label")));
         * physicsSettings.addChild(physics.getContactSettings());
         * physicsSettings.addChild(new Label("Annealing", new
         * ElementId("window.title.label")));
         * physicsSettings.addChild(physics.getAnnealingSettings());
         *
         * tabs.addTab("Projectiles", shoot.getSettings());
         */

        // SkySettingsState skySettings = getState(SkySettingsState.class);
        // getParameterTabs().addTab("Sky", skySettings.getSettings());

    }

    @Override
    protected void cleanup(final Application app) {
        GuiGlobals.getInstance().getInputMapper().removeDelegate(F_SETTINGS, this, "toggleEnabled");
    }

    @Override
    protected void onEnable() {
        ((SimpleApplication) getApplication()).getGuiNode().attachChild(mainWindow);
        // getState(CameraMovementState.class).setEnabled(false);
        getState(InfinityCameraState.class).setEnabled(false);
        // getState(ShootState.class).setEnabled(false);
        // getState(ObjectInfoState.class).setEnabled(true);
    }

    @Override
    protected void onDisable() {
        mainWindow.removeFromParent();
        // getState(CameraMovementState.class).setEnabled(true);
        getState(InfinityCameraState.class).setEnabled(true);
        // getState(ShootState.class).setEnabled(true);
        // getState(ObjectInfoState.class).setEnabled(false);
    }
}
