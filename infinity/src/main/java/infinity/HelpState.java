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

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Joiner;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.KeyInput;
import com.jme3.input.KeyNames;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import com.simsilica.lemur.Container;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.Insets3f;
import com.simsilica.lemur.Label;
import com.simsilica.lemur.input.Axis;
import com.simsilica.lemur.input.Button;
import com.simsilica.lemur.input.FunctionId;
import com.simsilica.lemur.input.InputMapper;
import com.simsilica.lemur.input.InputMapper.Mapping;
import com.simsilica.lemur.style.ElementId;

import infinity.client.CameraMovementFunctions;
import infinity.client.view.DebugFunctions;
import infinity.client.view.ToolFunctions;

/**
 * Presents a help popup to the user when they press F1.
 *
 * @author Paul Speed
 */
public class HelpState extends BaseAppState {

    public static final FunctionId F_HELP = new FunctionId("Help");

    private Container helpWindow;
    // private final boolean movementState = false;

    private final KeyHelp[] keyHelp = { new KeyHelp(F_HELP, "Opens/closes this help window."),
            new KeyHelp(CameraMovementFunctions.F_X_LOOK, "Rotates left/right."),
            new KeyHelp(CameraMovementFunctions.F_Y_LOOK, "Rotates up/down."),
            new KeyHelp(CameraMovementFunctions.F_MOVE, "Flies forward and back."),
            new KeyHelp(CameraMovementFunctions.F_STRAFE, "Flies side to side."),
            new KeyHelp(CameraMovementFunctions.F_ELEVATE, "Flies up or down."),
            new KeyHelp(CameraMovementFunctions.F_RUN, "Increases speed."),
            new KeyHelp(MainGameFunctions.F_IN_GAME_MENU, "In Game Menu"),
            new KeyHelp(MainGameFunctions.F_CHAT_CONSOLE, "Chat Console"),
            new KeyHelp(ToolFunctions.F_MAIN_TOOL, "Main Tool"), new KeyHelp(ToolFunctions.F_ALT_TOOL, "Alt. Tool"),
            new KeyHelp(SettingsState.F_SETTINGS, "Opens the in-game settings panel."),
            new KeyHelp(DebugFunctions.F_BIN_DEBUG, "Toggle Bin Status"),
            new KeyHelp(DebugFunctions.F_BODY_DEBUG, "Toggle Body Debug"),
            new KeyHelp(DebugFunctions.F_CONTACT_DEBUG, "Toggle Contact Debug"),
            new KeyHelp("PrtScrn", "Takes a screen shot."), new KeyHelp("F5", "Toggles display stats."),
            new KeyHelp("F6", "Toggles rendering frame timings.") };

    public HelpState() {
        setEnabled(false);
    }

    public static void initializeDefaultMappings(final InputMapper inputMapper) {
        inputMapper.map(F_HELP, KeyInput.KEY_F1);
    }

    public void close() {
        setEnabled(false);
    }

    public void toggleEnabled() {
        setEnabled(!isEnabled());
    }

    @Override
    protected void initialize(final Application app) {

        final InputMapper inputMapper = GuiGlobals.getInstance().getInputMapper();
        inputMapper.addDelegate(F_HELP, this, "toggleEnabled");

        helpWindow = new Container();
        final Label title = helpWindow.addChild(new Label("In-Game Help", new ElementId("title")));
        // title.setFontSize(24);
        title.setInsets(new Insets3f(2, 2, 0, 2));

        final Container sub = helpWindow.addChild(new Container());
        sub.setInsets(new Insets3f(10, 10, 10, 10));
        sub.addChild(new Label("Key Bindings"));

        final Container keys = sub.addChild(new Container());

        final Joiner commas = Joiner.on(", ");
        final Joiner lines = Joiner.on("\n");
        for (final KeyHelp help : keyHelp) {
            help.updateKeys(inputMapper);
            String s = commas.join(help.keyNames);
            keys.addChild(new Label(s, new ElementId("help.key.label")));
            s = lines.join(help.description);
            keys.addChild(new Label(s, new ElementId("help.description.label")), Integer.valueOf(1));
        }

        // helpWindow.addChild(new ActionButton(new CallMethodAction("Done", this,
        // "close")));

        System.out.println("All InputMapper function mappings:");
        for (final FunctionId id : inputMapper.getFunctionIds()) {
            System.out.println(id);
            System.out.println("  mappings:");
            for (final Mapping m : inputMapper.getMappings(id)) {
                System.out.println("    " + m);
                final Object o = m.getPrimaryActivator();
                if (o instanceof Integer) {
                    final Integer keyCode = (Integer) o;
                    System.out.println("      primary:" + KeyNames.getName(keyCode.intValue()));
                } else {
                    System.out.println("      primary:" + o);
                }
                for (final Object mod : m.getModifiers()) {
                    if (mod instanceof Integer) {
                        final Integer keyCode = (Integer) mod;
                        System.out.println("      modifier:" + KeyNames.getName(keyCode.intValue()));
                    }
                }
            }
        }
    }

