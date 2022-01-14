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

package infinity.client.view;

import org.slf4j.*;

import com.jme3.scene.*;

import com.simsilica.builder.*;
import com.simsilica.pager.*;

import com.simsilica.mworld.*;

/**
 *  Integrated leaf data geometry generation into the PagedGrid system.
 *
 *  @author    Paul Speed
 */
public class LeafDataZone extends AbstractZone {
    static Logger log = LoggerFactory.getLogger(LeafDataZone.class);
    
    private World world;
    private LeafId leafId;
    private Node leafNode;
    private Node lastLeafNode;    
    private BlockGeometryIndex geomIndex;
    
    public LeafDataZone( World world, Grid grid, int xCell, int yCell, int zCell, BlockGeometryIndex geomIndex ) {
        super(grid, xCell, yCell, zCell);
        this.world = world;
        // Hack for now... the whole paged grid thing is not the way anyway anymore
        this.leafId = LeafId.fromWorld(xCell * 32, yCell * 32, zCell * 32);
        this.geomIndex = geomIndex;
    }
    
    @Override
    public void build() {  
//log.info("build():" + leafId);
        LeafData leaf = world.getLeaf(leafId);
        if( log.isTraceEnabled() ) {
            log.trace("build() leaf empty cells:" + leaf.getEmptyCellCount() + "  isEmpty:" + leaf.isEmpty() + "  cells:" + leaf.getRawCells());
        }        
        if( leaf.isEmpty() ) {
            //System.out.println("--- skipping:" + leafId);
            return;
        }
 
        lastLeafNode = leafNode;       
        leafNode = new Node("leaf:" + leafId);
        geomIndex.generateBlocks(leafNode, leaf.getRawCells()); 
    }
     
    @Override
    public void apply( Builder builder ) {
//log.info("apply():" + leafId);
        if( lastLeafNode != null ) {
            lastLeafNode.removeFromParent();
        }
        if( leafNode != null ) {
            getZoneRoot().attachChild(leafNode);
        }
    }

    @Override
    public void release( Builder builder ) {
//log.info("release():" + leafId);
        //Mesh mesh = boxGeom.getMesh();
        //for( VertexBuffer vb : mesh.getBufferList() ) {
        //    if( log.isDebugEnabled() ) {
        //        log.debug("--destroying buffer:" + vb);
        //    }
        //    BufferUtils.destroyDirectBuffer( vb.getData() );
        //}        
    }
    
    public static class Factory implements ZoneFactory {
        private World world;
        private BlockGeometryIndex geomIndex; 
        
        public Factory( World world, BlockGeometryIndex geomIndex ) {
            this.world = world;;
            this.geomIndex = geomIndex;
        }
        
        @Override
        public Zone createZone( PagedGrid pg, int xCell, int yCell, int zCell ) {
            Zone result = new LeafDataZone(world, pg.getGrid(), xCell, yCell, zCell, geomIndex);
            return result;   
        }        
    }
}

