/*
 * $Id$
 *
 * Copyright (c) 2019, Simsilica, LLC
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

package infinity.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.mathd.Grid;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;

/**
 * Mapping this as a separate component for now to indicate a large object that
 * is using the large object proximity grid. It may be that this is unnecessary
 * and the cell component is enough.
 *
 * @author Paul Speed
 */
public class LargeGridCell implements EntityComponent {

    private long cellId;

    private LargeGridCell() {
    }

    public LargeGridCell(final long cellId) {
        this.cellId = cellId;
    }

    public static LargeGridCell create(final Grid grid, final double x, final double y, final double z) {
        return create(grid, new Vec3d(x, y, z));
    }

    public static LargeGridCell create(final Grid grid, final Vec3d location) {
        final Vec3i cell = grid.worldToCell(location);
        return new LargeGridCell(grid.cellToId(cell.x, cell.y, cell.z));
    }

    public long getCellId() {
        return cellId;
    }

    @Override
    public String toString() {
        return "LargeGridCell[" + cellId + "]";
    }
}
