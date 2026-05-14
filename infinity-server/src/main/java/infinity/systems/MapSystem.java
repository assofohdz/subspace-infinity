// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mathd.Vec3i;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mworld.TileId;
import com.simsilica.mworld.World;
import com.simsilica.sim.SimTime;
import infinity.InfinityConstants;
import infinity.config.EngineConfig;
import infinity.es.TileTypes;
import infinity.map.LevelFile;
import infinity.map.LevelLoader;
import infinity.server.AssetLoaderService;
import infinity.settings.EngineConfigSystem;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Map lifecycle owner — spiral auto-placement + async load/unload/swap; delegates cell-writes to {@link LegacyMapProjector} + {@link WallLightDecorator}. */
public class MapSystem extends BaseInfinitySystem {

  public static final int MAP_SIZE = InfinityConstants.TILE_SIZE;
  public static final float NOISE4J_CORRIDOR = 0f;
  public static final float NOISE4J_FLOOR = 0.5f;
  public static final float NOISE4J_WALL = 1f;

  static Logger log = LoggerFactory.getLogger(MapSystem.class);

  private static final String LOG_CURRENT_MAP_LOCATION =
      "Currentmap location is:{}, current direction is:{}";

  private final String mapDirectory = "Maps";
  private final Map<String, Set<Vec3d>> activeMaps = new HashMap<>();
  private final Map<String, Vec3d> mapCoordinates = new LinkedHashMap<>();
  private Vec3d currentMapLoc = new Vec3d(-1, 0, -1);
  private SimTime time;
  private AssetLoaderService assetLoader;
  private World world;
  private EngineConfigSystem engineConfigSystem;
  private LegacyMapProjector projector;
  private MapSystemLogic.Direction direction = MapSystemLogic.Direction.S;

  public MapSystem() {}

  protected MPhysSystem<MBlockShape> getPhysicsSystem() {
    final MPhysSystem<?> s = getSystem(MPhysSystem.class);
    @SuppressWarnings("unchecked")
    final MPhysSystem<MBlockShape> result = (MPhysSystem<MBlockShape>) s;
    return result;
  }

  public void setCell(final Vec3d pos, final int type) {
    world.setWorldCell(pos, type);
  }

  @Override
  protected void initialize() {
    final EntityData ed = requireSystem(EntityData.class);
    final MPhysSystem<MBlockShape> physics = getPhysicsSystem();
    if (physics == null) {
      throw new IllegalStateException(getClass().getName() + " system requires the MPhysSystem.");
    }
    world = super.getManager().get(World.class);
    if (world == null) {
      throw new IllegalStateException(getClass().getName() + " system requires the World.");
    }
    this.assetLoader = requireSystem(AssetLoaderService.class);
    this.engineConfigSystem = requireSystem(EngineConfigSystem.class);

    final PhysicsSpace<EntityId, MBlockShape> physicsSpace = physics.getPhysicsSpace();
    assetLoader.registerLoader(LevelLoader.class, "lvl", "lvz");

    // Single projector instance — engine-config provider closes over the live system so
    // Groovy-reload swaps are picked up automatically the next time project() runs.
    // The projector retains its own ed/physicsSpace/world handles, so MapSystem does not
    // need fields for them post-extraction (they are init-only construction inputs).
    this.projector = new LegacyMapProjector(
        ed, physicsSpace, world,
        () -> engineConfigSystem == null ? EngineConfig.DEFAULTS : engineConfigSystem.get());

    // Create entities, so the tile types will be in the string index (we use the
    // tiletypes as filters)
    final EntityId e = ed.createEntity();
    final short s = 0;
    ed.setComponent(e, TileTypes.legacy("empty", s, ed));

    final EntityId e2 = ed.createEntity();
    final short s2 = 0;
    ed.setComponent(e2, TileTypes.wangblob("empty", s2, ed));
  }

  /**
   * Finds the center of an arena. Uses MAP_SIZE to calculate center
   *
   * @param currentxCoord the x-coordinate
   * @param currentzCoord the y-coordinate
   * @return the Vec3d center coordinate
   */
  public Vec3d getCenterOfArena(final double currentxCoord, final double currentzCoord) {
    return MapSystemLogic.getCenterOfArena(currentxCoord, currentzCoord, MAP_SIZE);
  }

