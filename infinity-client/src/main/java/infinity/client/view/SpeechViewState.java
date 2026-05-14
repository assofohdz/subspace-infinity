/*
 * $Id$
 *
 * Copyright (c) 2021, Simsilica, LLC
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

import infinity.client.ConnectionState;
import infinity.client.states.ModelViewState;
import infinity.es.Speech;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.bounding.BoundingBox;
import com.jme3.bounding.BoundingVolume;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.font.Rectangle;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.control.BillboardControl;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.common.Decay;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.lemur.GuiGlobals;
/**
 * Renders chat-bubble {@link BitmapText} above each entity with a
 * {@code Speech} component. Bubble lifetime is driven by the entity's
 * {@link com.simsilica.es.common.Decay}; alpha + Y-rise are interpolated
 * against {@code Decay.getPercentRemaining(time)} below 50% remaining.
 *
 * <p>Position tracks the speaker's spatial each frame (resolved via
 * {@link infinity.client.states.ModelViewState#getModel}, not
 * {@code ed.getComponent}). Multiple bubbles for the same speaker stack
 * upward via {@code lineOffset}.
 */
public class SpeechViewState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(SpeechViewState.class);

    private EntityData ed;
    private SpeechContainer bubbles;
    private TimeSource timeSource;
    private Node root;
    private BitmapFont font;

    private ModelViewState models;

    private ListMultimap<EntityId, SpeechBubble> activeBubbles;

    public SpeechViewState() {
        // no-op: default ctor; state is wired in initialize()
    }

    protected Node getRoot() {
        return ((SimpleApplication)getApplication()).getRootNode();
    }

    protected Node getSpeechRoot() {
        return root;
    }

    @Override
    protected void initialize( final Application app ) {
        this.ed = getState(ConnectionState.class, true).getEntityData();
        this.timeSource = getState(ConnectionState.class, true).getRemoteTimeSource();
        this.models = getState(ModelViewState.class, true);
        this.root = new Node("speech");

        this.font = GuiGlobals.getInstance().loadFont("Interface/Fonts/Default.fnt");

        this.activeBubbles = MultimapBuilder.hashKeys().arrayListValues().build();
    }

    @Override
    protected void cleanup( final Application app ) {
        if (bubbles != null) {
            bubbles.stop();
            bubbles = null;
        }
    }

    @Override
    protected void onEnable() {
        getRoot().attachChild(root);
        this.bubbles = new SpeechContainer(ed);
        bubbles.start();
    }

    @Override
    protected void onDisable() {
        root.removeFromParent();
        if (bubbles != null) {
            bubbles.stop();
            bubbles = null;
        }
    }

    @Override
    public void update( final float tpf ) {
        bubbles.update();
        long time = timeSource.getTime();
        for( final SpeechBubble bubble : bubbles.getArray() ) {
            bubble.update(time);
        }
    }

    private class SpeechBubble {
        private EntityId speaker;
        private Decay decay;

        private Spatial model;
        private BitmapText spatial;
        private float yOffset;
        private float lineOffset = 0;

        public SpeechBubble(final Speech speech, final Decay decay ) {
            this.decay = decay;
            this.speaker = speech.getSpeaker();
            final String text = speech.getText();
            final ColorRGBA color = new ColorRGBA(1, 1, 0, 1);

            this.spatial = new BitmapText(font);

            // BitmapText is such a ludicrously difficult class to use
            float width = font.getLineWidth(text)/font.getPreferredSize();

            spatial.setSize(0.1f);
            spatial.setText(text);
            spatial.setColor(color);
            spatial.setBox(new Rectangle(-width * 0.5f, 0, width, 0.1f));
            spatial.setAlignment(BitmapFont.Align.Center);
            spatial.addControl(new BillboardControl());
            root.attachChild(spatial);

            // Adjust the offset of any existing bubbles for the entity ID
            for( final SpeechBubble bubble : activeBubbles.get(speaker) ) {
                bubble.lineOffset += 0.1f;
            }
            activeBubbles.put(speaker, this);
        }

        protected void update( final Speech speech ) {
            // no-op: Speech text is rendered at construction and never updates in-place; bubble decays via Decay
        }

        public void update( final long time ) {
            if( model == null ) {
                // Try to load the model
                model = models.getModel(speaker);
                if( model != null ) {
                    BoundingVolume bv = model.getWorldBound();
                    if( bv instanceof BoundingBox ) {
                        yOffset = ((BoundingBox)bv).getYExtent();
                    }
                }
            }
            if( model != null ) {
                spatial.setLocalTranslation(model.getWorldTranslation().add(0, yOffset + lineOffset, 0));
            }

            double left = decay.getPercentRemaining(time);
            if( left < 0.5 ) {
                double alpha = left / 0.5;
                alpha = alpha * alpha;
                spatial.setAlpha((float)alpha);
                spatial.move(0, (float)(1 - alpha) * 0.1f, 0);
            }

        }

        public void release() {
            spatial.removeFromParent();
            activeBubbles.remove(speaker, this);
        }
    }

    private class SpeechContainer extends EntityContainer<SpeechBubble> {

        public SpeechContainer( final EntityData ed ) {
            super(ed, Speech.class, Decay.class);
        }

        public SpeechBubble[] getArray() {
            return (SpeechBubble[])super.getArray();
        }

        protected SpeechBubble addObject( final Entity e ) {
            Speech speech = e.get(Speech.class);
            Decay decay = e.get(Decay.class);
log.info("New speech:{}", speech);
            return new SpeechBubble(speech, decay);
        }

        protected void updateObject( final SpeechBubble object, final Entity e ) {
            object.update(e.get(Speech.class));
        }

        protected void removeObject( final SpeechBubble object, final Entity e ) {
            object.release();
        }
    }
}
