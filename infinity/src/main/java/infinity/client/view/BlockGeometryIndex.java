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
import com.simsilica.mblock.geom.DefaultBlockFactory;
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
 * A simple wrapper around the GeometryFactory that loads the block set configuration and material
 * registry.
 *
 * @author Asser Fahrenholz
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
   * The mutable material registry handed to {@link GeometryFactory}. Held as a field so per-arena
   * tileset registration can add/replace entries at runtime — {@code GeometryFactory} looks up each
   * block's material on every render by {@link MaterialType#getId()}, so mutations propagate.
   */
  private Map<String, Material> materials;

  /**
   * Per-arena-slot tile materials. Index N holds the material rendering tiles for arena slot N.
   * Populated by {@link #registerArenaTileset(AssetManager, int, String)} and shared for all slots
   * at bootstrap (so every slot has a renderable fallback before the real tileset arrives).
   */
  private final Material[] arenaMaterials = new Material[InfinityConstants.MAX_ARENAS];

  public static final float DEFAULT_POOL_GAIN = 7.0f;
  public static final float DEFAULT_SUN_SCALE = 0.8f;
  public static final float DEFAULT_EXPOSURE = 1.8f;
  public static final float DEFAULT_TEXTURE_GAMMA = 0.6f;

  /**
   * Returns slot 0's tile material. Retained for back-compat with callers that tune shader
   * parameters (e.g. {@code LightingTunerState}) — in multi-arena use those tweaks only affect
   * slot 0. The first-loaded arena always occupies slot 0 given the allocator's first-free policy.
   */
  public Material getTileMaterial() {
    return arenaMaterials[0];
  }

  private void applyTileShaderDefaults(final Material mat) {
    mat.setFloat("PoolGain", DEFAULT_POOL_GAIN);
    mat.setFloat("SunScale", DEFAULT_SUN_SCALE);
    mat.setFloat("Exposure", DEFAULT_EXPOSURE);
    mat.setFloat("TextureGamma", DEFAULT_TEXTURE_GAMMA);
  }

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

  /**
   * Install the same bootstrap material under every arena slot's MaterialType keys. This
   * guarantees that if the server writes tile cells for any slot before the client has received
   * that arena's {@code ArenaMap} entity, geometry generation still finds a material — it just
   * paints with the bootstrap tileset until {@link #registerArenaTileset} is called with the real
   * level.
   */
  private void bootstrapAllArenaTilesets(final Material bootstrap) {
    for (int arenaIndex = 0; arenaIndex < InfinityConstants.MAX_ARENAS; arenaIndex++) {
      arenaMaterials[arenaIndex] = bootstrap;
      putTileMaterialKeys(arenaIndex, bootstrap);
    }
    log.info("Bootstrapped {} arena tileset slots with shared fallback material",
        InfinityConstants.MAX_ARENAS);
  }

  /**
   * Register the tileset embedded in {@code levelPath} as the material for arena slot
   * {@code arenaIndex}. Block-type registrations for this slot already reference a per-slot
   * {@link MaterialType} key (set up once by {@link #registerTileBlockTypes}); this method just
   * swaps the material under those keys, so any geometry already built for this slot picks up
   * the new tileset on the next render.
   *
   * @param assets     client asset manager
   * @param arenaIndex zero-based arena slot, {@code 0..MAX_ARENAS-1}
   * @param levelPath  asset path to the {@code .lvl} providing the tileset BMP
   */
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

  /**
   * Loads the embedded tileset BMP out of a {@code .lvl} file and wraps it as a padded
   * {@link Texture2D} ready for use as {@code ColorMap}. Pure-black pixels are mapped to alpha=0
   * (Subspace transparency convention). See {@link #buildPaddedTilesetTexture} for the gutter +
   * filter setup that kills minification shimmer on tile interiors.
   */
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

  /**
   * Loads the fallback tileset PNG from the asset path as ARGB pixels and routes them through the
   * same padded-atlas builder used for .lvl tilesets. PNGs already carry alpha, so the
   * pure-black-to-transparent rule is skipped.
   */
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

  /**
   * Bakes a 19x10 Subspace tileset into a padded atlas, where each
   * {@link FlatTileBlockFactory#TILE_PIXELS}x{@link FlatTileBlockFactory#TILE_PIXELS} tile is
   * surrounded by a {@link FlatTileBlockFactory#GUTTER_PIXELS}-wide replicate border. The gutter
   * lets trilinear/anisotropic filtering sample the inner tile edge without the GPU's 4-texel
   * kernel reaching into the neighboring tile in the atlas. UVs in {@link FlatTileBlockFactory}
   * address only the inner tile region, so the gutter is invisible in the rendered scene — its
   * sole job is to be the pixel that bilinear blends toward at tile edges.
   *
   * <p>The image is flipped vertically as it is written (UV V=0 at the bottom matches JME3's
   * texture origin convention). Filters are set to {@code Trilinear} / {@code Bilinear} with 8x
   * anisotropy; mipmaps are auto-generated by JME on first upload because the min filter requests
   * them.
   *
   * @param srcPixels ARGB pixels in row-major top-down order (BMP / BufferedImage convention)
   * @param srcW source image width in pixels
   * @param srcH source image height in pixels
   * @param applyBlackTransparency map pure-black pixels to alpha=0 (Subspace BMP convention only)
   */
  private Texture2D buildPaddedTilesetTexture(
      final int[] srcPixels, final int srcW, final int srcH, final boolean applyBlackTransparency) {
    final int cols = FlatTileBlockFactory.TILESET_COLUMNS;
    final int rows = FlatTileBlockFactory.TILESET_ROWS;
    final int tileW = srcW / cols;
    final int tileH = srcH / rows;
    if (tileW * cols != srcW || tileH * rows != srcH) {
      throw new InfinityRunTimeException(
          "Tileset dimensions " + srcW + "x" + srcH + " not divisible by atlas grid " + cols + "x"
              + rows);
    }
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
    return v < lo ? lo : (v > hi ? hi : v);
  }

  /**
   * Registers {@link BlockType}s for every tile in every arena slot. Each arena's 190 tile types
   * live in a contiguous block-type range starting at {@code TILE_TYPE_BASE + arenaIndex *
   * TILE_COUNT}, and each tile's {@link MaterialType} names a per-slot key ({@code "tile_<N>"})
   * so the material lookup in {@link GeometryFactory} resolves to that arena's tileset. The
   * FlatTileBlockFactory's UV math is slot-independent (normalized over a 19×10 grid, with the
   * inner-tile gutter inset baked in via {@link FlatTileBlockFactory#GUTTER_PIXELS}), so the same
   * tile id renders correctly regardless of which arena it's in.
   */
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