  private Vec3d calculateNextOffset() {
    final MapSystemLogic.Direction testDirection = direction.next();
    final Vec3d testMapLoc = testDirection.advance(currentMapLoc);
    // First time we will land here:
    if (!mapCoordinates.containsValue(currentMapLoc)) {
      log.info(LOG_CURRENT_MAP_LOCATION, currentMapLoc, direction);
      return currentMapLoc;
    } else if (!mapCoordinates.containsValue(testMapLoc)) {

      // Test if we should go new direction
      currentMapLoc = testMapLoc;
      direction = testDirection;
      log.info(LOG_CURRENT_MAP_LOCATION, currentMapLoc, direction);
      return currentMapLoc;
    }

    // If we have to continue straight ahead in our direction:
    currentMapLoc = direction.advance(currentMapLoc);
    log.info(LOG_CURRENT_MAP_LOCATION, currentMapLoc, direction);
    return currentMapLoc;
  }

  /**
   * Loads a map, auto-positioning it via the spiral placement algorithm.
   *
   * @param mapName    the lvl-map to load
   * @param arenaIndex zero-based arena slot — determines the block-type range the map's tiles
   *                   occupy ({@code arenaTileBase(arenaIndex)..+189}), and therefore which
   *                   tileset the client will render them with.
   * @return true if loaded
   */
  public boolean loadMap(final String mapName, final int arenaIndex) {
    if (!(mapName.endsWith(".lvl") || mapName.endsWith(".lvz"))) {
      return false;
    }
    final Vec3d offset = calculateNextOffset();
    final TileId tile = TileId.fromCell((int) offset.x, 0, (int) offset.z);
    return loadMap(mapName, tile, arenaIndex);
  }

  /**
   * Loads a map at an explicit grid location. Each TileId is a 1024x1024 slot on Moss's TILE_GRID;
   * adjacent tiles share a 2-cell gutter formed by each map's own border ring.
   *
   * @param mapName    the lvl-map to load
   * @param tile       the Moss TileId specifying where to place the map
   * @param arenaIndex zero-based arena slot (see {@link #loadMap(String, int)})
   * @return true if loaded, false if the filename is invalid or the tile is occupied
   */
  public boolean loadMap(final String mapName, final TileId tile, final int arenaIndex) {
    log.info("Loading map: {} at {} (arenaIndex={})", mapName, tile, arenaIndex);
    if (!(mapName.endsWith(".lvl") || mapName.endsWith(".lvz"))) {
      return false;
    }
    final Vec3i cell = tile.getCell(null);
    final Vec3d offset = new Vec3d(cell.x, cell.y, cell.z);
    if (mapCoordinates.containsValue(offset)) {
      log.warn("Tile {} already occupied; skipping {}", tile, mapName);
      return false;
    }
    final Vec3i corner = tile.getWorld(null);
    final Vec3d worldOffset = new Vec3d(corner.x, corner.y, corner.z);

    final String fileName = mapDirectory + "/" + mapName;
    final LevelFile res = (LevelFile) assetLoader.loadAsset(fileName);

    final int tileBase = InfinityConstants.arenaTileBase(arenaIndex);

    // Snapshot the sim timestamp synchronously on the caller thread. The async block
    // population must NOT read this.time from a worker thread — at startup the first
    // arena auto-loads before MapSystem.update has ever run, so this.time is null and
    // any entity-create branch (door / wormhole / asteroid / turfflag) NPEs out.
    // Trench's map happens to have no entity-trigger tiles so it dodges the NPE; deva
    // has wormholes / doors and falls in.
    final long createdTime = time != null ? time.getTime() : 0L;
    // Loading a map takes ~3 seconds depending on density — do it asynchronously.
    // Attach an exceptionally handler so any throw inside project()
    // is logged instead of silently swallowed by the future (which would otherwise
    // leave the arena visible-cube up but no blocks rendered, with zero log evidence).
    final CompletableFuture<Set<Vec3d>> completableFuture =
        CompletableFuture.supplyAsync(
            () -> projector.project(res, worldOffset, tileBase, createdTime));
    completableFuture
        .thenAccept(s -> activeMaps.put(mapName, s))
        .exceptionally(ex -> {
          log.error("Async block population failed for map {}", mapName, ex);
          return null;
        });

    res.setMapName(mapName);
    mapCoordinates.put(mapName, offset);
    log.info("Queued map: {} at grid {} (world {})", mapName, offset, worldOffset);
    return true;
  }

  /**
   * Returns the maximum world-space bounds of the loaded map. {@code mapCoordinates}
   * stores grid-cell offsets (each step is one tile = {@code MAP_SIZE} world units),
   * so the world max is {@code (gridOffset * MAP_SIZE) + (MAP_SIZE, 0, MAP_SIZE)}.
   *
   * @param arenaId the map to get the bounds for
   * @return the maximum world-space bounds of the map
   */
  public Vec3d getMapBoundsMax(final String arenaId) {
    final Vec3d min = getMapBoundsMin(arenaId);
    return new Vec3d(min.x + MAP_SIZE, min.y, min.z + MAP_SIZE);
  }

