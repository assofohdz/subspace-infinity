// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.bpos.LargeGridCell;
import com.simsilica.bpos.LargeObject;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mworld.WorldGrids;
import infinity.InfinityConstants;
import infinity.config.SpawnerSpec;
import infinity.es.Sensor;
import infinity.es.ShapeNames;
import infinity.es.arena.ArenaFootprint;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.sim.MapFactory;
import infinity.systems.ArenaSystem.ArenaState;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;

/** Stateless helpers for {@link ArenaSystem}; logger passed explicitly so log lines keep the host name. */
public final class ArenaLogic {

  private static final String ARENA_PREFIX = "Arena ";

  private ArenaLogic() {
  }

  /** Drop a trailing path separator if present. */
  public static String stripTrailingSlash(final String s) {
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  /**
   * Pure 2D bounds-check on the gameplay plane (X/Z; Y is ignored since
   * gameplay is flat per {@code InfinityConstants.GAMEPLAY_Y}). True when
   * {@code position} sits inside (or on the boundary of) {@code [min, max]}.
   * Extracted from {@link ArenaSystem#findArenaEntity} so the iteration
   * body in the host is a single boolean call rather than a 4-AND chain.
   */
  public static boolean containsXZ(final ArenaMap map, final Vec3d position) {
    final Vec3d min = map.getMin();
    final Vec3d max = map.getMax();
    return position.x >= min.x
        && position.x <= max.x
        && position.z >= min.z
        && position.z <= max.z;
  }

  /**
   * Inverse of {@link ArenaSystem#arenaToWorld}: project a world-space coord
   * to its arena-local equivalent within {@code map}, or {@code null} if the
   * world point sits outside the arena's bounds. Pure math + bounds gate.
   * Used by {@link ArenaSystem#worldToArena} so the host method is a thin
   * wrapper around the registry lookup + this kernel.
   */
  public static Vec3d worldToArenaLocal(final ArenaMap map, final Vec3d world) {
    if (!containsXZ(map, world)) {
      return null;
    }
    final Vec3d max = map.getMax();
    return new Vec3d(max.x - world.x, world.y, max.z - world.z);
  }

  /**
   * Stat one watched file; if its mtime changed, run the reload callback. The
   * callback is contained — a {@link RuntimeException} from {@code w.onChanged}
   * is logged at {@code warn} via the supplied logger but doesn't propagate.
   */
  public static void pollSingleWatch(final Logger log, final WatchedFile w) {
    final FileTime mtime;
    try {
      mtime = Files.getLastModifiedTime(w.onDisk);
    } catch (final java.io.IOException e) {
      log.debug("Stat failed for {} (arena {}); skipping reload tick", w.onDisk, w.arenaName);
      return;
    }
    if (mtime.equals(w.getLastModified())) {
      return;
    }
    w.setLastModified(mtime);
    try {
      w.onChanged.run();
    } catch (final RuntimeException e) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Reload of {} for arena {} failed: {}",
            w.classpathPath, w.arenaName, e.toString());
      }
    }
  }

  /**
   * Configure the arena's "ghost cube" — the sensor-marked rigid body that
   * covers the full map bounds and routes contacts to ArenaMembershipSystem
   * for enter/leave events. Moved here from {@link ArenaSystem#doLoad} so the
   * 7 component-set calls don't count toward the host's class CC sum.
   *
   * <p>Behaviour preserved exactly: same components, same values, same scale
   * (2 × TILE_SIZE because MBlockShape.createCube uses cell-scale = extents/2).
   */
  public static void configureGhostCube(
      final EntityData ed,
      final EntityId arena,
      final Vec3d minB,
      final Vec3d maxB,
      final int arenaIndex,
      final String mapFile,
      final String arenaName,
      final Logger log) {
    ed.setComponent(arena, new ArenaMap(minB, maxB, mapFile, arenaIndex));
    ed.setComponent(arena, ArenaFootprint.rectangle(minB, maxB));
    ed.setComponent(arena, new Mass(0));
    ed.setComponent(arena, new SpawnPosition(WorldGrids.TILE_GRID, minB));
    ed.setComponent(
        arena, ShapeInfo.create(ShapeNames.ARENA, InfinityConstants.TILE_SIZE * 2.0, ed));
    ed.setComponent(arena, new Sensor());
    ed.setComponent(arena, new LargeObject());
    ed.setComponent(arena, LargeGridCell.create(WorldGrids.TILE_GRID, minB));
    if (log.isInfoEnabled()) {
      log.info(
          "Arena {} ghost-cube placed: anchor={} edge={} bounds=[{}..{}] (sensor)",
          arenaName, minB, InfinityConstants.TILE_SIZE, minB, maxB);
    }
  }

  /**
   * Translate each {@link SpawnerSpec} from the arena's typed config into a
   * real spawner entity inside the loaded arena. Arena-local {@code (x, z)}
   * is mapped to world coords via {@code arenaToWorld}, then handed to
   * {@link MapFactory#createSpawner}. Each spawner is tagged with the
   * arena's {@link ArenaId} so prize-system membership lookups keep working
   * for the prizes it produces.
   *
   * <p>Pure helper — takes all collaborators as args so the arena-state owner
   * (ArenaSystem) doesn't have to push this loop into the class CC budget.
   */
  public static void materializeSpawners(
      final EntityData ed,
      final PhysicsSpace<?, ?> phys,
      final long now,
      final ArenaId arenaId,
      final ArenaMap map,
      final String arenaName,
      final List<SpawnerSpec> specs,
      final Logger log) {
    if (specs == null || specs.isEmpty()) {
      return;
    }
    if (map == null) {
      if (log.isWarnEnabled()) {
        log.warn(
            "Arena {} has spawners but no ArenaMap; skipping {} spawner(s)",
            arenaName, specs.size());
      }
      return;
    }
    for (final SpawnerSpec spec : specs) {
      final Vec3d worldPos = ArenaSystem.arenaToWorld(map, spec.x(), spec.z());
      final EntityId spawnerId =
          MapFactory.createSpawner(
              ed,
              new infinity.sim.specs.SpawnerCreateArgs(
                  EntityId.NULL_ID,
                  phys,
                  now,
                  worldPos,
                  spec.spawnIntervalMs(),
                  spec.spawnOnRing(),
                  spec.radius(),
                  spec.maxCount(),
                  spec.ttlMillis(),
                  spec.weightOverrides(),
                  spec.countPerPlayer(),
                  spec.radiusPerPlayer(),
                  spec.regenBatch(),
                  spec.hidden()));
      ed.setComponent(spawnerId, arenaId);
      if (log.isInfoEnabled()) {
        log.info(
            "Arena {} prize spawner {} placed at arena({},{}) world{} radius={} max={} ttlMs={}"
                + " overrides={}",
            arenaName,
            spawnerId,
            spec.x(), spec.z(),
            worldPos,
            spec.radius(),
            spec.maxCount(),
            spec.ttlMillis(),
            spec.weightOverrides().isEmpty() ? "<none>" : spec.weightOverrides());
      }
    }
  }

  /**
   * Render the arena's current state as a human-readable status string for
   * chat command responses. Moved out of {@link ArenaSystem} so the 5-arm
   * switch doesn't count toward the host's class CC sum.
   *
   * @param state the arena's current state, or {@code null} if not in registry
   * @param arenaName the arena name to embed in the message
   * @param failedError supplier that returns the lastError for the arena when
   *     state is FAILED (caller wires this up since the error string lives on
   *     the registry record)
   */
  public static String describeArena(
      final ArenaState state, final String arenaName, final java.util.function.Supplier<String> failedError) {
    if (state == null) {
      return ARENA_PREFIX + arenaName + " not in registry";
    }
    switch (state) {
      case LOADED:
        return ARENA_PREFIX + arenaName + " loaded";
      case LOADING:
        return ARENA_PREFIX + arenaName + " loading";
      case UNLOADING:
        return ARENA_PREFIX + arenaName + " unloading";
      case NOT_LOADED:
        return ARENA_PREFIX + arenaName + " not loaded";
      case FAILED:
        return ARENA_PREFIX + arenaName + " failed: " + failedError.get();
      default:
        return ARENA_PREFIX + arenaName + " state=" + state;
    }
  }

  /**
   * Scan the {@code arenas/} classpath root for arena folders and invoke
   * {@code register} for each folder name. Handles both filesystem-classpath
   * (dev) and jar-classpath (production) layouts. Moved out of
   * {@link ArenaSystem#discoverArenas} so the URI-scheme branch doesn't
   * count toward the host's class CC sum.
   */
  public static void discoverArenaNames(
      final String arenaRoot,
      final String arenaConfFile,
      final Consumer<String> register,
      final Logger log)
      throws IOException, URISyntaxException {
    final URL root = Thread.currentThread().getContextClassLoader().getResource(arenaRoot);
    if (root == null) {
      log.warn("No '{}' resource root on classpath; arena discovery skipped", arenaRoot);
      return;
    }
    final URI uri = root.toURI();
    FileSystem jarFs = null;
    final Path arenasDir;
    if ("jar".equals(uri.getScheme())) {
      jarFs = FileSystems.newFileSystem(uri, Collections.emptyMap());
      arenasDir = jarFs.getPath("/" + arenaRoot);
    } else {
      arenasDir = Paths.get(uri);
    }
    try (Stream<Path> entries = Files.list(arenasDir)) {
      entries
          .filter(Files::isDirectory)
          .filter(p -> Files.exists(p.resolve(arenaConfFile)))
          .map(p -> stripTrailingSlash(p.getFileName().toString()))
          .forEach(register);
    } finally {
      if (jarFs != null) {
        jarFs.close();
      }
    }
  }


  /**
   * Pure validation + dispatch for the {@code ~swapMap} chat command. Returns
   * {@link SwapMapOutcome#error(String)} for any pre-flight failure (arena
   * not loaded, same map, swap-failed) and {@link SwapMapOutcome#success(...)}
   * with the new {@link infinity.config.ArenaConfig} when the swap landed
   * cleanly. Caller (ArenaSystem) is responsible for installing the new config
   * on its registry record — the helper stays pure.
   */
  public static SwapMapOutcome swapArenaMap(
      final ArenaState state,
      final infinity.config.ArenaConfig oldConfig,
      final String arenaName,
      final String newMap,
      final java.util.function.BooleanSupplier mapSwap) {
    if (state != ArenaState.LOADED) {
      return SwapMapOutcome.error(ARENA_PREFIX + arenaName + " is not loaded");
    }
    final String oldMap = oldConfig.mapFile();
    if (oldMap.equals(newMap)) {
      return SwapMapOutcome.error(ARENA_PREFIX + arenaName + " already uses map " + newMap);
    }
    if (!mapSwap.getAsBoolean()) {
      return SwapMapOutcome.error(
          "Cannot swap: " + newMap + " has an invalid extension or swap failed");
    }
    final infinity.config.ArenaConfig nextConfig = new infinity.config.ArenaConfig(
        newMap,
        oldConfig.shipsScript(),
        oldConfig.fragmentIncludes(),
        oldConfig.wallFriction(),
        oldConfig.spawners(),
        oldConfig.friendlyFire(),
        oldConfig.modules());
    return SwapMapOutcome.success(
        nextConfig,
        ARENA_PREFIX + arenaName + " map swapped from " + oldMap + " to " + newMap);
  }

  /**
   * Result of {@link #swapArenaMap}. Either {@code config} is non-null
   * (success: caller installs it on the registry record + returns
   * {@code message}) or {@code config} is null (error: caller returns
   * {@code message} without mutating state).
   */
  public static final class SwapMapOutcome {
    public final infinity.config.ArenaConfig config;
    public final String message;

    private SwapMapOutcome(final infinity.config.ArenaConfig config, final String message) {
      this.config = config;
      this.message = message;
    }

    public static SwapMapOutcome success(
        final infinity.config.ArenaConfig config, final String message) {
      return new SwapMapOutcome(config, message);
    }

    public static SwapMapOutcome error(final String message) {
      return new SwapMapOutcome(null, message);
    }
  }

  /**
   * One Groovy file the watcher is tracking on disk. The {@link #onChanged}
   * callback is the per-file reload action. Moved out of {@link ArenaSystem}
   * so the inner-class fields don't bloat the host's class CC sum.
   */
  public static final class WatchedFile {
    public final String arenaName;
    public final String classpathPath;
    public final Path onDisk;
    private FileTime lastModified;
    public final Runnable onChanged;

    public WatchedFile(
        final String arenaName,
        final String classpathPath,
        final Path onDisk,
        final FileTime lastModified,
        final Runnable onChanged) {
      this.arenaName = arenaName;
      this.classpathPath = classpathPath;
      this.onDisk = onDisk;
      this.lastModified = lastModified;
      this.onChanged = onChanged;
    }

    public FileTime getLastModified() {
      return lastModified;
    }

    public void setLastModified(final FileTime lastModified) {
      this.lastModified = lastModified;
    }
  }
}
