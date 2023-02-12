/*
 * $Id$
 *
 * Copyright (c) 2017, Simsilica, LLC
 * All rights reserved.
 */

package infinity.client.view;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.scene.Node;
import com.simsilica.mblock.BlockTypeIndex;
import com.simsilica.mblock.CellArray;
import com.simsilica.mblock.CellData;
import com.simsilica.mblock.ConstantCellData;
import com.simsilica.mblock.FluidTypeIndex;
import com.simsilica.mblock.LightUtils;
import com.simsilica.mblock.config.MaterialRegistry;
import com.simsilica.mblock.geom.GeometryFactory;
import com.simsilica.mblock.io.BlockTypeData;
import com.simsilica.mblock.io.FluidTypeData;
import infinity.sim.util.InfinityRunTimeException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A simple wrapper around the GeometryFactory that loads the block set configuration and material
 * registry.
 *
 * @author Asser Fahrenholz
 */
public class BlockGeometryIndex {

  static Logger log = LoggerFactory.getLogger(BlockGeometryIndex.class);

  protected final GeometryFactory geomFactory;

  /**
   * Creates a new BlockGeometryIndex.
   *
   * @param assets the asset manager to use for loading the block set configuration and material
   */
  public BlockGeometryIndex(final AssetManager assets) {

    try {
      if (!BlockTypeIndex.isInitialized()) {
        BlockTypeIndex.initialize(BlockTypeData.load("/blocks.bset"));
        FluidTypeIndex.initialize(FluidTypeData.load("/fluids.fset"));
      }

      Map<String, Material> materials =
          MaterialRegistry.loadCompiledMaterials(assets, "/materials.mset");
      geomFactory = new GeometryFactory(materials);
    } catch (Exception e) {
      throw new InfinityRunTimeException("Error initializing block set configuration", e);
    }
  }

  public Node generateBlocks(final Node target, final CellArray cells) {
    return generateBlocks(target, cells, new ConstantCellData(LightUtils.DIRECT_SUN));
  }

  public Node generateBlocks(final Node target, final CellArray cells, final CellData lightData) {
    return generateBlocks(target, cells, lightData, true);
  }

  public Node generateBlocks(
      final Node target,
      final CellArray cells,
      final CellData lightData,
      final boolean smoothLighting) {
    return geomFactory.generateBlocks(target, cells, lightData, smoothLighting);
  }
}
