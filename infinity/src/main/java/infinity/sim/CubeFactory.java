package infinity.sim;

import com.simsilica.es.EntityData;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeFactory;
import com.simsilica.ext.mphys.ShapeInfo;
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

  EntityData ed;

  public CubeFactory(EntityData ed) {
    this.ed = ed;
  }

  public CubeFactory() {
    this(null);
  }

  @Override
  public MBlockShape createShape(ShapeInfo info, Mass mass) {
    if (info.getShapeName(ed).equals("arena")) {
      return this.createStaticGhostCube(info);
    }
    return this.createStaticPhysicalCube(info);
  }

  private MBlockShape createStaticGhostCube(ShapeInfo info) {
    CellArray cells = new CellArray(1, 1, 1);
    cells.setCell(0, 0, 0, 1);
    MaskUtils.calculateSideMasks(cells);
    CellArrayPart ghostBlock =
        new CellArrayPart(
            new Vec3d(),
            CellArrayPart.Type.Blocks,
            cells,
            1.0,
            info.getScale(),
            BodyMass.createSimple(0.0, null, info.getScale()));
    return new MBlockShape(ghostBlock);
  }

  private MBlockShape createStaticPhysicalCube(ShapeInfo info) {
    CellArray cells = new CellArray(1, 1, 1);
    cells.setCell(0, 0, 0, 1);
    MaskUtils.calculateSideMasks(cells);
    return MBlockShape.createShape(cells, info.getScale() / 2.0, 0.0);
  }
}
