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
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.post.FilterPostProcessor;
import com.jme3.system.AppSettings;

import com.simsilica.fx.shadow.DropShadowFilter;

/**
 *
 *
 *  @author    Paul Speed
 */
public class PostProcessingState extends BaseAppState {

    private FilterPostProcessor fpp;
    private DropShadowFilter shadows;
    private float shadowStrength = 0.7f;
    
    public PostProcessingState() {
    }

    public void setEnableShadows( boolean b ) {
        shadows.setEnabled(b);
    }

    public void setShadowStrength( float f ) {
        if( this.shadowStrength == f ) {
            return;
        }
        this.shadowStrength = f;
        resetShadowStrength();
    }
    
    public float getShadowStrength() {
        return shadowStrength;
    }

    protected void resetShadowStrength() {
        shadows.setShadowIntensity(shadowStrength);
    }

    @Override
    protected void initialize( Application app ) {
    
        AssetManager assets = app.getAssetManager();
        
        fpp = new FilterPostProcessor(assets);
        AppSettings settings = app.getContext().getSettings();
        if( settings.getSamples() != 0 ) {
            fpp.setNumSamples(settings.getSamples());
        }

        shadows = new DropShadowFilter();
        shadows.setEnabled(true);
        //shadows.setShowBox(true);
        fpp.addFilter(shadows);
        
        resetShadowStrength();                   
    }

    @Override
    protected void cleanup( Application app ) {
    }

    @Override
    protected void onEnable() {
        getApplication().getViewPort().addProcessor(fpp);        
    }

    @Override
    public void update( float tpf ) {
    }

    @Override
    protected void onDisable() {
        getApplication().getViewPort().removeProcessor(fpp);        
    }
}
