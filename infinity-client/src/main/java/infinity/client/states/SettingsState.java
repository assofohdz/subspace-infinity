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

package infinity.client.states;

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

/** F12 settings panel; presents a tabbed window of in-game tunables. */
public class SettingsState extends BaseAppState {

    private static final String GLASS_STYLE = "glass";

    public static final FunctionId F_SETTINGS = new FunctionId("Show Settings");

    private Container mainWindow;

    private TabbedPanel tabs;

    public SettingsState() {
        setEnabled(false);
    }

    public static void initializeDefaultMappings(final InputMapper inputMapper) {
        inputMapper.map(F_SETTINGS, KeyInput.KEY_F12);
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

        mainWindow = new Container(new BorderLayout(), new ElementId("window"), GLASS_STYLE);
        mainWindow.addChild(new Label("Settings", mainWindow.getElementId().child("title.label"), GLASS_STYLE),
                BorderLayout.Position.North);
        mainWindow.setLocalTranslation(10, app.getCamera().getHeight() - 10, 0);

        final Container mainContents = mainWindow.addChild(
                new Container(mainWindow.getElementId().child("contents.container"), GLASS_STYLE),
                BorderLayout.Position.Center);

        tabs = new TabbedPanel(GLASS_STYLE);
        mainContents.addChild(tabs);
    }

    @Override
    protected void cleanup(final Application app) {
        GuiGlobals.getInstance().getInputMapper().removeDelegate(F_SETTINGS, this, "toggleEnabled");
    }

    @Override
    protected void onEnable() {
        ((SimpleApplication) getApplication()).getGuiNode().attachChild(mainWindow);
        getState(InfinityCameraState.class).setEnabled(false);
    }

    @Override
    protected void onDisable() {
        mainWindow.removeFromParent();
        getState(InfinityCameraState.class).setEnabled(true);
    }
}