    @Override
    protected void cleanup(final Application app) {
        final InputMapper inputMapper = GuiGlobals.getInstance().getInputMapper();
        inputMapper.removeDelegate(F_HELP, this, "toggleEnabled");
    }

    @Override
    protected void onEnable() {
        final Node gui = ((SimpleApplication) getApplication()).getGuiNode();

        final int width = getApplication().getCamera().getWidth();
        final int height = getApplication().getCamera().getHeight();

        // Base size and positioning off of 1.5x the 'standard scale'
        // float standardScale = 1;
        // helpWindow.setLocalScale(1.5f * standardScale);

        final Vector3f pref = helpWindow.getPreferredSize();
        // pref.multLocal(1.5f * standardScale);

        helpWindow.setLocalTranslation(width * 0.5f - pref.x * 0.5f, height * 0.5f + pref.y * 0.5f, 100);

        gui.attachChild(helpWindow);
        GuiGlobals.getInstance().requestFocus(helpWindow);
    }

    @Override
    protected void onDisable() {
        helpWindow.removeFromParent();
    }

    private class KeyHelp {
        FunctionId function;
        String[] keyNames;
        String[] description;

        public KeyHelp(final FunctionId function, final String... description) {
            this.function = function;
            this.description = description;
        }

        public KeyHelp(final String keys, final String... description) {
            keyNames = new String[] { keys };
            this.description = description;
        }

        public void updateKeys(final InputMapper inputMapper) {
            if (function == null) {
                return;
            }

            final List<String> names = new ArrayList<>();

            // Capture all of the keys first
            for (final Mapping m : inputMapper.getMappings(function)) {
                final Object o = m.getPrimaryActivator();

                String primary;
                if (o instanceof Button) {
                    continue;
                } else if (o instanceof Axis) {
                    continue;
                } else if (o instanceof Integer) {
                    final Integer i = (Integer) o;
                    primary = KeyNames.getName(i.intValue());
                } else {
                    // Not a mapping we can deal with
                    continue;
                }

                // Keep track of the mirrored form and combined forms
                // in case we want to swap out an older mirrored form
                // for the combined form. For example, Left Shift + F4
                // and Right Shift + F4 combined to Shift + F4.
                final StringBuilder alt = new StringBuilder(primary);
                final StringBuilder comb = new StringBuilder(primary);

                final StringBuilder sb = new StringBuilder(primary);
                for (final Object mod : m.getModifiers()) {
                    if (mod instanceof Integer) {
                        final int iMod = ((Integer) mod).intValue();
                        if (iMod == KeyInput.KEY_LSHIFT) {
                            alt.insert(0, KeyNames.getName(KeyInput.KEY_RSHIFT) + "+");
                            comb.insert(0, "Shift+");
                        } else if (iMod == KeyInput.KEY_RSHIFT) {
                            alt.insert(0, KeyNames.getName(KeyInput.KEY_LSHIFT) + "+");
                            comb.insert(0, "Shift+");
                        } else if (iMod == KeyInput.KEY_LCONTROL) {
                            alt.insert(0, KeyNames.getName(KeyInput.KEY_RCONTROL) + "+");
                            comb.insert(0, "Ctrl+");
                        } else if (iMod == KeyInput.KEY_RCONTROL) {
                            alt.insert(0, KeyNames.getName(KeyInput.KEY_LCONTROL) + "+");
                            comb.insert(0, "Ctrl+");
                        }
                        sb.insert(0, KeyNames.getName(((Integer) mod).intValue()) + "+");
                    }
                }
                System.out.println(function + " normal:" + sb + "  alt:" + alt + "  comb:" + comb);
                if (names.remove(alt.toString())) {
                    names.add(comb.toString());
                } else {
                    names.add(sb.toString());
                }
            }

            // Then capture axis and buttons
            for (final Mapping m : inputMapper.getMappings(function)) {
                final Object o = m.getPrimaryActivator();

                String primary;
                if (o instanceof Button) {
                    primary = ((Button) o).getName();
                } else if (o instanceof Axis) {
                    primary = ((Axis) o).getName();
                } else {
                    // Not a mapping we can deal with
                    continue;
                }

                final StringBuilder sb = new StringBuilder(primary);
                for (final Object mod : m.getModifiers()) {
                    if (mod instanceof Integer) {
                        sb.append("+");
                        sb.append(KeyNames.getName(((Integer) mod).intValue()));
                    }
                }
                names.add(sb.toString());
            }
            keyNames = new String[names.size()];
            keyNames = names.toArray(keyNames);
        }
    }
}
