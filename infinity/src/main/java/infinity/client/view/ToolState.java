/*
 * $Id$
 *
 * Copyright (c) 2019, Simsilica, LLC
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

package infinity.client.view;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;

import com.simsilica.es.EntityId;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.input.FunctionId;
import com.simsilica.lemur.input.InputMapper;
import com.simsilica.lemur.input.InputState;
import com.simsilica.lemur.input.StateFunctionListener;

import infinity.client.ConnectionState;
import infinity.client.GameSessionClientService;
import infinity.net.GameSession;

/**
 * Manages the mouse/tool interaction with the scene/world.
 *
 * @author Paul Speed
 */
public class ToolState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(ToolState.class);

    private Spatial cursor;
    private int size = 48;

    private ToolListener toolListener = new ToolListener();

    private GameSession gameSession;

    private ModelViewState models;

    public ToolState() {
    }

    protected Node getGuiNode() {
        return ((SimpleApplication) getApplication()).getGuiNode();
    }

    @Override
    protected void initialize(Application app) {

        models = getState(ModelViewState.class);

        GuiGlobals globals = GuiGlobals.getInstance();

        Quad quad = new Quad(size, size);
        Texture texture = globals.loadTexture("Interface/glass-orb-dark-48.png", false, false);

        cursor = new Geometry("cursor", quad);
        Material mat = globals.createMaterial(texture, false).getMaterial();
        mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
        cursor.setMaterial(mat);
    }

    @Override
    protected void cleanup(Application app) {
    }

    @Override
    protected void onEnable() {
        resetCursorPosition();
        getGuiNode().attachChild(cursor);

        gameSession = getState(ConnectionState.class).getService(GameSessionClientService.class);

        InputMapper input = GuiGlobals.getInstance().getInputMapper();
        input.addStateListener(toolListener, ToolFunctions.F_MAIN_TOOL, ToolFunctions.F_ALT_TOOL);
    }

    @Override
    protected void onDisable() {
        cursor.removeFromParent();
        InputMapper input = GuiGlobals.getInstance().getInputMapper();
        input.removeStateListener(toolListener, ToolFunctions.F_MAIN_TOOL, ToolFunctions.F_ALT_TOOL);
    }

    protected void resetCursorPosition() {
        int width = getApplication().getCamera().getWidth();
        int height = getApplication().getCamera().getHeight();
        cursor.setLocalTranslation(width * 0.5f - size * 0.5f, height * 0.5f - size * 0.5f, 0);
    }

    @Override
    public void update(float tpf) {
    }

    private class ToolListener implements StateFunctionListener {

        private EntityId heldEntity;

        @Override
        public void valueChanged(FunctionId func, InputState value, double tpf) {
            log.info("valueChanged(" + func + ", " + value + ", " + tpf + ")");

            if (func == ToolFunctions.F_MAIN_TOOL) {
                // Right now we'll just hard-code some tools
                if (value == InputState.Positive) {

                    // See if we can grab anything
                    PickedObject po = models.pickObject();
                    if (po != null) {
                        // gameSession.startHolding(po.entityId, po.location);
                        heldEntity = po.entityId;
                    } else {
                        // Just in case
                        heldEntity = null;
                    }
                } else {
                    // gameSession.stopHolding(heldEntity);
                    heldEntity = null;
                }
            }
        }
    }
}
