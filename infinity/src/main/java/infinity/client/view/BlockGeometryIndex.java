/*
 * $Id$
 *
 * Copyright (c) 2017, Simsilica, LLC
 * All rights reserved.
 */

package infinity.client.view;

import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import com.simsilica.mblock.BlockName;
import com.simsilica.mblock.BlockType;
import com.simsilica.mblock.BlockTypeIndex;
import com.simsilica.mblock.CellArray;
import com.simsilica.mblock.CellData;
import com.simsilica.mblock.ConstantCellData;
import com.simsilica.mblock.FluidTypeIndex;
import com.simsilica.mblock.LightUtils;
import com.simsilica.mblock.config.MaterialRegistry;
import com.simsilica.mblock.geom.DefaultBlockFactory;
import com.simsilica.mblock.geom.GeomReq;
import com.simsilica.mblock.geom.GeometryFactory;
import com.simsilica.mblock.geom.MaterialType;
import com.simsilica.mblock.io.BlockTypeData;
import com.simsilica.mblock.io.FluidTypeData;
import infinity.map.LevelFile;
import infinity.map.LevelLoader;
import infinity.sim.util.InfinityRunTimeException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;
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

  /**
   * Block type index for flat tiles. Tiles use indices TILE_TYPE_BASE to TILE_TYPE_BASE + 189.
   * The tileId (1-190) maps to type index (TILE_TYPE_BASE + tileId - 1).
   */
  public static final int TILE_TYPE_BASE = 100;

  /** Total number of tiles in the Subspace tileset (190 tiles). */
  public static final int TILE_COUNT = 190;

  /** Required size for BlockTypeIndex array to hold all tiles. */
  public static final int REQUIRED_ARRAY_SIZE = TILE_TYPE_BASE + TILE_COUNT;

  /** The material name used for tiles in the material registry. */
  public static final String TILE_MATERIAL_NAME = "tile";

  /** Material name used for the lantern (visible cube on each light-emitter cell). */
  public static final String LANTERN_MATERIAL_NAME = "lantern";

  /** Tile ID ranges for Z-ordering layers (matching Subspace tile category semantics). */
  public static final int FLYOVER_TILE_START = 173;
  public static final int FLYOVER_TILE_END = 175;
  public static final int FLYUNDER_TILE_START = 176;
  public static final int FLYUNDER_TILE_END = 190;

  /** Render layers: flyover behind, regular middle, flyunder in front. */
  public static final int FLYOVER_LAYER = 0;
  public static final int REGULAR_LAYER = 1;
  public static final int FLYUNDER_LAYER = 2;

  /**
   * Block type index for invisible physics blocks. These blocks have collision but no visible
   * geometry. Used for collision at Y=1 while visual tiles are rendered at Y=2.
   */
  public static final int INVISIBLE_BLOCK_TYPE_INDEX = 11;

  /**
   * Block type index for invisible light-emitter cells. Non-solid, transparent, no geometry.
   * Placed at wall-run midpoints on a plane above the tile floor; their emission is flood-filled
   * by {@link com.simsilica.mblock.LightUtils#recalculateLighting} into neighboring cells' lightData,
   * which the tile shader reads via vertex colors.
   */
  public static final int LIGHT_EMITTER_BLOCK_TYPE_INDEX = 12;

  protected final GeometryFactory geomFactory;

  /**
   * Creates a new BlockGeometryIndex, extracting the tileset from the embedded BMP in the given
   * level file. This is the preferred constructor — it ensures the visual tileset matches the map.
   *
   * @param assets    the asset manager
   * @param levelPath asset path to the .lvl file (e.g. {@code "Maps/trench.lvl"})
   */
  public BlockGeometryIndex(final AssetManager assets, final String levelPath) {
    try {
      if (!BlockTypeIndex.isInitialized()) {
        BlockTypeIndex.initialize(BlockTypeData.load("/blocks.bset"));
        FluidTypeIndex.initialize(FluidTypeData.load("/fluids.fset"));
      }
      expandBlockTypeIndex(REQUIRED_ARRAY_SIZE);
      Map<String, Material> materials =
          MaterialRegistry.loadCompiledMaterials(assets, "/materials.mset");
      registerInvisibleBlockType();
      registerLanternMaterial(assets, materials);
      registerLightEmitterBlockType();
      registerTileMaterialFromLevel(assets, materials, levelPath);
      registerTileBlockTypes();
      geomFactory = new GeometryFactory(materials);
    } catch (Exception e) {
      throw new InfinityRunTimeException("Error initializing block set configuration", e);
    }
  }

  /**
   * Creates a new BlockGeometryIndex using a fallback tileset PNG.
   *
   * @param assets the asset manager to use for loading the block set configuration and material
   */
  public BlockGeometryIndex(final AssetManager assets) {

    try {
      if (!BlockTypeIndex.isInitialized()) {
        BlockTypeIndex.initialize(BlockTypeData.load("/blocks.bset"));
        FluidTypeIndex.initialize(FluidTypeData.load("/fluids.fset"));
      }

      // Expand the BlockTypeIndex array to accommodate tile types
      expandBlockTypeIndex(REQUIRED_ARRAY_SIZE);

      Map<String, Material> materials =
          MaterialRegistry.loadCompiledMaterials(assets, "/materials.mset");

      // Register invisible block type for physics-only blocks
      registerInvisibleBlockType();
      registerLanternMaterial(assets, materials);
      registerLightEmitterBlockType();

      // Register tile material and block types
      registerTileMaterial(assets, materials);
      registerTileBlockTypes();

      geomFactory = new GeometryFactory(materials);
    } catch (Exception e) {
      throw new InfinityRunTimeException("Error initializing block set configuration", e);
    }
  }

  /**
   * Expands the BlockTypeIndex array to the specified size using reflection.
   * This is necessary because the .bset file only defines a limited number of types,
   * but we need additional slots for dynamically registered tile types.
   *
   * @param requiredSize the minimum required size for the array
   */
  private void expandBlockTypeIndex(final int requiredSize) {
    try {
      BlockType[] currentTypes = BlockTypeIndex.getTypes();
      if (currentTypes.length >= requiredSize) {
        log.info("BlockTypeIndex already has sufficient size: {}", currentTypes.length);
        return;
      }

      // Create expanded array and copy existing types
      BlockType[] expandedTypes = Arrays.copyOf(currentTypes, requiredSize);

      // Use reflection to set the expanded array
      Field typesField = BlockTypeIndex.class.getDeclaredField("types");
      typesField.setAccessible(true);
      typesField.set(null, expandedTypes);

      Field typeCountField = BlockTypeIndex.class.getDeclaredField("typeCount");
      typeCountField.setAccessible(true);
      typeCountField.set(null, requiredSize);

      log.info("Expanded BlockTypeIndex from {} to {} types", currentTypes.length, requiredSize);
    } catch (Exception e) {
      throw new InfinityRunTimeException("Failed to expand BlockTypeIndex", e);
    }
  }

  /**
   * Registers an invisible block type for physics-only blocks at Y=1. These blocks have collision
   * but produce no visible geometry.
   */
  private void registerInvisibleBlockType() {
    BlockName name = new BlockName("invisible", "physics");
    BlockType blockType = new BlockType(name, InvisibleBlockFactory.getInstance());
    BlockTypeIndex.override(INVISIBLE_BLOCK_TYPE_INDEX, blockType);
    log.info("Registered invisible physics block type at index {}", INVISIBLE_BLOCK_TYPE_INDEX);
  }

  /**
   * Registers a bright-yellow unlit material under {@link #LANTERN_MATERIAL_NAME} so the
   * light-emitter cube can render as a visible "lantern". Unshaded so it ignores the voxel
   * lighting pipeline — the cube stays full-bright-yellow regardless of the dim ambient
   * (matches the reference torch-cube look).
   */
  private void registerLanternMaterial(
      final AssetManager assets, final Map<String, Material> materials) {
    Material lanternMat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
    lanternMat.setColor("Color", new ColorRGBA(1.0f, 0.85f, 0.4f, 1.0f));
    String key = new MaterialType(LANTERN_MATERIAL_NAME, Arrays.asList()).getId();
    materials.put(key, lanternMat);
  }

  /**
   * Registers a light-emitter block type that also renders as a visible lantern cube.
   * BlockType's 4-bit r/g/b/sun emission value is seeded into lightData by
   * {@link com.simsilica.mblock.LightUtils#recalculateLighting}; the cube itself is drawn
   * by {@link DefaultBlockFactory#createCube} with the lantern material.
   */
  private void registerLightEmitterBlockType() {
    BlockName name = new BlockName("lantern", "light_emitter");
    // sun=0, r=15, g=13, b=8 → warm amber torch-glow at near-max strength.
    // Flood fill attenuates each channel by 1 per cell, so pools reach ~14
    // cells for the red channel (full-saturation core), ~12 for green, ~7
    // for blue — producing warm amber at the center fading to deeper red at
    // the edges.
    short emission = (short) 0x0FD8;
    MaterialType lanternMaterial = new MaterialType(LANTERN_MATERIAL_NAME, Arrays.asList());
    // LanternBlockFactory emits a half-size cube that hangs below its cell
    // origin (Y = -1 .. -0.5), so placed at cell Y=2 above a wall at Y=1 the
    // cube's bottom face touches the wall surface. Fully transparent for
    // light propagation so the emission escapes to neighboring cells.
    BlockType blockType = new BlockType(
        name, 0, emission,
        new LanternBlockFactory(lanternMaterial));
    BlockTypeIndex.override(LIGHT_EMITTER_BLOCK_TYPE_INDEX, blockType);
    log.info("Registered light-emitter block type at index {}", LIGHT_EMITTER_BLOCK_TYPE_INDEX);
  }

  /**
   * Registers the tileset material for rendering flat tiles.
   *
   * @param assets the asset manager
   * @param materials the material registry map to add the tile material to
   */
  private void registerTileMaterial(
      final AssetManager assets, final Map<String, Material> materials) {
    // Custom MOSS voxel-lit material: sun channel in vertex-color alpha,
    // colored light in vertex-color rgb, combined per-channel with max().
    // Ship and wall lighting is produced entirely by the baked cell lightData
    // written into the mesh vertex colors by GeometryFactory — no jME lights
    // need to be present.
    Material tileMat = new Material(assets, "MatDefs/TileLit.j3md");
    tileMat.setTexture("ColorMap", assets.loadTexture("Textures/Subspace/tiles.png"));
    tileMat.setFloat("AlphaDiscardThreshold", 0.5f);

    for (int layer : new int[]{FLYOVER_LAYER, REGULAR_LAYER, FLYUNDER_LAYER}) {
      String key = new MaterialType(TILE_MATERIAL_NAME, layer, Arrays.asList(GeomReq.Normals)).getId();
      materials.put(key, tileMat);
    }
    log.info("Registered tileset PNG under 3 layer keys");
  }

  /**
   * Extracts the embedded tileset from a .lvl file and registers it as the tile material.
   * Black pixels (Subspace transparency color) are converted to alpha=0.
   * The pixel data is flipped vertically to match JME3's bottom-left UV origin.
   */
  private void registerTileMaterialFromLevel(
      final AssetManager assets,
      final Map<String, Material> materials,
      final String levelPath) {

    assets.registerLoader(LevelLoader.class, "lvl");
    LevelFile levelFile = (LevelFile) assets.loadAsset(levelPath);

    int[] pixels = levelFile.getTileSetPixels();
    int width = levelFile.getTileSetImageWidth();
    int height = levelFile.getTileSetImageHeight();

    // BitMap stores rows top-to-bottom (pixels[0] = top row).
    // JME3 texture V=0 is at the bottom, so we iterate rows in reverse.
    // Also treat pure black as transparent (Subspace convention).
    ByteBuffer buf = BufferUtils.createByteBuffer(width * height * 4);
    for (int row = height - 1; row >= 0; row--) {
      for (int col = 0; col < width; col++) {
        int argb = pixels[row * width + col];
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        int a = (r == 0 && g == 0 && b == 0) ? 0 : ((argb >> 24) & 0xFF);
        buf.put((byte) r).put((byte) g).put((byte) b).put((byte) a);
      }
    }
    buf.flip();

    Image jmeImage = new Image(Image.Format.RGBA8, width, height, buf, ColorSpace.sRGB);
    Texture2D tileTexture = new Texture2D(jmeImage);
    tileTexture.setMinFilter(Texture.MinFilter.NearestNoMipMaps);
    tileTexture.setMagFilter(Texture.MagFilter.Nearest);

    Material tileMat = new Material(assets, "MatDefs/TileLit.j3md");
    tileMat.setTexture("ColorMap", tileTexture);
    tileMat.setFloat("AlphaDiscardThreshold", 0.5f);

    for (int layer : new int[]{FLYOVER_LAYER, REGULAR_LAYER, FLYUNDER_LAYER}) {
      String key = new MaterialType(TILE_MATERIAL_NAME, layer, Arrays.asList(GeomReq.Normals)).getId();
      materials.put(key, tileMat);
    }
    log.info("Registered tileset from '{}' under 3 layer keys ({}x{})", levelPath, width, height);
  }

  /**
   * Registers BlockTypes for all 190 Subspace tiles using FlatTileBlockFactory. Tiles are
   * registered at indices TILE_TYPE_BASE to TILE_TYPE_BASE + 189. Each tile gets its own
   * FlatTileBlockFactory with pre-computed UV coordinates.
   */
  private void registerTileBlockTypes() {
    for (int tileId = 1; tileId <= TILE_COUNT; tileId++) {
      int typeIndex = TILE_TYPE_BASE + tileId - 1;

      int layer;
      if (tileId >= FLYOVER_TILE_START && tileId <= FLYOVER_TILE_END) {
        layer = FLYOVER_LAYER;
      } else if (tileId >= FLYUNDER_TILE_START && tileId <= FLYUNDER_TILE_END) {
        layer = FLYUNDER_LAYER;
      } else {
        layer = REGULAR_LAYER;
      }

      MaterialType tileMaterialType = new MaterialType(TILE_MATERIAL_NAME, layer, Arrays.asList(GeomReq.Normals));
      FlatTileBlockFactory factory = FlatTileBlockFactory.createForTile(tileMaterialType, tileId);
      BlockName name = new BlockName("tile", String.valueOf(tileId));
      BlockTypeIndex.override(typeIndex, new BlockType(name, factory));
    }
    log.info("Registered {} tile block types (indices {}-{}) with Z-order layers",
        TILE_COUNT, TILE_TYPE_BASE, TILE_TYPE_BASE + TILE_COUNT - 1);
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
