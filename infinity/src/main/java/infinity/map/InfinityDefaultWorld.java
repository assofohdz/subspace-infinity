/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.map;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mworld.CellChangeEvent;
import com.simsilica.mworld.CellChangeListener;
import com.simsilica.mworld.Coordinates;
import com.simsilica.mworld.LeafData;
import com.simsilica.mworld.World;
import com.simsilica.mworld.base.WorldCellData;
import com.simsilica.mworld.db.LeafDb;

import infinity.client.view.BlockGeometryIndex;

/**
 *
 * @author AFahrenholz
 */
public class InfinityDefaultWorld implements World {

    static Logger log = LoggerFactory.getLogger(InfinityDefaultWorld.class);

    private final LeafDb leafDb;

    private final List<CellChangeListener> cellListeners = new ArrayList<>();
    private CellChangeListener[] cellListenerArray;
    private final CellChangeListener[] emptyCellListenerArray = new CellChangeListener[0];

    public InfinityDefaultWorld(final LeafDb leafDb) {
        this.leafDb = leafDb;
    }

    @Override
    public void addCellChangeListener(final CellChangeListener l) {
        cellListeners.add(l);
        cellListenerArray = null;
    }

    @Override
    public void removeCellChangeListener(final CellChangeListener l) {
        cellListeners.remove(l);
        cellListenerArray = null;
    }

    protected CellChangeListener[] getCellListenerArray() {
        if (cellListenerArray == null) {
            cellListenerArray = cellListeners.toArray(emptyCellListenerArray);
        }
        return cellListenerArray;
    }

    protected void fireCellChanged(final long leafId, final int x, final int y, final int z, final int value) {
//log.info("fireCellChanged(" + leafId + ", " + x + ", " + y + ", " + z + ", " + value + ") listeners count:" + cellListeners.size());
        if (cellListeners.isEmpty()) {
            return;
        }
        final CellChangeEvent event = new CellChangeEvent(leafId, x, y, z, value);
        fireCellChanged(event);
    }

    protected void fireCellChanged(final CellChangeEvent event) {
        for (final CellChangeListener l : getCellListenerArray()) {
            l.cellChanged(event);
        }
    }

    @Override
    public int getWorldCell(final Vec3d world) {
        final LeafData leaf = getWorldLeaf(world);
        if (leaf == null) {
            return -1;
        }
        final int x = Coordinates.worldToCell(world.x) - leaf.getInfo().location.x;
        final int y = Coordinates.worldToCell(world.y) - leaf.getInfo().location.y;
        final int z = Coordinates.worldToCell(world.z) - leaf.getInfo().location.z;
        return leaf.getCell(x, y, z);
    }

    @Override
    public LeafData getWorldLeaf(final Vec3d worldLocation) {
        return getLeaf(Coordinates.worldToLeafId(worldLocation.x, worldLocation.y, worldLocation.z));
    }

    @Override
    public LeafData getLeaf(final Vec3i leafLoc) {
        return getLeaf(Coordinates.leafToLeafId(leafLoc.x, leafLoc.y, leafLoc.z));
    }

    @Override
    public LeafData getLeaf(final long leafId) {
        return leafDb.loadLeaf(leafId);
    }

    @Override
    public int setWorldCell(final Vec3d world, final int type) {
//log.info("setWorldCell(" + world + ", " + type + ")");
        final LeafData leaf = getWorldLeaf(world);
        if (leaf == null) {
            return -1;
        }

        final WorldCellData data = new WorldCellData(leaf, this);

        final int x = Coordinates.worldToCell(world.x);
        final int y = Coordinates.worldToCell(world.y);
        final int z = Coordinates.worldToCell(world.z);

        data.setCell(x, y, z, type);
        BlockGeometryIndex.recalculateSideMasks(data, x, y, z);

        // Get the newly masked value to fire in the event
        final int value = data.getCell(x, y, z);
//log.info("set cell:" + x + ", " + y + ", " + z + "  to: " + MaskUtils.valueToString(value));

        // Vec3i leafLoc = leaf.getInfo().location;
        // fireCellChanged(leaf.getInfo().leafId, x - leafLoc.x, y - leafLoc.y, z -
        // leafLoc.z, value);
        // Push the changes back to the DB
        for (final LeafData mod : data.getModified()) {
            leafDb.storeLeaf(mod);
        }

        // Notify the listeners
        for (final CellChangeEvent event : data.getChanges()) {
//log.info("firing event:" + event);
            fireCellChanged(event);
        }

        /*
         * int x = Coordinates.worldToCell(world.x) - leaf.getInfo().location.x; int y =
         * Coordinates.worldToCell(world.y) - leaf.getInfo().location.y; int z =
         * Coordinates.worldToCell(world.z) - leaf.getInfo().location.z;
         *
         *
         * leaf.setCell(x, y, z, value);
         *
         * //MaskUtils.recalculateSideMasks(leaf.getCells(), x, y, z, true);
         *
         * fireCellChanged(leaf.getInfo().leafId, x, y, z, value);
         */
        return value;
    }
}
