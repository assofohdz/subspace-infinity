/* 
 * Copyright (c) 2018, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package infinity.client;

import infinity.client.view.ModelViewState;
import com.jme3.app.Application;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.scene.Spatial;
import com.simsilica.lemur.event.BaseAppState;

/**
 * A state to manage in-game camera 
 * @author Asser
 */
public class CameraState extends BaseAppState{

    public static final float DISTANCETOPLANE = 60;
    private Camera camera;

    public Camera getCamera() {
        return camera;
    }
    private ModelViewState models;
    private Spatial playerShip;
    

    @Override
    protected void initialize(Application app) {
        this.camera = app.getCamera();
        
        this.camera.setLocation(new Vector3f(0,0,DISTANCETOPLANE));
        this.camera.lookAt(new Vector3f(0,0,0), Vector3f.UNIT_Z); //Set camera to look at the origin
        
        this.models = getState(ModelViewState.class);
        this.playerShip = models.getPlayerSpatial();
    }

    @Override
    protected void cleanup(Application app) {
    }

    @Override
    protected void enable() {
    }

    @Override
    protected void disable() {
    }
    
    @Override
    public void update(float tpf) {
        
        
        if( playerShip != null ) {
            camera.setLocation(playerShip.getWorldTranslation().add(0,0,DISTANCETOPLANE));  //Set camera position above spatial - Z is up
            camera.lookAt(playerShip.getWorldTranslation(), Vector3f.UNIT_Z); //Set camera to look at the spatial
        }
        else //Probably a crude way to do it - should be handled properly
        {
            this.playerShip = models.getPlayerSpatial(); 
        }
    }
    
    public void setPlayerShip(Spatial s){
        this.playerShip = s;
    }
}
