/*
 * $Id$
 *
 * Copyright (c) 2017, Simsilica, LLC
 * All rights reserved.
 */

package infinity.client.view;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.scene.Node;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.image.ColorSpace;
import com.jme3.util.BufferUtils;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import com.simsilica.mblock.BlockName;
import com.simsilica.mblock.BlockType;
import com.simsilica.mblock.BlockTypeIndex;
import com.simsilica.mblock.CellArray;
import com.simsilica.mblock.CellData;
import com.simsilica.mblock.ConstantCellData;
import com.simsilica.mblock.FluidTypeIndex;
import com.simsilica.mblock.LightUtils;
import com.simsilica.mblock.config.MaterialRegistry;
import com.simsilica.mblock.geom.GeomReq;
import com.simsilica.mblock.geom.GeometryFactory;
import com.simsilica.mblock.geom.MaterialType;
import com.simsilica.mblock.io.BlockTypeData;
import com.simsilica.mblock.io.FluidTypeData;
import infinity.InfinityConstants;
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
 * {@link GeometryFactory} wrapper that loads the Moss block set + material registry
 * and owns the three Infinity-specific block extensions on top of {@code blocks.bset}:
 *
 * <ul>
 *   <li>Tiles — per-arena registration (one {@link FlatTileBlockFactory} per
 *       arena × tile-id pair); {@link MaterialType} keys are per-slot so the
 *       same tile-id renders different textures in different arenas.</li>
 *   <li>Invisible physics block (index 11) — collision at Y=1 while visual
 *       tiles render at Y=2.</li>
 *   <li>Light-emitter cell (index 12) — non-solid + transparent;
 *       {@link com.simsilica.mblock.LightUtils#recalculateLighting} flood-fills
 *       its emission into neighbour cells' lightData, which the tile shader
 *       reads via vertex colors. See {@code LanternBlockFactory} for the
 *       cube geometry that visualises each emitter cell.</li>
 * </ul>
 *
 * <p>Tile-type layout shared with the server: see
 * {@link InfinityConstants#TILE_TYPE_BASE}, {@link InfinityConstants#TILE_COUNT},
 * {@link InfinityConstants#BLOCK_TYPE_INDEX_SIZE}.
 */
public class BlockGeometryIndex {

  static Logger log = LoggerFactory.getLogger(BlockGeometryIndex.class);

  // Tile-type layout — see InfinityConstants.{TILE_TYPE_BASE, TILE_COUNT,
  // BLOCK_TYPE_INDEX_SIZE} for the canonical definition shared with the server.

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

  /** Invisible physics block — collision at Y=1 while visuals render at Y=2. */
  public static final int INVISIBLE_BLOCK_TYPE_INDEX = 11;

  /** Light-emitter cell — non-solid, transparent; emission flood-filled by {@link com.simsilica.mblock.LightUtils#recalculateLighting}. */
  public static final int LIGHT_EMITTER_BLOCK_TYPE_INDEX = 12;

  protected final GeometryFactory geomFactory;

  // Mutable so per-arena registration can swap materials at runtime; GeometryFactory re-reads each render.
  private Map<String, Material> materials;

  // Index N = arena slot N's tile material; bootstrap shares one fallback across all slots.
  private final Material[] arenaMaterials = new Material[InfinityConstants.MAX_ARENAS];

  public static final float DEFAULT_POOL_GAIN = 7.0f;
  public static final float DEFAULT_SUN_SCALE = 0.8f;
  public static final float DEFAULT_EXPOSURE = 1.8f;
  public static final float DEFAULT_TEXTURE_GAMMA = 0.6f;

  /** Returns slot 0's tile material — back-compat for shader tuners (e.g. {@code LightingTunerState}). */
  public Material getTileMaterial() {
    return arenaMaterials[0];
  }

  private void applyTileShaderDefaults(final Material mat) {
    mat.setFloat("PoolGain", DEFAULT_POOL_GAIN);
    mat.setFloat("SunScale", DEFAULT_SUN_SCALE);
    mat.setFloat("Exposure", DEFAULT_EXPOSURE);
    mat.setFloat("TextureGamma", DEFAULT_TEXTURE_GAMMA);
  }

  /** Extracts the tileset from the embedded BMP in {@code levelPath} (e.g. {@code "Maps/trench.lvl"}). */
  public BlockGeometryIndex(final AssetManager assets, final String levelPath) {
    try {
      if (!BlockTypeIndex.isInitialized()) {
        BlockTypeIndex.initialize(BlockTypeData.load("/blocks.bset"));
        FluidTypeIndex.initialize(FluidTypeData.load("/fluids.fset"));
      }
      expandBlockTypeIndex(InfinityConstants.BLOCK_TYPE_INDEX_SIZE);
      this.materials = MaterialRegistry.loadCompiledMaterials(assets, "/materials.mset");
      registerInvisibleBlockType();
      registerLanternMaterial(assets, materials);
      registerLightEmitterBlockType();
      registerTileBlockTypes();
      bootstrapAllArenaTilesets(buildTileMaterialFromLevel(assets, levelPath));
      geomFactory = new GeometryFactory(materials);
    } catch (Exception e) {
      throw new InfinityRunTimeException("Error initializing block set configuration", e);
    }
  }

  /** Falls back to a built-in tileset PNG (used when no {@code .lvl} is available). */
  public BlockGeometryIndex(final AssetManager assets) {

    try {
      if (!BlockTypeIndex.isInitialized()) {
        BlockTypeIndex.initialize(BlockTypeData.load("/blocks.bset"));
        FluidTypeIndex.initialize(FluidTypeData.load("/fluids.fset"));
      }

      // Expand the BlockTypeIndex array to accommodate tile types
      expandBlockTypeIndex(InfinityConstants.BLOCK_TYPE_INDEX_SIZE);

      this.materials = MaterialRegistry.loadCompiledMaterials(assets, "/materials.mset");

      // Register invisible block type for physics-only blocks
      registerInvisibleBlockType();
      registerLanternMaterial(assets, materials);
      registerLightEmitterBlockType();

      // Register tile material and block types
      registerTileBlockTypes();
      bootstrapAllArenaTilesets(buildTileMaterialFromPng(assets));

      geomFactory = new GeometryFactory(materials);
    } catch (Exception e) {
      throw new InfinityRunTimeException("Error initializing block set configuration", e);
    }
  }

  // Reflection: the .bset file defines a fixed type count; we need extra slots for dynamic tile types.
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

  private void registerInvisibleBlockType() {
    BlockName name = new BlockName("invisible", "physics");
    BlockType blockType = new BlockType(name, InvisibleBlockFactory.getInstance());
    BlockTypeIndex.override(INVISIBLE_BLOCK_TYPE_INDEX, blockType);
    log.info("Registered invisible physics block type at index {}", INVISIBLE_BLOCK_TYPE_INDEX);
  }

  // Unshaded — the cube stays full-bright-yellow regardless of voxel ambient (matches reference torch-cube look).
  private void registerLanternMaterial(
      final AssetManager assets, final Map<String, Material> materials) {
    Material lanternMat = new Material(assets, "Common/MatDefs/Misc/Unshaded.j3md");
    lanternMat.setColor("Color", new ColorRGBA(1.0f, 0.85f, 0.4f, 1.0f));
    String key = new MaterialType(LANTERN_MATERIAL_NAME, Arrays.asList()).getId();
    materials.put(key, lanternMat);
  }

  private void registerLightEmitterBlockType() {
    BlockName name = new BlockName(LANTERN_MATERIAL_NAME, "light_emitter");
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

  /** Builds a tile {@link Material} from a fallback PNG on the asset path. */
  private Material buildTileMaterialFromPng(final AssetManager assets) {
    final Material mat = new Material(assets, "MatDefs/TileLit.j3md");
    mat.setTexture("ColorMap", loadFallbackTilesetPng(assets));
    configureTileMaterial(mat);
    return mat;
  }

  /** Builds a tile {@link Material} from the embedded BMP inside the given {@code .lvl}. */
  private Material buildTileMaterialFromLevel(final AssetManager assets, final String levelPath) {
    final Material mat = new Material(assets, "MatDefs/TileLit.j3md");
    mat.setTexture("ColorMap", loadTilesetTexture(assets, levelPath));
    configureTileMaterial(mat);
    return mat;
  }

  private void configureTileMaterial(final Material mat) {
    mat.setFloat("AlphaDiscardThreshold", 0.5f);
    mat.setBoolean("DebugShipLight", false);
    mat.setBoolean("DebugLeafGrid", false);
    applyTileShaderDefaults(mat);
  }

  // Guarantees geometry generation finds a material if server writes cells before {@code ArenaMap} arrives.
  private void bootstrapAllArenaTilesets(final Material bootstrap) {
    for (int arenaIndex = 0; arenaIndex < InfinityConstants.MAX_ARENAS; arenaIndex++) {
      arenaMaterials[arenaIndex] = bootstrap;
      putTileMaterialKeys(arenaIndex, bootstrap);
    }
    log.info("Bootstrapped {} arena tileset slots with shared fallback material",
        InfinityConstants.MAX_ARENAS);
  }

  /** Swap arena slot {@code arenaIndex}'s tile material to the tileset embedded in {@code levelPath}. */
  public void registerArenaTileset(
      final AssetManager assets, final int arenaIndex, final String levelPath) {
    if (arenaIndex < 0 || arenaIndex >= InfinityConstants.MAX_ARENAS) {
      log.warn("registerArenaTileset: arenaIndex {} out of range [0,{})",
          arenaIndex, InfinityConstants.MAX_ARENAS);
      return;
    }
    final Material mat = buildTileMaterialFromLevel(assets, levelPath);
    arenaMaterials[arenaIndex] = mat;
    putTileMaterialKeys(arenaIndex, mat);
    log.info("Registered tileset '{}' for arena slot {}", levelPath, arenaIndex);
  }

  /** Store the given material under all three layer MaterialType keys for the arena slot. */
  private void putTileMaterialKeys(final int arenaIndex, final Material mat) {
    final String name = tileMaterialName(arenaIndex);
    for (final int layer : new int[] {FLYOVER_LAYER, REGULAR_LAYER, FLYUNDER_LAYER}) {
      final String key = new MaterialType(name, layer, Arrays.asList(GeomReq.Normals)).getId();
      materials.put(key, mat);
    }
  }

  /** Per-slot MaterialType name — keeps each arena's tileset lookups independent. */
  private static String tileMaterialName(final int arenaIndex) {
    return TILE_MATERIAL_NAME + "_" + arenaIndex;
  }

  // Subspace BMP convention: pure-black pixels → alpha=0. See {@link #buildPaddedTilesetTexture} for gutter/filter setup.
  private Texture2D loadTilesetTexture(final AssetManager assets, final String levelPath) {
    assets.registerLoader(LevelLoader.class, "lvl");
    final LevelFile levelFile = (LevelFile) assets.loadAsset(levelPath);

    final infinity.map.BitmapData tileset = levelFile.getTileset();
    return buildPaddedTilesetTexture(
        tileset.argb(),
        tileset.width(),
        tileset.height(),
        true);
  }

  // PNG already carries alpha, so the black→transparent rule is skipped.
  private Texture2D loadFallbackTilesetPng(final AssetManager assets) {
    final String path = "Textures/Subspace/tiles.png";
    final AssetInfo info = assets.locateAsset(new AssetKey<>(path));
    if (info == null) {
      throw new InfinityRunTimeException("Fallback tileset asset not found: " + path);
    }
    try (InputStream is = info.openStream()) {
      final BufferedImage img = ImageIO.read(is);
      if (img == null) {
        throw new InfinityRunTimeException("Could not decode fallback tileset PNG: " + path);
      }
      final int w = img.getWidth();
      final int h = img.getHeight();
      final int[] pixels = img.getRGB(0, 0, w, h, null, 0, w);
      return buildPaddedTilesetTexture(pixels, w, h, false);
    } catch (IOException e) {
      throw new InfinityRunTimeException("Failed to read fallback tileset PNG: " + path, e);
    }
  }

  /** Bakes a 19x10 tileset into a padded atlas; the gutter keeps trilinear/aniso filtering from bleeding across tiles. */
  private Texture2D buildPaddedTilesetTexture(
      final int[] srcPixels, final int srcW, final int srcH, final boolean applyBlackTransparency) {
    final int cols = FlatTileBlockFactory.TILESET_COLUMNS;
    final int rows = FlatTileBlockFactory.TILESET_ROWS;
    final int tileW = srcW / cols;
    final int tileH = srcH / rows;
    validateTilesetDimensions(srcW, srcH, cols, rows, tileW, tileH);
    final int gutter = FlatTileBlockFactory.GUTTER_PIXELS;
    final int cellW = tileW + 2 * gutter;
    final int cellH = tileH + 2 * gutter;
    final int dstW = cellW * cols;
    final int dstH = cellH * rows;

    final ByteBuffer buf = BufferUtils.createByteBuffer(dstW * dstH * 4);
    // Iterate destination rows bottom-up: the first bytes written land at OpenGL V=0 (texture
    // bottom), which must correspond to the BMP-bottom row to match the source's visual
    // orientation. In-cell coordinates are clamped into [0, tile-1] so the gutter ring picks
    // up an edge-replicated copy of the tile's own border pixel rather than a neighbor's.
    for (int dy = dstH - 1; dy >= 0; dy--) {
      final int row = dy / cellH;
      final int dyInCell = dy - row * cellH;
      final int tileY = clamp(dyInCell - gutter, 0, tileH - 1);
      for (int dx = 0; dx < dstW; dx++) {
        final int col = dx / cellW;
        final int dxInCell = dx - col * cellW;
        final int tileX = clamp(dxInCell - gutter, 0, tileW - 1);
        final int srcY = row * tileH + tileY;
        final int srcX = col * tileW + tileX;
        final int argb = srcPixels[srcY * srcW + srcX];
        writePixel(buf, argb, applyBlackTransparency);
      }
    }
    buf.flip();

    final Image jmeImage = new Image(Image.Format.RGBA8, dstW, dstH, buf, ColorSpace.sRGB);
    final Texture2D tex = new Texture2D(jmeImage);
    tex.setMinFilter(Texture.MinFilter.Trilinear);
    tex.setMagFilter(Texture.MagFilter.Bilinear);
    tex.setAnisotropicFilter(16);
    log.info(
        "Built padded tileset {}x{} -> {}x{} (tile {}x{}, gutter {})",
        srcW, srcH, dstW, dstH, tileW, tileH, gutter);
    return tex;
  }

  private static int clamp(final int v, final int lo, final int hi) {
    return v < lo ? lo : v > hi ? hi : v;
  }

  // The per-tile w/h arithmetic in {@link #buildPaddedTilesetTexture} relies on exact integer division.
  private static void validateTilesetDimensions(
      final int srcW, final int srcH, final int cols, final int rows,
      final int tileW, final int tileH) {
    if (tileW * cols != srcW || tileH * rows != srcH) {
      throw new InfinityRunTimeException(
          "Tileset dimensions " + srcW + "x" + srcH + " not divisible by atlas grid " + cols + "x"
              + rows);
    }
  }

  private static void writePixel(
      final ByteBuffer buf, final int argb, final boolean applyBlackTransparency) {
    final int r = (argb >> 16) & 0xFF;
    final int g = (argb >> 8) & 0xFF;
    final int b = argb & 0xFF;
    final int a;
    if (applyBlackTransparency && r == 0 && g == 0 && b == 0) {
      a = 0;
    } else {
      a = (argb >> 24) & 0xFF;
    }
    buf.put((byte) r).put((byte) g).put((byte) b).put((byte) a);
  }

  // Each arena's 190 tile types live in {@code TILE_TYPE_BASE + arenaIndex * TILE_COUNT}; per-slot MaterialType keys keep tilesets independent.
  private void registerTileBlockTypes() {
    for (int arenaIndex = 0; arenaIndex < InfinityConstants.MAX_ARENAS; arenaIndex++) {
      final int base =
          InfinityConstants.TILE_TYPE_BASE + arenaIndex * InfinityConstants.TILE_COUNT;
      final String matName = tileMaterialName(arenaIndex);
      for (int tileId = 1; tileId <= InfinityConstants.TILE_COUNT; tileId++) {
        final int typeIndex = base + tileId - 1;
        final int layer;
        if (tileId >= FLYOVER_TILE_START && tileId <= FLYOVER_TILE_END) {
          layer = FLYOVER_LAYER;
        } else if (tileId >= FLYUNDER_TILE_START && tileId <= FLYUNDER_TILE_END) {
          layer = FLYUNDER_LAYER;
        } else {
          layer = REGULAR_LAYER;
        }
        final MaterialType mt = new MaterialType(matName, layer, Arrays.asList(GeomReq.Normals));
        final FlatTileBlockFactory factory = FlatTileBlockFactory.createForTile(mt, tileId);
        final BlockName name = new BlockName("tile", arenaIndex + ":" + tileId);
        BlockTypeIndex.override(typeIndex, new BlockType(name, factory));
      }
    }
    if (log.isInfoEnabled()) {
      log.info(
          "Registered {} tile block types ({} arenas × {} tiles) indices {}..{}",
          InfinityConstants.MAX_ARENAS * InfinityConstants.TILE_COUNT,
          InfinityConstants.MAX_ARENAS,
          InfinityConstants.TILE_COUNT,
          InfinityConstants.TILE_TYPE_BASE,
          InfinityConstants.BLOCK_TYPE_INDEX_SIZE - 1);
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
