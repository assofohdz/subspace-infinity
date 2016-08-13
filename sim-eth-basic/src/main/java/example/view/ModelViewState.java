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

import java.util.*;
import java.util.concurrent.*;

import org.slf4j.*;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.asset.AssetManager;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.material.Material;
import com.jme3.scene.*;
import com.jme3.texture.Texture;
import com.jme3.util.SafeArrayList;

import com.simsilica.lemur.GuiGlobals;

import example.ConnectionState;
import example.GameSessionState;
import example.Main;
import example.net.GameSessionListener;
import example.net.client.GameSessionClientService;

/**
 *  Displays the models for the various physics objects.
 *
 *  @author    Paul Speed
 */
public class ModelViewState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(ModelViewState.class);

    private Node modelRoot;
    
    private GameSessionObserver gameSessionObserver = new GameSessionObserver();
    private Map<Integer, ObjectInfo> index = new ConcurrentHashMap<>();
    private ConcurrentLinkedQueue<ObjectInfo> toAdd = new ConcurrentLinkedQueue<>();
    private ConcurrentLinkedQueue<Integer> toRemove = new ConcurrentLinkedQueue<>();   

    private Map<Integer, Spatial> models = new HashMap<>();
    private SafeArrayList<ObjectInfo> objects = new SafeArrayList<>(ObjectInfo.class); 

    public ModelViewState() {
    }

    @Override
    protected void initialize( Application app ) {
        modelRoot = new Node();
    
        getState(ConnectionState.class).getService(GameSessionClientService.class).addGameSessionListener(gameSessionObserver);                
    }

    @Override
    protected void cleanup(Application app) {
    }

    @Override
    protected void onEnable() {
        ((Main)getApplication()).getRootNode().attachChild(modelRoot);
    }

    @Override
    protected void onDisable() {
        modelRoot.removeFromParent();
    }
    
    protected void addModel( ObjectInfo info ) {
 
        Spatial model = createShip(info.clientId, info.playerName, info.shipId); 
        models.put(info.shipId, model);       
        modelRoot.attachChild(model);
        
        info.spatial = model;
        objects.add(info);
        
        // If the ship is our ship then we'll hide it else it looks bad.
        if( info.shipId == getState(GameSessionState.class).getShipId() ) {
            model.setCullHint(Spatial.CullHint.Always);
        }
    }
    
    protected void removeModel( int id ) {
        ObjectInfo info = index.remove(id);
        Spatial spatial = models.remove(id);
        if( spatial != null ) {
            spatial.removeFromParent();
        }
    }

    @Override
    public void update( float tpf ) {
        if( !toAdd.isEmpty() ) {
            ObjectInfo info;
            while( (info = toAdd.poll()) != null ) {
                addModel(info);
            }
        }
        if( !toRemove.isEmpty() ) {
            Integer id;
            while( (id = toRemove.poll()) != null ) {
                removeModel(id);
            }
        }
        
        // Update all of the models
        for( ObjectInfo info : objects.getArray() ) {
            info.updateSpatial();
        }
    }

    protected Spatial createShip( int clientId, String playerName, int shipId ) {
    
        AssetManager assetManager = getApplication().getAssetManager();
        
        Spatial ship = assetManager.loadModel("Models/fighter.j3o");
        ship.center();
        Texture texture = assetManager.loadTexture("Textures/ship1.png");
        Material mat = GuiGlobals.getInstance().createMaterial(texture, false).getMaterial();
        ship.setMaterial(mat);
 
        Node result = new Node("ship:" + shipId);
        result.attachChild(ship);        
 
        result.setUserData("clientId", clientId);
        result.setUserData("playerName", playerName);
        result.setUserData("objectId", shipId);
        
        return result;
    }
     
    private class ObjectInfo {
        int clientId;
        String playerName;
        int shipId;
        Spatial spatial;
        
        volatile Vector3f updatePos;
        volatile Quaternion updateRot;
        
        public ObjectInfo( int clientId, String playerName, int shipId ) {
            this.clientId = clientId;
            this.playerName = playerName;
            this.shipId = shipId;
        }
        
        public void updateSpatial() {
            Vector3f pos = updatePos;
            Quaternion rot = updateRot;
            if( pos == null || rot == null ) {
                return; // no update info yet
            }
            
            spatial.setLocalTranslation(pos);
            spatial.setLocalRotation(rot);
        }        
    }
     
    private class GameSessionObserver implements GameSessionListener {
 
        @Override
        public void playerJoined( int clientId, String playerName, int shipId ){
            ObjectInfo info = new ObjectInfo(clientId, playerName, shipId);
            index.put(shipId, info); 
            toAdd.add(info);
        }
    
        @Override
        public void playerLeft( int clientId, String playerName, int shipId ) {           
            toRemove.add(shipId);
        }
        
        @Override
        public void updateObject( int objectId, Quaternion orientation, Vector3f pos ) {
            ObjectInfo info = index.get(objectId);
            if( info != null ) {
                info.updatePos = pos;
                info.updateRot = orientation;
            }
        }
    }
}


