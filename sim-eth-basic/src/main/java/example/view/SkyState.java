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

package example.view;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.scene.Spatial;
import com.jme3.texture.Texture;
import com.jme3.util.SkyFactory;

import example.Main;

/**
 *
 *
 *  @author    Paul Speed
 */
public class SkyState extends BaseAppState {

    private Spatial sky;

    public SkyState() {
    }
    
    @Override
    protected void initialize( Application app ) {

        Texture texture1 = app.getAssetManager().loadTexture("Textures/galaxy+Z.jpg");
        Texture texture2 = app.getAssetManager().loadTexture("Textures/galaxy-Z.jpg"); 
        Texture texture3 = app.getAssetManager().loadTexture("Textures/galaxy+X.jpg"); 
        Texture texture4 = app.getAssetManager().loadTexture("Textures/galaxy-X.jpg"); 
        Texture texture5 = app.getAssetManager().loadTexture("Textures/galaxy+Y.jpg"); 
        Texture texture6 = app.getAssetManager().loadTexture("Textures/galaxy-Y.jpg");
 
        sky = SkyFactory.createSky(app.getAssetManager(),
                                   texture1, texture2,
                                   texture3, texture4,
                                   texture5, texture6);    
    }

    @Override
    protected void cleanup( Application app ) {
    }
    
    @Override
    protected void onEnable() {
        ((Main)getApplication()).getRootNode().attachChild(sky);
    }
    
    @Override
    protected void onDisable() {
        sky.removeFromParent();
    }
}


