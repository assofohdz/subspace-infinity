package infinity.server;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simsilica.mathd.Vec3i;
import com.simsilica.mblock.CellArray;
import com.simsilica.mblock.CellData;
import com.simsilica.mblock.Direction;
import com.simsilica.mblock.MaskUtils;
import com.simsilica.mworld.Coordinates;
import com.simsilica.mworld.LeafData;
import com.simsilica.mworld.LeafInfo;
import com.simsilica.mworld.db.LeafDb;

/**
 * Represents a double sine wave.
 *
 * @author Paul Speed
 */
public class EmptyLeafDb implements LeafDb {

    static Logger log = LoggerFactory.getLogger(EmptyLeafDb.class);

    public static final int LEAF_SIZE = LeafInfo.SIZE;

    private final double yMin = 0; // -64;
    private final double yMax = 128;
    private final double yRange = yMax - yMin;
    private final double xScale = 256;
    private final double zScale = 256;
    private final CellData worldData = new GeneratedCellData();

    public EmptyLeafDb() {
        /*
         * for( int i = 0; i < 32; i++ ) { StringBuilder sb = new StringBuilder(); for(
         * int k = 0; k < 32; k++ ) { // Account for the border by subtracting 1 double
         * x = i; double z = k; double xSin = Math.sin(Math.PI * x/xScale); double zSin
         * = Math.sin(Math.PI * z/zScale); double sin = xSin * zSin; sin = sin * sin *
         * sin;
         *
         * double elevation = Math.round(yMin + sin * yRange * 0.5 + yRange * 0.5);
         * sb.append("[" + (int)elevation + "]"); } log.info(sb.toString()); }
         */
    }

    /*
     * @Override public LeafData loadLeaf( long leafId ) { Vec3i world =
     * Coordinates.leafIdToWorld(leafId);
     *
     * // Leaf arrays are one bigger all around CellArray cells = new CellArray(34);
     *
     * //double yMin = 0; //-64; //double yMax = 128; //double yRange = yMax - yMin;
     * //double xScale = 256; //double zScale = 256;
     *
     * int count = 0; for( int i = 0; i < 34; i++ ) { for( int k = 0; k < 34; k++ )
     * { // Account for the border by subtracting 1 double x = world.x + i - 1;
     * double z = world.z + k - 1; double xSin = Math.sin(Math.PI * x/xScale);
     * double zSin = Math.sin(Math.PI * z/zScale); double sin = xSin * zSin; sin =
     * sin * sin * sin;
     *
     * double elevation = Math.round(yMin + sin * yRange * 0.5 + yRange * 0.5);
     *
     * if( elevation < world.y ) { continue; }
     *
     * //int val = (int)elevation; //elevation < 16 ? 1 : 2; int val = elevation <
     * 64 ? 16 : 32; //21; if( elevation > 110 ) { val = 43; }
     *
     * for( int j = 0; j < 34; j++ ) { double y = world.y + j - 1; if( y < elevation
     * ) { cells.setCell(i, j, k, val); count++; } } } }
     *
     * if( world.x == 0 && world.y == 64 && world.z == 0 ) { for( int i = 0; i < 10;
     * i++ ) { for( int k = 0; k < 10; k++ ) { int x = i + 5; int z = k + 5; int val
     * = 31; //Math.min(64, k * 10 + i + 1); cells.setCell(x, 1, z, val); } } }
     *
     *
     * int sideCount = MaskUtils.calculateSideMasks(cells, 1, 1, 1, 33, 33, 33);
     * LeafData result; //if( count > 0 || sideCount > 0 ) { if( count > 0 ) {
     * //sideCount > 0 ) { result = new LeafData(new LeafInfo(world, leafId),
     * cells); } else { result = new LeafData(new LeafInfo(world, leafId), null); }
     * return result; }
     */

    @Override
    public LeafData loadLeaf(final long leafId) {
        final Vec3i world = Coordinates.leafIdToWorld(leafId);

        // Create the CellArray for the leaf
        final CellArray cells = new CellArray(LEAF_SIZE);

        // Copy the world data into the local cells array
        int empty = LeafInfo.CELL_COUNT;
        for (int i = 0; i < LEAF_SIZE; i++) {
            for (int j = 0; j < LEAF_SIZE; j++) {
                for (int k = 0; k < LEAF_SIZE; k++) {
                    final int x = world.x + i;
                    final int y = world.y + j;
                    final int z = world.z + k;
                    final int val = worldData.getCell(x, y, z);
                    if (val == 0) {
                        // No need to set it or to calculate masks... 0 is always empty
                        continue;
                    }

                    // Calculate the world mask for this cell
                    final int sideMask = MaskUtils.calculateSideMask(x, y, z, worldData);

                    final int cell = MaskUtils.setSideMask(val, sideMask);
                    cells.setCell(i, j, k, cell);

                    empty--;
                }
            }
        }

        return new LeafData(new LeafInfo(world, leafId), cells, empty);
        // if( count > 0 ) {
        // return new LeafData(new LeafInfo(world, leafId), cells);
        // } else {
        // return new LeafData(new LeafInfo(world, leafId), null);
        // }
    }

    @Override
    public void storeLeaf(final LeafData leaf) {
    }

    /**
     * Convenient for looking up masks if we generate the data in the context of the
     * CellData interface. I'm not sure this translates well to real terrain
     * generation but I suspect it's fine.
     */
    private class GeneratedCellData implements CellData {

        @Override
        public int getCell(final int x, final int y, final int z) {

            return 0;
        }

        @Override
        public int getCell(final int x, final int y, final int z, final int defaultValue) {
            return getCell(x, y, z);
        }

        @Override
        public int getCell(final int x, final int y, final int z, final Direction dir, final int defaultValue) {
            final Vec3i v = dir.getVec3i();
            return getCell(x + v.x, y + v.y, z + v.z, defaultValue);
        }

        @Override
        public void setCell(final int x, final int y, final int z, final int type) {
            throw new UnsupportedOperationException("Cannot set values back to function-generated data.");
        }

    }
}
