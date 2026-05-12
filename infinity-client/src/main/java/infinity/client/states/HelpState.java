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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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

import infinity.client.MainGameFunctions;
import infinity.client.view.DebugFunctions;
import infinity.client.view.ToolFunctions;

/**
 * F1 help popup; key bindings auto-derived from
 * {@link InputMapper#getFunctionIds()}, grouped by {@link FunctionId#getGroup()},
 * then followed by a hand-written "System (jME defaults)" section for keys that
 * don't go through {@code InputMapper} (PrtScrn / F5 / F6).
 *
 * <p>Function IDs without any active mapping render as {@code (unbound)} so
 * this popup doubles as a self-debugging tool for declared-but-unbound
 * function ids (see {@code .scratch/debug-state-bindings/PRD.md} TD-1).
 */
public class HelpState extends BaseAppState {

    public static final FunctionId F_HELP = new FunctionId("Help");

    private Container helpWindow;

    // Falls back to {@link FunctionId#getName()} when a function has no entry here.
    private final Map<FunctionId, String> descriptionOverrides = buildDescriptionOverrides();

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
        title.setInsets(new Insets3f(2, 2, 0, 2));

        final Container sub = helpWindow.addChild(new Container());
        sub.setInsets(new Insets3f(10, 10, 10, 10));
        sub.addChild(new Label("Key Bindings"));

        final Container keys = sub.addChild(new Container());

        // Auto-derived, group-organized FunctionId entries.
        final Map<String, List<KeyHelp>> byGroup = collectFunctionIdsByGroup(inputMapper);
        for (final Map.Entry<String, List<KeyHelp>> group : byGroup.entrySet()) {
            renderGroupHeader(keys, group.getKey());
            renderHelpRows(keys, inputMapper, group.getValue());
        }

        // System (jME defaults) supplementary section — these bindings live
        // outside InputMapper, so they're hand-written and rendered last.
        renderGroupHeader(keys, "System (jME defaults)");
        renderHelpRows(keys, inputMapper, List.of(
                new KeyHelp("PrtScrn", "Takes a screen shot."),
                new KeyHelp("F5", "Toggles display stats."),
                new KeyHelp("F6", "Toggles rendering frame timings.")));

