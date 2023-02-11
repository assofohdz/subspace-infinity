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

package infinity.sim;

import com.simsilica.mblock.CellArray;
import com.simsilica.mworld.base.LeafChangeListenerSupport;
import com.simsilica.mworld.base.WorldCellData;
import java.util.*;

import org.slf4j.*;

import com.simsilica.mathd.*;

import com.simsilica.mblock.MaskUtils;
import com.simsilica.mworld.*;
import com.simsilica.mworld.db.*;
import com.simsilica.mworld.tile.*;
import com.simsilica.mworld.tile.pc.PointCloudLayer;
import com.simsilica.mworld.tile.tree.TreeLayer;

/**
 * Provided for interrum backwards partial compatibility for older apps/demos that haven't been
 * converted to ColumnDb yet. Some functions do not work.
 *
 * @author Paul Speed
 */
public class InfinityDefaultLeafWorld implements World {

  static Logger log = LoggerFactory.getLogger(InfinityDefaultLeafWorld.class);

  private final LeafDb leafDb;
  private final int yMax;

  private final List<CellChangeListener> cellListeners = new ArrayList<>();
  private CellChangeListener[] cellListenerArray;
  private final CellChangeListener[] emptyCellListenerArray = new CellChangeListener[0];

  private final LeafChangeListenerSupport leafListeners = new LeafChangeListenerSupport();

  public InfinityDefaultLeafWorld(LeafDb leafDb, int yMax) {
    this.leafDb = leafDb;
    this.yMax = yMax;
  }

  @Override
  public void addCellChangeListener(CellChangeListener l) {
    cellListeners.add(l);
    cellListenerArray = null;
  }

  @Override
  public void removeCellChangeListener(CellChangeListener l) {
    cellListeners.remove(l);
    cellListenerArray = null;
  }

  @Override
  public void addLeafChangeListener(LeafChangeListener l) {
    leafListeners.add(l);
  }

  @Override
  public void removeLeafChangeListener(LeafChangeListener l) {
    leafListeners.remove(l);
  }

  @Override
  public int getMaxY() {
    return yMax;
  }

  protected CellChangeListener[] getCellListenerArray() {
    if (cellListenerArray == null) {
      cellListenerArray = cellListeners.toArray(emptyCellListenerArray);
    }
    return cellListenerArray;
  }

  //    protected void fireCellChanged( LeafId leafId, int x, int y, int z, int value ) {
  //// log.info("fireCellChanged(" + leafId + ", " + x + ", " + y + ", " + z + ", " + value + ")
  // listeners count:" + cellListeners.size());
  //        if( cellListeners.isEmpty() ) {
  //            return;
  //        }
  //        CellChangeEvent event = new CellChangeEvent(leafId, x, y, z, value);
  //        fireCellChanged(event);
  //    }
  //
  protected void fireCellChanged(CellChangeEvent event) {
    for (CellChangeListener l : getCellListenerArray()) {
      l.cellChanged(event);
    }
  }

  @Override
  public int setWorldCell(Vec3d world, int type) {
    // log.info("setWorldCell(" + world + ", " + type + ")");
    LeafId id = LeafId.fromWorld(world);
    LeafData leaf = getLeaf(id);
    if (leaf == null) {
      return -1;
    }

    WorldCellData data = new WorldCellData(leaf, this);

    int x = Coordinates.worldToCell(world.x);
    int y = Coordinates.worldToCell(world.y);
    int z = Coordinates.worldToCell(world.z);

    data.setCell(x, y, z, type);
    MaskUtils.recalculateSideMasks(data, x, y, z, -1); // -1 so that 'outside the world' is badType
    //MaskUtils.oldRecalculateSideMasks(data, x, y, z);

    // Get the newly masked value to fire in the event
    int value = data.getCell(x, y, z);
    // log.info("set cell:" + x + ", " + y + ", " + z + "  to: " + MaskUtils.valueToString(value));

    // Push the changes back to the DB
    for (LeafData mod : data.getModified()) {
      leafDb.storeLeaf(mod);
    }

    // Notify the listeners
    for (LeafData mod : data.getModified()) {
      leafListeners.fireLeafChanged(mod.getInfo().leafId, mod.getInfo().version.getVersion());
    }

    for (CellChangeEvent event : data.getChanges()) {
      // log.info("firing event:" + event);
      fireCellChanged(event);
    }

    return value;
  }

  @Override
  public int getWorldCell(Vec3d world) {
    LeafId id = LeafId.fromWorld(world);
    LeafData leaf = getLeaf(id);
    if (leaf == null) {
      return -1;
    }
    int x = Coordinates.worldToCell(world.x) - leaf.getInfo().location.x;
    int y = Coordinates.worldToCell(world.y) - leaf.getInfo().location.y;
    int z = Coordinates.worldToCell(world.z) - leaf.getInfo().location.z;
    return leaf.getCell(x, y, z);
  }

  @Override
  public LeafData getWorldLeaf(Vec3d worldLocation) {
    return getLeaf(LeafId.fromWorld(worldLocation));
  }

  @Override
  public LeafData getLeaf(LeafId leafId) {
    return leafDb.loadLeaf(leafId);
  }

  @Override
  public LightData getLight(LeafId leafId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public FluidData getFluid(LeafId leafId) {
    throw new UnsupportedOperationException();
  }

  @Override
  public TerrainImage getTerrainImage(TileId id, TerrainImageType type, Resolution res) {
    throw new UnsupportedOperationException();
  }

  @Override
  public TreeLayer getTrees(TileId id, Resolution res) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PointCloudLayer getPointCloudLayer(TileId id, Resolution res) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void addTileListener(TileListener l) {
    log.error("addTileListener() unsupported");
    // throw new UnsupportedOperationException();
  }

  @Override
  public void removeTileListener(TileListener l) {
    log.error("removeTileListener() unsupported");
    // throw new UnsupportedOperationException();
  }
}
