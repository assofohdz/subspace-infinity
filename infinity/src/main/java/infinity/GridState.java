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

import com.jme3.app.*;
import com.jme3.app.state.*;
import com.jme3.app.state.BaseAppState;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.material.RenderState.FaceCullMode;
import com.jme3.math.*;
import com.jme3.renderer.Camera;
import com.jme3.renderer.queue.RenderQueue.Bucket;
import com.jme3.scene.*;
import com.jme3.scene.VertexBuffer.Type;
import com.jme3.scene.debug.WireBox;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Texture;

import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.event.*;
import com.simsilica.lemur.input.*;

import com.simsilica.mathd.Vec3i;
import com.simsilica.mathd.Grid;

/**
 *  A standard app state for displaying a local-relative x,z grid at y=0.
 *
 *  @author    Paul Speed
 */
public class GridState extends BaseAppState {
 
    private Grid grid;
 
    private Node floor;
    private Geometry wireFloor;
    private Geometry flatFloor;
 
    private Camera camera;
    private Vec3i pos = new Vec3i();
    private Vec3i lastPos = new Vec3i();
 
    private int floorGridSize = 100;
    
    private ColorRGBA gridColor = new ColorRGBA(0.5f, 0.75f, 0.75f, 1);
    private ColorRGBA cellColor = new ColorRGBA(0.5f, 0.45f, 0.45f, 0.25f);  
    private ColorRGBA boxColor = new ColorRGBA(1, 1, 0, 0.45f);
    //private ColorRGBA boxColor = new ColorRGBA(1, 1, 0, 1f);
 
    private Node cellRoot;
 
    public GridState( Grid grid ) {
        this.grid = grid;
    }
 
    protected Node getRoot() {
        return ((SimpleApplication)getApplication()).getRootNode();
    }
    
    protected Camera getCamera() {
        if( camera == null ) {
            camera = getApplication().getCamera();
        }
        return camera;
    }
    
    @Override   
    protected void initialize( Application app ) {
 
        this.floor = new Node("grid");
 
        GuiGlobals globals = GuiGlobals.getInstance();       
        {
            com.jme3.scene.debug.Grid mesh = new com.jme3.scene.debug.Grid(floorGridSize + 1, floorGridSize + 1, 1);
            this.wireFloor = new Geometry("grid-lines", mesh);
            Material mat = globals.createMaterial(gridColor, false).getMaterial();
            mat.getAdditionalRenderState().setDepthWrite(false);
            wireFloor.setMaterial(mat);
            wireFloor.setLocalTranslation(-(floorGridSize * 0.5f), 0, -(floorGridSize * 0.5f));
            wireFloor.setUserData("layer", 1);
            wireFloor.setQueueBucket(Bucket.Transparent);
            floor.attachChild(wireFloor);
        }
        
        { 
            Quad mesh = new Quad(floorGridSize, floorGridSize);
            mesh.scaleTextureCoordinates(new Vector2f(floorGridSize, floorGridSize));
            Texture gridTexture = globals.loadTexture("Interface/grid-cell.png", true, false);
            Material mat = globals.createMaterial(gridTexture, false).getMaterial();
            this.flatFloor = new Geometry("grid-quads", mesh);
            mat.setColor("Color", cellColor);
            mat.getAdditionalRenderState().setFaceCullMode(FaceCullMode.Off);
            mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
            //mat.getAdditionalRenderState().setDepthWrite(false);
            flatFloor.setMaterial(mat);
            flatFloor.setQueueBucket(Bucket.Transparent);
        
            flatFloor.rotate(-FastMath.HALF_PI, 0, 0);
            flatFloor.setLocalTranslation(-(floorGridSize * 0.5f), -0.001f, (floorGridSize * 0.5f));
            flatFloor.setUserData("layer", 2);
            floor.attachChild(flatFloor);
        }
        
        {
            // Create the 3D grid cell zones.  We know we are only using a 2D
            // grid right now so we'll simplify and simply give a reasonable max
            // vertical bounds
            cellRoot = new Node("cellRoot");
        
            Texture texture = globals.loadTexture("Interface/bottom-corners.png", true, true);    
            Material mat = globals.createMaterial(boxColor, false).getMaterial();
            mat.setTexture("ColorMap", texture);
            mat.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
            mat.getAdditionalRenderState().setDepthWrite(false);
            //mat.setFloat("AlphaDiscardThreshold", 0.05f);
 
            float xHalf = grid.getSpacing().x * 0.5f;
            float zHalf = grid.getSpacing().z * 0.5f;
            float height = 2f;                     
            WireBox box = new WireBox(xHalf, height, zHalf);
            box.setBuffer(Type.TexCoord, 2, new float[]{0, 0,
                                                        1, 0,
                                                        1, 0.5f,
                                                        0, 0.5f,
                                                        
                                                        1, 0,
                                                        0, 0,
                                                        0, 0.5f,
                                                        1, 0.5f});
            int radius = 4;                                                        
            for( int x = -radius; x <= radius; x++ ) {
                for( int z = -radius; z <= radius; z++ ) {
                    Geometry geom = new Geometry("cell[" + x + ", " + z + "]", box);
                    geom.setMaterial(mat);
                    geom.setLocalTranslation(x * grid.getSpacing().x + xHalf,
                                             height, // + 0.01f,
                                             z * grid.getSpacing().z + zHalf);
                                              
                    geom.setQueueBucket(Bucket.Transparent);
                    geom.setUserData("layer", 3);
                    cellRoot.attachChild(geom);                        
                }
            }
        }
    }
        
    @Override   
    protected void cleanup( Application app ) {
    }
    
    @Override   
    protected void onEnable() {
        getRoot().attachChild(floor);
        getRoot().attachChild(cellRoot);
    }
    
    @Override   
    protected void onDisable() {
        floor.removeFromParent();
        cellRoot.removeFromParent();
    }
 
    @Override
    public void update( float tpf ) {
        Vector3f cameraPos = getCamera().getLocation();
        pos.x = (int)Math.floor(cameraPos.x);       
        pos.z = (int)Math.floor(cameraPos.z);
        
        if( pos.x != lastPos.x || pos.z != lastPos.z ) {
            lastPos.set(pos);
            floor.setLocalTranslation(pos.x, 0, pos.z); 
            
            // Get our latest grid location and make sure the zones are
            // set right, too.
            Vec3i cell = grid.worldToCell(cameraPos.x, cameraPos.y, cameraPos.z);
            Vec3i world = grid.cellToWorld(cell);
            cellRoot.setLocalTranslation(world.x, world.y, world.z); 
        }       
    }

}