        dumpInputMappings(inputMapper);
    }

    private static Map<FunctionId, String> buildDescriptionOverrides() {
        final Map<FunctionId, String> map = new HashMap<>();
        map.put(F_HELP, "Opens/closes this help window.");
        map.put(MainGameFunctions.F_IN_GAME_MENU, "In Game Menu");
        map.put(MainGameFunctions.F_PLAYER_LIST, "Player List");
        map.put(MainGameFunctions.F_CHAT_CONSOLE, "Chat Console");
        map.put(ToolFunctions.F_MAIN_TOOL, "Main Tool");
        map.put(ToolFunctions.F_ALT_TOOL, "Alt. Tool");
        map.put(SettingsState.F_SETTINGS, "Opens the in-game settings panel.");
        map.put(DebugFunctions.F_BIN_DEBUG, "Toggle Bin Status");
        map.put(DebugFunctions.F_BODY_DEBUG, "Toggle Body Debug");
        map.put(DebugFunctions.F_CONTACT_DEBUG, "Toggle Contact Debug");
        return map;
    }

    // {@link TreeMap} so groups render alphabetically; entries sorted by function name for stable output.
    private Map<String, List<KeyHelp>> collectFunctionIdsByGroup(final InputMapper inputMapper) {
        final Map<String, List<KeyHelp>> byGroup = new TreeMap<>();
        for (final FunctionId function : inputMapper.getFunctionIds()) {
            final String description =
                    descriptionOverrides.getOrDefault(function, function.getName());
            byGroup.computeIfAbsent(function.getGroup(), g -> new ArrayList<>())
                    .add(new KeyHelp(function, description));
        }
        for (final List<KeyHelp> entries : byGroup.values()) {
            entries.sort(Comparator.comparing(h -> h.function.getName()));
        }
        return byGroup;
    }

    private static void renderGroupHeader(final Container keys, final String groupName) {
        keys.addChild(new Label(groupName, new ElementId("help.section.label")));
        // Empty cell in column 1 so the header doesn't visually merge with the next
        // row's keys column.
        keys.addChild(new Label("", new ElementId("help.section.label")), Integer.valueOf(1));
    }

    private static void renderHelpRows(
            final Container keys, final InputMapper inputMapper, final List<KeyHelp> rows) {
        final Joiner commas = Joiner.on(", ");
        final Joiner lines = Joiner.on("\n");
        for (final KeyHelp help : rows) {
            help.updateKeys(inputMapper);
            keys.addChild(
                    new Label(commas.join(help.keyNames), new ElementId("help.key.label")));
            keys.addChild(
                    new Label(lines.join(help.description), new ElementId("help.description.label")),
                    Integer.valueOf(1));
        }
    }

    private static void dumpInputMappings(final InputMapper inputMapper) {
        System.out.println("All InputMapper function mappings:");
        for (final FunctionId id : inputMapper.getFunctionIds()) {
            System.out.println(id);
            System.out.println("  mappings:");
            for (final Mapping m : inputMapper.getMappings(id)) {
                dumpMapping(m);
            }
        }
    }

    private static void dumpMapping(final Mapping m) {
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

        final Vector3f pref = helpWindow.getPreferredSize();

        helpWindow.setLocalTranslation(width * 0.5f - pref.x * 0.5f, height * 0.5f + pref.y * 0.5f, 100);

        gui.attachChild(helpWindow);
        GuiGlobals.getInstance().requestFocus(helpWindow);
    }

    @Override
    protected void onDisable() {
        helpWindow.removeFromParent();
    }

    private static class KeyHelp {
        private static final String UNBOUND = "(unbound)";

        FunctionId function;
        String[] keyNames;
        String[] description;

        KeyHelp(final FunctionId function, final String... description) {
            this.function = function;
            this.description = description;
        }

        KeyHelp(final String keys, final String... description) {
            keyNames = new String[] { keys };
            this.description = description;
        }

        public void updateKeys(final InputMapper inputMapper) {
            if (function == null) {
                return;
            }

            final List<String> names = new ArrayList<>();
            captureKeyMappings(inputMapper, names);
            captureAxisAndButtonMappings(inputMapper, names);
            // Slice client-leak-cleanup TD-1 — surface unmapped declarations rather
            // than rendering a blank cell, so this popup self-documents the
            // declared-but-unbound bug class.
            if (names.isEmpty()) {
                names.add(UNBOUND);
            }
            keyNames = names.toArray(new String[0]);
        }

        private void captureKeyMappings(final InputMapper inputMapper, final List<String> names) {
            for (final Mapping m : inputMapper.getMappings(function)) {
                final Object o = m.getPrimaryActivator();
                if (!(o instanceof Integer)) {
                    // Buttons / Axes / unknown — handled in the second pass.
                    continue;
                }
                final String primary = KeyNames.getName(((Integer) o).intValue());
                processIntegerMapping(m, primary, names);
            }
        }

        private static void processIntegerMapping(final Mapping m, final String primary, final List<String> names) {
            // Keep track of the mirrored form and combined forms
            // in case we want to swap out an older mirrored form
            // for the combined form. For example, Left Shift + F4
            // and Right Shift + F4 combined to Shift + F4.
            final StringBuilder alt = new StringBuilder(primary);
            final StringBuilder comb = new StringBuilder(primary);
            final StringBuilder sb = new StringBuilder(primary);
            for (final Object mod : m.getModifiers()) {
                if (mod instanceof Integer) {
                    applyModifierToBuilders((Integer) mod, alt, comb, sb);
                }
            }
            if (names.remove(alt.toString())) {
                names.add(comb.toString());
            } else {
                names.add(sb.toString());
            }
        }

        private static void applyModifierToBuilders(final Integer mod, final StringBuilder alt,
                final StringBuilder comb, final StringBuilder sb) {
            final int iMod = mod.intValue();
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
            sb.insert(0, KeyNames.getName(iMod) + "+");
        }

        private void captureAxisAndButtonMappings(final InputMapper inputMapper, final List<String> names) {
            for (final Mapping m : inputMapper.getMappings(function)) {
                final Object o = m.getPrimaryActivator();
                final String primary;
                if (o instanceof Button) {
                    primary = ((Button) o).getName();
                } else if (o instanceof Axis) {
                    primary = ((Axis) o).getName();
                } else {
                    // Not a mapping we can deal with — handled in the first pass.
                    continue;
                }

                final StringBuilder sb = new StringBuilder(primary);
                for (final Object mod : m.getModifiers()) {
                    if (mod instanceof Integer) {
                        sb.append('+');
                        sb.append(KeyNames.getName(((Integer) mod).intValue()));
                    }
                }
                names.add(sb.toString());
            }
        }
    }
}
