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

package infinity.server;

import org.slf4j.*;

import com.simsilica.mathd.*;

import com.simsilica.mblock.*;
import com.simsilica.mworld.*;
import com.simsilica.mworld.db.LeafDb;

/**
 *  Represents a double sine wave.
 *
 *  @author    Paul Speed
 */
public class EmptyLeafDb implements LeafDb {

    static Logger log = LoggerFactory.getLogger(EmptyLeafDb.class);

    public static final int LEAF_SIZE = LeafInfo.SIZE;

    private double yMin = 0; //-64;
    private double yMax; // = 128;
    private double seaLevel = 64;
    private double yRange; // = yMax - yMin;
    private double xScale = 256;
    private double zScale = 256;
    private CellData worldData = new GeneratedCellData();

    private int snowElevation = 110;
    private int underWaterType = 16;
    private int landType = 32;
    private int snowType = 43;

    public EmptyLeafDb() {
        this(64);
    }

    public EmptyLeafDb( int maxHeightAboveSeaLevel ) {
        this.yMax = seaLevel + maxHeightAboveSeaLevel;
        this.yRange = yMax - yMin;
    }

    public EmptyLeafDb( int seaLevel, int snowElevation, int maxHeight,
                       int underWaterType, int landType, int snowType ) {
        this.seaLevel = seaLevel;
        this.snowElevation = snowElevation;
        this.yMax = maxHeight;
        this.yRange = yMax - yMin;
        this.underWaterType = underWaterType;
        this.landType = landType;
        this.snowType = snowType;
    }

    @Override
    public LeafData loadLeaf( LeafId leafId ) {
        Vec3i world = leafId.getWorld(null);

        // Create the CellArray for the leaf
        CellArray cells = new CellArray(LEAF_SIZE);

        // Copy the world data into the local cells array
        int empty = LeafInfo.CELL_COUNT;
        /*
        for( int i = 0; i < LEAF_SIZE; i++ ) {
            for( int j = 0; j < LEAF_SIZE; j++ ) {
                for( int k = 0; k < LEAF_SIZE; k++ ) {
                    int x = world.x + i;
                    int y = world.y + j;
                    int z = world.z + k;
                    int val = worldData.getCell(x, y, z);
                    if( val == 0 ) {
                        // No need to set it or to calculate masks... 0 is always empty
                        continue;
                    }

                    // Calculate the world mask for this cell
                    int sideMask = MaskUtils.calculateSideMask(x, y, z, worldData);

                    int cell = MaskUtils.setSideMask(val, sideMask);
                    cells.setCell(i, j, k, cell);

                    empty--;
                }
            }
        }
        */
        return new LeafData(new LeafInfo(world, leafId, new DataVersion(0)), cells, empty);
    }

    @Override
    public void storeLeaf( LeafData leaf ) {
    }

    /**
     *  Convenient for looking up masks if we generate the data in the context
     *  of the CellData interface.  I'm not sure this translates well to real
     *  terrain generation but I suspect it's fine.
     */
    private class GeneratedCellData implements CellData {

        @Override
        public int getCell( int x, int y, int z ) {

            double xSin = Math.sin(Math.PI * x/xScale);
            double zSin = Math.sin(Math.PI * z/zScale);
            double sin = xSin * zSin;
            sin = sin * sin * sin;

            //double elevation = Math.round(yMin + sin * yRange * 0.5 + yRange * 0.5);
            double elevation;
            if( sin < 0 ) {
                elevation = Math.round(yMin + sin * seaLevel + seaLevel);
            } else {
                elevation = Math.round(yMin + sin * (yRange - seaLevel) + seaLevel);
            }

            if( y >= elevation ) {
                return 0;
            }

            int val = elevation < seaLevel ? underWaterType : landType; //21;
            if( elevation > snowElevation ) {
                val = snowType;
            }

            return val;
        }

        @Override
        public int getCell( int x, int y, int z, int defaultValue ) {
            return getCell(x, y, z);
        }

        @Override
        public int getCell( int x, int y, int z, Direction dir, int defaultValue ) {
            Vec3i v = dir.getVec3i();
            return getCell(x + v.x, y + v.y, z + v.z, defaultValue);
        }

        @Override
        public void setCell( int x, int y, int z, int type ) {
            throw new UnsupportedOperationException("Cannot set values back to function-generated data.");
        }

    }
}
