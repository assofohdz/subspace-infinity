/*
 * $Id$
 *
 * Copyright (c) 2018, Simsilica, LLC
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

import infinity.client.view.BlockGeometryIndex;
import infinity.client.view.InfinityBlockGeometryIndex;
import infinity.client.view.LeafDataZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jme3.app.Application;
import com.jme3.app.SimpleApplication;
import com.jme3.app.state.BaseAppState;
import com.jme3.material.Material;
import com.jme3.material.RenderState.BlendMode;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

import com.simsilica.builder.Builder;
import com.simsilica.builder.BuilderState;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mworld.CellChangeEvent;
import com.simsilica.mworld.CellChangeListener;
import com.simsilica.mworld.World;
import com.simsilica.mworld.net.client.WorldClientService;
import com.simsilica.pager.Grid;
import com.simsilica.pager.PagedGrid;
import com.simsilica.pager.ZoneFactory;
import com.simsilica.pager.debug.BBoxZone;

import infinity.client.ConnectionState;

/**
 *
 *
 * @author Paul Speed
 */
public class WorldViewState extends BaseAppState {

    static Logger log = LoggerFactory.getLogger(WorldViewState.class);

    private World world;
    private final CellObserver cellObserver = new CellObserver();

    private PagedGrid pager;
    private Node worldRoot;

    private final Vector3f viewLoc = new Vector3f(20, InfinityCameraState.DISTANCETOPLANE, 20);
    private final Vector3f viewCell = new Vector3f();

    private BlockGeometryIndex geomIndex;

    public WorldViewState() {
    }

    public void setViewLocation(final Vector3f viewLoc) {
        // viewLoc = viewLoc.setY(30);
        this.viewLoc.set(viewLoc);
        //log.info("setViewLocation:: viewLoc is now: "+viewLoc);
        if (pager != null) {
            pager.setCenterWorldLocation(viewLoc.x, viewLoc.z);

            pager.getGrid().toCell(viewLoc, viewCell);

            // The grid may be in 3D but the pager considers it a 2D grid for the
            // sake of center placement.
            viewCell.y = 0;
            //log.info("setViewLocation:: viewCell is now: "+viewCell);
        }
    }

    public Vector3f getViewLocation() {
        //log.info("getViewLocation:: Getting viewLoc: "+viewLoc);
        return viewLoc;
    }

    public Vector3f getViewCell() {
        //log.info("getViewCell:: Getting viewCell: "+viewCell);
        return viewCell;
    }

    public Vector3f cellToWorld(final Vec3i cell, final Vector3f target) {
        if (pager == null) {
            return target;
        }
        return pager.getGrid().toWorld(cell.x, cell.y, cell.z, target);
    }

    @Override
    protected void initialize(final Application app) {

        geomIndex = new BlockGeometryIndex(app.getAssetManager());

        world = getState(ConnectionState.class).getService(WorldClientService.class);
        // log.info("World:" + world);

        // LeafData data = world.getLeaf(0);
        // log.info("Data for leafId 0:" + data);

        // data = world.getLeaf(new Vec3i(0, 2, 0));
        // log.info("Data for leaf 0, 2, 0:" + data);

        final Builder builder = getState(BuilderState.class).getBuilder();

        final Grid rootGrid = new Grid(new Vector3f(32, 32, 32), new Vector3f(0, 0, 0));


        final ZoneFactory rootFactory = new LeafDataZone.Factory(world, geomIndex);

        pager = new PagedGrid(rootFactory, builder, rootGrid, 1, 5);

        worldRoot = new Node("worldRoot");
        worldRoot.attachChild(pager.getGridRoot());

        final boolean showGrid = true;
        if (showGrid) {
            final Material boxMaterial = GuiGlobals.getInstance()
                    .createMaterial(new ColorRGBA(0.2f, 0.6f, 0.4f, 0.25f), false).getMaterial();
            boxMaterial.getAdditionalRenderState().setBlendMode(BlendMode.Alpha);
            boxMaterial.getAdditionalRenderState().setWireframe(true);
            final ZoneFactory gridFactory = new BBoxZone.Factory(boxMaterial);

            final PagedGrid gridPager = new PagedGrid(pager, gridFactory, builder, rootGrid, 1, 5);
            worldRoot.attachChild(gridPager.getGridRoot());
        }

        world.addCellChangeListener(cellObserver);

    }

    protected void cellChanged(final CellChangeEvent event) {
        //log.info("cellChanged(" + event + ")");
        final Vec3i loc = event.getLeafWorld();
        pager.rebuildCell(loc.x, loc.y, loc.z);
    }

    @Override
    protected void cleanup(final Application app) {
        world.removeCellChangeListener(cellObserver);
        pager.release();
    }

    /*
     * public void update( float tpf ) { // For now just directly forward the camera
     * location Vector3f loc = getApplication().getCamera().getLocation();
     *
     * pager.setCenterWorldLocation(loc.x, loc.z); }
     */

    @Override
    protected void onEnable() {
        ((SimpleApplication) getApplication()).getRootNode().attachChild(worldRoot);

        pager.setCenterWorldLocation(viewLoc.x, viewLoc.z);
        getApplication().getCamera().setLocation(new Vector3f(0, viewLoc.y, 0));
        getApplication().getCamera().lookAt(new Vector3f(0,0,0), Vector3f.UNIT_Y);
    }

    @Override
    protected void onDisable() {
        worldRoot.removeFromParent();
    }

    private class CellObserver implements CellChangeListener {

        @Override
        public void cellChanged(final CellChangeEvent event) {
            WorldViewState.this.cellChanged(event);
//BlockGeometryIndex.debug = true;
        }
    }

    public BlockGeometryIndex getGeomIndex() {
        return geomIndex;
    }
}