  /**
   * Returns the minimum world-space bounds of the loaded map (the bounds-min corner).
   * Converts the stored grid-cell offset to world units by multiplying by {@code MAP_SIZE}.
   *
   * @param map the map to get the bounds for
   * @return the minimum world-space bounds of the map
   */
  public Vec3d getMapBoundsMin(final String map) {
    final Vec3d gridOffset = mapCoordinates.get(map);
    return new Vec3d(gridOffset.x * MAP_SIZE, gridOffset.y, gridOffset.z * MAP_SIZE);
  }

  /**
   * Unloads a given lvz-map. Block clearing runs asynchronously; the tracking
   * entries are removed after the clear completes so a subsequent loadMap for
   * the same slot will see it as occupied until the clear is done.
   *
   * @param mapName the name of the map to unload
   * @return true if the unload was queued, false if no such map is loaded
   */
  public boolean unloadMap(final String mapName) {
    if (!activeMaps.containsKey(mapName)) {
      return false;
    }
    final Set<Vec3d> coordinates = activeMaps.get(mapName);
    final CompletableFuture<Boolean> completableFuture =
        CompletableFuture.supplyAsync(() -> this.removeBlocksFromLegacyMap(coordinates));
    completableFuture.thenAccept(s -> activeMaps.remove(mapName));
    completableFuture.thenAccept(s -> mapCoordinates.remove(mapName));
    return true;
  }

  /**
   * Replaces a loaded map with a new one at the same grid slot. The new map's
   * coordinate entry is registered synchronously (so bounds are immediately
   * queryable), while the old blocks are cleared and the new blocks are built
   * asynchronously.
   *
   * @param oldMapName the currently-loaded map to replace
   * @param newMapName the map to load in its place
   * @return true if the swap was queued, false if the old map isn't loaded or
   *         the new name has an invalid extension
   */
  public boolean swapMap(final String oldMapName, final String newMapName, final int arenaIndex) {
    if (!activeMaps.containsKey(oldMapName)) {
      return false;
    }
    if (!(newMapName.endsWith(".lvl") || newMapName.endsWith(".lvz"))) {
      return false;
    }
    final Vec3d offset = mapCoordinates.get(oldMapName);
    final TileId tile = TileId.fromCell((int) offset.x, 0, (int) offset.z);
    final Vec3i corner = tile.getWorld(null);
    final Vec3d worldOffset = new Vec3d(corner.x, corner.y, corner.z);

    final Set<Vec3d> oldCoordinates = activeMaps.remove(oldMapName);
    mapCoordinates.remove(oldMapName);
    mapCoordinates.put(newMapName, offset);

    final LevelFile res = (LevelFile) assetLoader.loadAsset(mapDirectory + "/" + newMapName);
    res.setMapName(newMapName);
    log.info("Swapping {} -> {} at {}", oldMapName, newMapName, tile);

    final int tileBase = InfinityConstants.arenaTileBase(arenaIndex);
    final long createdTime = time != null ? time.getTime() : 0L;
    CompletableFuture
        .supplyAsync(() -> removeBlocksFromLegacyMap(oldCoordinates))
        .thenApplyAsync(s -> projector.project(res, worldOffset, tileBase, createdTime))
        .thenAccept(blocks -> activeMaps.put(newMapName, blocks));
    return true;
  }

  /** Returns true if the given map is currently tracked as loaded. */
  public boolean isLoaded(final String mapName) {
    return activeMaps.containsKey(mapName);
  }

  private boolean removeBlocksFromLegacyMap(final Set<Vec3d> coordinates) {
    for (final Vec3d location : coordinates) {
      world.setWorldCell(location, 0);
    }
    verifyCleared(coordinates);
    return true;
  }

  /**
   * Post-clear audit: after iterating the tracked coordinates and writing 0, read
   * each cell back and report any whose type bits are still non-zero. A non-zero
   * readback means either our tracked set didn't cover a cell we wrote, or
   * another system re-filled the cell between clear and read.
   */
  private void verifyCleared(final Set<Vec3d> coordinates) {
    int lingering = 0;
    int shown = 0;
    for (final Vec3d location : coordinates) {
      final int raw = world.getWorldCell(location);
      final int type = raw & 0x000fffff;
      if (type != 0) {
        if (shown < 10) {
          if (log.isWarnEnabled()) {
            log.warn(
                "Post-clear: cell still non-zero at " + location
                    + " type=" + type + " raw=0x" + Integer.toHexString(raw));
          }
          shown++;
        }
        lingering++;
      }
    }
    MapSystemLogic.logVerifyClearedSummary(log, lingering, coordinates.size());
  }

  @Override
  protected void terminate() {
    // No EntitySets held; lifecycle-only state cleared via stop()/start() if added later.
  }

  @Override
  public void update(final SimTime tpf) {
    time = tpf;
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}
}
