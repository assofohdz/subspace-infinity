// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim.internal;

import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeFactory;
import com.simsilica.mblock.phys.MBlockShape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Cube {@link ShapeFactory}; placeholder until MOSS lands a real ghost-cube. */
public class CubeFactory implements ShapeFactory<MBlockShape> {
  static Logger log = LoggerFactory.getLogger(CubeFactory.class);

  public CubeFactory() {
    // no-op: default constructor; CubeFactory is stateless.
  }

  @Override
  public MBlockShape createShape(final String name, final double scale, final Mass mass) {
    // Static cube via MBlockShape.createCube — Type.Blocks. Cell-scale is
    // `extents/2` so the produced cube edge length = scale / 2; pass 2 × edge
    // to get an edge-length cube (e.g. 2 × TILE_SIZE for a TILE_SIZE-edge cube).
    //
    // Contact routing note: sphere-vs-Blocks contacts DO fan out through the
    // standard ContactListener chain, so ContactSystem's Sensor filter sees them
    // and can `contact.disable()` to make the cube behave as a sensor. (Only
    // Blocks-vs-Blocks contacts skip ContactSystem and go through
    // MBlockCollisionSystem directly — irrelevant here, dynamic bodies are
    // spheres.) See ArenaMembershipSystem for the membership flow.
    return MBlockShape.createCube(scale);
  }
}
