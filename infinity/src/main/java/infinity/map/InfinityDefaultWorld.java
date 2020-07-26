/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.map;

import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mblock.MaskUtils;
import com.simsilica.mworld.CellChangeEvent;
import com.simsilica.mworld.CellChangeListener;
import com.simsilica.mworld.Coordinates;
import com.simsilica.mworld.LeafData;
import com.simsilica.mworld.World;
import com.simsilica.mworld.base.DefaultWorld;
import com.simsilica.mworld.base.WorldCellData;
import com.simsilica.mworld.db.LeafDb;
import infinity.client.view.BlockGeometryIndex;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author AFahrenholz
 */
public class InfinityDefaultWorld implements World {

    static Logger log = LoggerFactory.getLogger(InfinityDefaultWorld.class);

    private LeafDb leafDb;

    private List<CellChangeListener> cellListeners = new ArrayList<>();
    private CellChangeListener[] cellListenerArray;
    private CellChangeListener[] emptyCellListenerArray = new CellChangeListener[0];

    public InfinityDefaultWorld(LeafDb leafDb) {
        this.leafDb = leafDb;
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

    protected CellChangeListener[] getCellListenerArray() {
        if (cellListenerArray == null) {
            cellListenerArray = cellListeners.toArray(emptyCellListenerArray);
        }
        return cellListenerArray;
    }

    protected void fireCellChanged(long leafId, int x, int y, int z, int value) {
//log.info("fireCellChanged(" + leafId + ", " + x + ", " + y + ", " + z + ", " + value + ") listeners count:" + cellListeners.size());    
        if (cellListeners.isEmpty()) {
            return;
        }
        CellChangeEvent event = new CellChangeEvent(leafId, x, y, z, value);
        fireCellChanged(event);
    }

    protected void fireCellChanged(CellChangeEvent event) {
        for (CellChangeListener l : getCellListenerArray()) {
            l.cellChanged(event);
        }
    }

    public int setWorldCell(Vec3d world, int type, boolean recalculateSideMasks) {
//log.info("setWorldCell(" + world + ", " + type + ")");    
        LeafData leaf = getWorldLeaf(world);
        if (leaf == null) {
            return -1;
        }

        WorldCellData data = new WorldCellData(leaf, this);

        int x = Coordinates.worldToCell(world.x);
        int y = Coordinates.worldToCell(world.y);
        int z = Coordinates.worldToCell(world.z);

        data.setCell(x, y, z, type);
        //We are Subspace Infinity, will calculate sidemasks manually and never recalculate (mostly using just UP)
        if (recalculateSideMasks) {
            BlockGeometryIndex.recalculateSideMasks(data, x, y, z);
        }

        // Get the newly masked value to fire in the event
        int value = data.getCell(x, y, z);
//log.info("set cell:" + x + ", " + y + ", " + z + "  to: " + MaskUtils.valueToString(value));

        //Vec3i leafLoc = leaf.getInfo().location;
        //fireCellChanged(leaf.getInfo().leafId, x - leafLoc.x, y - leafLoc.y, z - leafLoc.z, value);
        // Push the changes back to the DB
        for (LeafData mod : data.getModified()) {
            leafDb.storeLeaf(mod);
        }

        // Notify the listeners       
        for (CellChangeEvent event : data.getChanges()) {
//log.info("firing event:" + event);        
            fireCellChanged(event);
        }

        /*        
        int x = Coordinates.worldToCell(world.x) - leaf.getInfo().location.x;
        int y = Coordinates.worldToCell(world.y) - leaf.getInfo().location.y;
        int z = Coordinates.worldToCell(world.z) - leaf.getInfo().location.z;
        
 
        leaf.setCell(x, y, z, value);

        //MaskUtils.recalculateSideMasks(leaf.getCells(), x, y, z, true);
 
        fireCellChanged(leaf.getInfo().leafId, x, y, z, value);
         */
        return value;
    }

    @Override
    public int getWorldCell(Vec3d world) {
        LeafData leaf = getWorldLeaf(world);
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
        return getLeaf(Coordinates.worldToLeafId(worldLocation.x, worldLocation.y, worldLocation.z));
    }

    @Override
    public LeafData getLeaf(Vec3i leafLoc) {
        return getLeaf(Coordinates.leafToLeafId(leafLoc.x, leafLoc.y, leafLoc.z));
    }

    @Override
    public LeafData getLeaf(long leafId) {
        return leafDb.loadLeaf(leafId);
    }

    @Override
    public int setWorldCell(Vec3d world, int type) {
//log.info("setWorldCell(" + world + ", " + type + ")");    
        LeafData leaf = getWorldLeaf(world);
        if (leaf == null) {
            return -1;
        }

        WorldCellData data = new WorldCellData(leaf, this);

        int x = Coordinates.worldToCell(world.x);
        int y = Coordinates.worldToCell(world.y);
        int z = Coordinates.worldToCell(world.z);

        data.setCell(x, y, z, type);
        BlockGeometryIndex.recalculateSideMasks(data, x, y, z);

        // Get the newly masked value to fire in the event
        int value = data.getCell(x, y, z);
//log.info("set cell:" + x + ", " + y + ", " + z + "  to: " + MaskUtils.valueToString(value));

        //Vec3i leafLoc = leaf.getInfo().location;
        //fireCellChanged(leaf.getInfo().leafId, x - leafLoc.x, y - leafLoc.y, z - leafLoc.z, value);
        // Push the changes back to the DB
        for (LeafData mod : data.getModified()) {
            leafDb.storeLeaf(mod);
        }

        // Notify the listeners       
        for (CellChangeEvent event : data.getChanges()) {
//log.info("firing event:" + event);        
            fireCellChanged(event);
        }

        /*        
        int x = Coordinates.worldToCell(world.x) - leaf.getInfo().location.x;
        int y = Coordinates.worldToCell(world.y) - leaf.getInfo().location.y;
        int z = Coordinates.worldToCell(world.z) - leaf.getInfo().location.z;
        
 
        leaf.setCell(x, y, z, value);

        //MaskUtils.recalculateSideMasks(leaf.getCells(), x, y, z, true);
 
        fireCellChanged(leaf.getInfo().leafId, x, y, z, value);
         */
        return value;
    }
}
