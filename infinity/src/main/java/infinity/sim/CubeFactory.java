/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.sim;

import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeFactory;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.CellArray;
import com.simsilica.mblock.MaskUtils;
import com.simsilica.mblock.phys.CellArrayPart;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.BodyMass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This is a factory that can create cubes. We need ato implement our own since MOSS is not there
 * yet with a real ghost cube.
 */
public class CubeFactory implements ShapeFactory<MBlockShape> {
  static Logger log = LoggerFactory.getLogger(CubeFactory.class);

  public CubeFactory() {
  }

  @Override
  public MBlockShape createShape(String name, double scale, Mass mass) {
    return MBlockShape.createCube(2);
//    if (info.getShapeName(ed).equals(ShapeNames.DOOR)) {
//      return this.createStaticGhostCube(info);
//    }
//    return this.createStaticPhysicalCube(info);
  }

  private MBlockShape createStaticGhostCube(double scale) {
    CellArray cells = new CellArray(1, 1, 1);
    cells.setCell(0, 0, 0, 1);
    MaskUtils.calculateSideMasks(cells);
    CellArrayPart ghostBlock =
        new CellArrayPart(
            new Vec3d(),
            CellArrayPart.Type.Blocks,
            cells,
            1.0,
            scale,
            BodyMass.createSimple(0.0, null, scale));
    return new MBlockShape(ghostBlock);
  }

  private MBlockShape createStaticPhysicalCube(double scale) {
    CellArray cells = new CellArray(1, 1, 1);
    cells.setCell(0, 0, 0, 1);
    MaskUtils.calculateSideMasks(cells);
    return MBlockShape.createShape(cells, scale / 2.0, 0.0);
  }
}
