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

package com.simsilica.demo.view;

import org.slf4j.*;

import com.google.common.collect.*;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.bounding.*;
import com.jme3.font.*;
import com.jme3.font.Rectangle;
import com.jme3.math.*;
import com.jme3.scene.*;
import com.jme3.scene.control.BillboardControl;

import com.simsilica.es.*;
import com.simsilica.es.common.*;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.lemur.GuiGlobals;

import com.simsilica.demo.client.ConnectionState;
import com.simsilica.demo.es.*;

/**
 *
 *
 *  @author    Paul Speed
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
    }
 
    protected Node getRoot() {
        return ((SimpleApplication)getApplication()).getRootNode();
    }

    protected Node getSpeechRoot() {
        return root;
    }
    
    @Override
    protected void initialize( Application app ) {
        this.ed = getState(ConnectionState.class, true).getEntityData();
        this.timeSource = getState(ConnectionState.class, true).getRemoteTimeSource();
        this.models = getState(ModelViewState.class, true);
        this.root = new Node("speech");
        
        this.font = GuiGlobals.getInstance().loadFont("Interface/Fonts/Default.fnt");
        
        this.activeBubbles = MultimapBuilder.hashKeys().arrayListValues().build();
    }
    
    @Override
    protected void cleanup( Application app ) {
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
        bubbles.stop();
    }
    
    @Override
    public void update( float tpf ) {
        bubbles.update();
        long time = timeSource.getTime();
        for( SpeechBubble bubble : bubbles.getArray() ) {
            bubble.update(time);
        }
    }
 
    private class SpeechBubble {
        private EntityId entityId;
        private EntityId speaker;
        private String text;
        private Decay decay;
 
        private Spatial model;       
        private BitmapText spatial;
        private ColorRGBA color = new ColorRGBA(1, 1, 0, 1);
        private float yOffset;
        private float lineOffset = 0;
 
        public SpeechBubble( EntityId entityId, Speech speech, Decay decay ) {
            this.entityId = entityId;
            this.decay = decay; 
            this.speaker = speech.getSpeaker();
            this.text = speech.getText();
 
            this.spatial = new BitmapText(font);
            
            // BitmapText is such a ludicrously difficult class to use
            float width = font.getLineWidth(text)/font.getPreferredSize();
 
//log.info("width:" + width + "  font size:" + font.getPreferredSize());            
            spatial.setSize(0.1f);
            spatial.setText(text);
            spatial.setColor(color);
            //spatial.setLineWrapMode(LineWrapMode.NoWrap);
            spatial.setBox(new Rectangle(-width * 0.5f, 0, width, 0.1f));
            spatial.setAlignment(BitmapFont.Align.Center);
            spatial.addControl(new BillboardControl());
            root.attachChild(spatial);
            
            // Adjust the offset of any existing bubbles for the entity ID
            for( SpeechBubble bubble : activeBubbles.get(speaker) ) {
                bubble.lineOffset += 0.1f;
            }
            activeBubbles.put(speaker, this);
        }        
        
        protected void update( Speech speech ) {
        }
 
        public void update( long time ) {
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
            
        public SpeechContainer( EntityData ed ) {
            super(ed, Speech.class, Decay.class);
        }

        public SpeechBubble[] getArray() {
            return (SpeechBubble[])super.getArray();
        }
        
        protected SpeechBubble addObject( Entity e ) {
            Speech speech = e.get(Speech.class);
            Decay decay = e.get(Decay.class); 
log.info("New speech:" + speech);             
            return new SpeechBubble(e.getId(), speech, decay);
        }
        
        protected void updateObject( SpeechBubble object, Entity e ) {
            object.update(e.get(Speech.class));
        }
        
        protected void removeObject( SpeechBubble object, Entity e ) {
            object.release();
        }
    }     
}
