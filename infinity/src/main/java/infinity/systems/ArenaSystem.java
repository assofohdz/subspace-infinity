// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.bpos.LargeGridCell;
import com.simsilica.bpos.LargeObject;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mworld.WorldGrids;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.InfinityConstants;
import infinity.config.ArenaConfig;
import infinity.config.SpawnerSpec;
import infinity.config.SpawnConfig;
import infinity.config.TeamSpawn;
import infinity.config.ZoneConfig;
import infinity.es.Sensor;
import infinity.es.ShapeNames;
import infinity.es.arena.ArenaId;
import infinity.systems.ship.ShipSpawnSystem;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.GroovyArenaLoader;
import infinity.settings.GroovySettingsHost;
import infinity.settings.GroovyZoneLoader;
import infinity.es.arena.ArenaFootprint;
import infinity.es.arena.ArenaMap;
import infinity.es.ship.Player;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.ArenaManager;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandTriFunction;
import infinity.sim.GameEntities;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arena lifecycle manager. Owns a registry of arenas the server knows about and drives their state
 * toward a declared "desired" set via a reconcile loop on each tick.
 *
 * <p>Flow:
 *
 * <ol>
 *   <li><b>Discovery</b> — at first {@link #update}, scan {@code arenas/&#47;arena.conf} on the
 *       classpath and register each folder as {@link ArenaState#NOT_LOADED}.
 *   <li><b>Zone startup config</b> — read {@code zone.conf}'s {@code [Startup] AutoLoad=} key and
 *       flip {@code desired=true} for each listed arena.
 *   <li><b>Reconcile</b> — every tick, compare each record's {@code desired} bit to its {@link
 *       ArenaState}. Desired but not loaded → load; not desired but loaded → unload.
 * </ol>
 *
 * Chat commands ({@code ~loadArena}, {@code ~loadMap}, {@code ~unloadMap}) are a thin imperative
 * face over the same desired-state API — they flip the bit and call {@link #reconcile} immediately
 * so they can return an accurate status string.
 *
 * @author Asser
 */
public class ArenaSystem extends AbstractGameSystem implements ArenaManager {

  static final Logger log = LoggerFactory.getLogger(ArenaSystem.class);

  /** Lifecycle state of an arena in the registry. */
  public enum ArenaState {
    /** Registered but not running. Transitions to {@link #LOADING} when {@code desired=true}. */
    NOT_LOADED,
    /** Load in progress. Currently transient (loads complete synchronously). */
    LOADING,
    /** Arena entity and settings are live; map block generation may still be streaming in. */
    LOADED,
    /** Unload in progress. Transient. */
    UNLOADING,
    /** Last attempt failed; the record stays here until manual retry clears it. */
    FAILED
  }

  /** One registry row per known arena. Mutated only on the sim thread. */
  private static final class ArenaRecord {
    final String name;
    boolean desired;
    ArenaState state = ArenaState.NOT_LOADED;
    EntityId entityId;
    int arenaIndex = -1; // assigned by the slot allocator at load-time; -1 when not loaded
    String lastError;
    /**
     * Typed arena-scope config — the in-scope subset of what used to live in
     * {@code arena.conf} (map / shipsScript / spawn / fragment list). Populated
     * at load-time from {@code arena.groovy} when present, otherwise
     * synthesised from the legacy INI so callers can read uniformly without
     * caring which authoring format produced the values. Stays
     * {@link ArenaConfig#EMPTY} until {@code doLoad} runs.
     */
    ArenaConfig config = ArenaConfig.EMPTY;

    ArenaRecord(final String name) {
      this.name = name;
    }
  }

  private static final String ARENA_ROOT = "arenas";
  private static final String ARENA_CONF = "arena.conf";
  /**
   * Arena name returned by {@link #getDefaultArenaId()} — the convention
   * arena that consumers fall back to when no specific arena context is
   * available (e.g. early-connect player spawn before the arena gate).
   */
  private static final String DEFAULT_ARENA_ID = "default";

  private final Map<String, ArenaRecord> registry = new ConcurrentHashMap<>();
  /**
   * Cached zone config, loaded once at startup. Other systems reach this via
   * {@link #getZoneConfig()} so consumers stay decoupled from the Groovy
   * loader and from the zone.groovy path.
   */
  private ZoneConfig zoneConfig = ZoneConfig.EMPTY;

  /**
   * Slot allocator for arena indices. Each {@code true} entry means the slot is in use by a
   * currently-loaded arena. Indices map 1:1 to the per-arena block-type ranges on the client
   * (tiles 1..190 of arena N live at block types {@code TILE_TYPE_BASE + N * 190}..+189).
   * Released on unload so the next load can reuse the slot.
   */
  private final boolean[] arenaSlots = new boolean[InfinityConstants.MAX_ARENAS];

  private EntityData ed;
  private EntitySet arenaEntities;
  private EntitySet playerEntities;
  private ConfigRegistrySystem configRegistry;
  private final GroovyArenaLoader arenaLoader = new GroovyArenaLoader();
  private boolean bootstrapped;

  /**
   * Per-arena watch state for hot-reloadable Groovy files (the arena's
   * {@code ships.groovy} plus every {@code .groovy} fragment in
   * {@code arena.groovy}'s {@code includeFragment} list). Polled each tick by
   * {@link #pollScriptWatches()} so dev-mode edits trigger an immediate reload
   * via the watch's {@link WatchedFile#onChanged} callback. Keyed by arena
   * name. Production / classpath-only deployments are not watched (no
   * entries — the on-disk path is unresolvable).
   */
  private final Map<String, List<WatchedFile>> watchedFiles = new ConcurrentHashMap<>();

  /**
   * Throttle deadline for {@link #pollScriptWatches()} — stat() once per arena
   * per {@code zoneConfig.scriptPollIntervalNanos()} sim-time-nanos. The
   * interval is read from {@code zone.groovy} (default 5 s) and is fixed at
   * server startup; not hot-reloadable.
   */
  private long nextScriptPollNanos;

  /**
   * One Groovy file the watcher is tracking on disk. The {@link #onChanged}
   * callback is the per-file reload action — for {@code ships.groovy} it
   * re-applies the typed ship config and reprojects live ships; for fragments
   * it asks {@link SettingsSystem#reloadFragments} to rebuild the merged
   * settings store. Consumers re-read on next consumption; no event fires.
   */
  private static final class WatchedFile {
    final String arenaName;
    final String classpathPath;
    final Path onDisk;
    FileTime lastModified;
    final Runnable onChanged;

    WatchedFile(
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
  }

  private final Pattern loadMap = Pattern.compile("\\~loadMap\\s(\\w+.(?:lvl|lvz))");
  private final Pattern unloadMap = Pattern.compile("\\~unloadMap\\s(\\w+.(?:lvl|lvz))");
  private final Pattern swapMap =
      Pattern.compile("\\~swapMap\\s([\\w()\\-]+)\\s+(\\w+\\.(?:lvl|lvz))");
  private final Pattern loadArenaByName = Pattern.compile("\\~loadArena\\s([\\w()\\-]+)");
  private final Pattern listArenas = Pattern.compile("\\~arenas");

  @Override
  protected void initialize() {
    final ChatHostedPoster chat = getSystem(InfinityChatHostedService.class);
    ed = getSystem(EntityData.class);
    arenaEntities = ed.getEntities(ArenaId.class, ArenaMap.class);
    playerEntities = ed.getEntities(Player.class, BodyPosition.class);
    configRegistry = getSystem(ConfigRegistrySystem.class);

    chat.registerPatternTriConsumer(
        loadMap,
        "The command to load a new map is ~loadMap <mapName>, where <mapName> is the name "
            + "of the map you want to load",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::loadArenaByMapCommand));
    chat.registerPatternTriConsumer(
        unloadMap,
        "The command to unload a new map is ~unloadMap <mapName>, where <mapName> is the "
            + "name of the map you want to unload",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::unloadArenaByMapCommand));
    chat.registerPatternTriConsumer(
        swapMap,
        "Swap the map of a loaded arena: ~swapMap <arenaName> <newMap>. The arena's identity and "
            + "settings stay the same; only the underlying map is replaced.",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::swapArenaCommand));
    chat.registerPatternTriConsumer(
        loadArenaByName,
        "The command to load an arena by name is ~loadArena <arenaName>. Reads "
            + "arenas/<arenaName>/arena.conf, loads the map declared by its [General] Map= key "
            + "(falling back to <arenaName>.lvl), and attaches the settings.",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::loadArenaByNameCommand));
    chat.registerPatternTriConsumer(
        listArenas,
        "~arenas lists every loaded arena with its world bounds + centre. Marks the "
            + "arena containing your avatar with [you].",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::listArenasCommand));
  }

  @Override
  protected void terminate() {
    arenaEntities.release();
    arenaEntities = null;

    playerEntities.release();
    playerEntities = null;
  }

  @Override
  public void update(final SimTime tpf) {
    // SettingsSystem is registered after ArenaSystem, so its arena.conf loader isn't
    // ready at initialize() time. Defer discovery + startup config to the first tick.
    if (!bootstrapped) {
      bootstrap();
      bootstrapped = true;
    }
    playerEntities.applyChanges();
    arenaEntities.applyChanges();
    reconcileAll();
    if (tpf.getTime() >= nextScriptPollNanos) {
      nextScriptPollNanos = tpf.getTime() + zoneConfig.scriptPollIntervalNanos();
      pollScriptWatches();
    }
  }

  /**
   * Register a per-arena watch on a Groovy file so a dev-mode edit fires
   * {@code onChanged} on the next throttled poll. No-op if the file isn't
   * reachable on disk (production / classpath-only deployments) or the path
   * is blank.
   */
  private void registerFileWatch(
      final ArenaId arenaId, final String classpathPath, final Runnable onChanged) {
    if (classpathPath == null || classpathPath.isBlank()) {
      return;
    }
    final Path onDisk = GroovySettingsHost.INSTANCE.resolveOnDisk(classpathPath);
    if (onDisk == null) {
      log.debug(
          "{} for arena {} not on disk; live reload disabled for this run",
          classpathPath, arenaId.getArena());
      return;
    }
    try {
      final FileTime mtime = Files.getLastModifiedTime(onDisk);
      watchedFiles
          .computeIfAbsent(arenaId.getArena(), k -> new ArrayList<>())
          .add(new WatchedFile(arenaId.getArena(), classpathPath, onDisk, mtime, onChanged));
      log.info("Watching {} for arena {}", onDisk, arenaId.getArena());
    } catch (final java.io.IOException e) {
      log.warn(
          "Could not stat {} to enable live reload for arena {}: {}",
          onDisk, arenaId.getArena(), e.toString());
    }
  }

  private void unregisterScriptWatch(final String arenaName) {
    final List<WatchedFile> removed = watchedFiles.remove(arenaName);
    if (removed != null && !removed.isEmpty()) {
      log.debug("Stopped watching {} file(s) for arena {}", removed.size(), arenaName);
    }
  }

  /**
   * Stat each watched file's on-disk path; if the mtime changed, run the
   * file's reload callback. Throttled to roughly once per
   * {@code zoneConfig.scriptPollIntervalNanos()} by the caller in
   * {@link #update}. Reload failures are logged and contained so a single
   * file's failure doesn't abort the rest of the poll.
   */
  private void pollScriptWatches() {
    if (watchedFiles.isEmpty()) {
      return;
    }
    for (final List<WatchedFile> arenaWatches : watchedFiles.values()) {
      for (final WatchedFile w : arenaWatches) {
        final FileTime mtime;
        try {
          mtime = Files.getLastModifiedTime(w.onDisk);
        } catch (final java.io.IOException e) {
          log.debug("Stat failed for {} (arena {}); skipping reload tick", w.onDisk, w.arenaName);
          continue;
        }
        if (mtime.equals(w.lastModified)) {
          continue;
        }
        w.lastModified = mtime;
        try {
          w.onChanged.run();
        } catch (final RuntimeException e) {
          log.warn(
              "Reload of {} for arena {} failed: {}",
              w.classpathPath, w.arenaName, e.toString());
        }
      }
    }
  }

  @Override
  public void start() {
    // No-op.
  }

  @Override
  public void stop() {
    // No-op.
  }

  /**
   * Resolve a world-space position to the {@link ArenaId} of the loaded arena whose
   * {@link ArenaMap} bounds contain the point on the gameplay plane (X/Z; Y is ignored
   * since gameplay is flat per {@code InfinityConstants.GAMEPLAY_Y}). Returns
   * {@code null} when the point sits outside every loaded arena — by design ships
   * are allowed to roam in no-arena void space.
   *
   * <p>Reads the EntitySet without re-applying changes (relies on {@link #update}
   * having done so this tick); arenas don't move within a tick so a one-tick stale
   * read is harmless. First match wins — adjacent arenas share only a 2-cell gutter,
   * so any overlap is intentional and either pick is correct.
   */
  @Nullable
  public ArenaId findArenaAt(final Vec3d position) {
    final Entity arena = findArenaEntity(position);
    return arena == null ? null : arena.get(ArenaId.class);
  }

  /**
   * Sibling of {@link #findArenaAt} that returns the arena entity's id rather than its
   * {@link ArenaId} component. Used by warp-driven membership reconciliation
   * ({@code ArenaMembershipSystem.markEntered}) which needs the entity reference to
   * mirror what a contact-driven enter would have produced.
   */
  @Nullable
  public EntityId findArenaEntityAt(final Vec3d position) {
    final Entity arena = findArenaEntity(position);
    return arena == null ? null : arena.getId();
  }

  @Nullable
  private Entity findArenaEntity(final Vec3d position) {
    for (final Entity arena : arenaEntities) {
      final ArenaMap map = arena.get(ArenaMap.class);
      final Vec3d min = map.getMin();
      final Vec3d max = map.getMax();
      if (position.x >= min.x && position.x <= max.x
          && position.z >= min.z && position.z <= max.z) {
        return arena;
      }
    }
    return null;
  }

  /**
   * Resolve the world-space spawn coordinate for the named arena and a
   * given player frequency. Single source of truth for both the
   * connect-time spawn (called from
   * {@code GameSessionHostedService.resolveInitialSpawn} via the
   * {@code zone.groovy enterSpawn} arena pointer) and in-arena
   * ship-change / respawns (called from
   * {@code AvatarSystem.requestShipChange} with the ship's own
   * {@code ArenaId} and {@link infinity.es.ship.Frequency}).
   *
   * <p>Two-tier lookup:
   * <ol>
   *   <li><b>Typed Pattern 4 first.</b> Read the arena's
   *       {@link SpawnConfig} from {@link ConfigRegistrySystem}. If
   *       {@link SpawnConfig#teams()} is non-empty, look up the team
   *       via {@link SpawnConfig#forFreq(int)} (Subspace canonical
   *       wraparound: {@code freq % teams.size()}). When the team's
   *       {@code radiusTiles > 0}, sample uniformly inside the disc;
   *       {@code radiusTiles == 0} means exact-point spawn.
   *   <li><b>Legacy fallback.</b> No typed {@code spawn.groovy}
   *       authored → fall back to {@link ArenaConfig#spawnX()} /
   *       {@link ArenaConfig#spawnZ()} (the single-spawn-point
   *       directive in {@code arena.groovy}). Preserves behaviour for
   *       arenas not yet migrated to typed spawn data — most notably
   *       the {@code (default)} arena and the SVS-family presets.
   * </ol>
   *
   * <p>Coordinates are arena-local <em>tiles</em> (REFERENCE.md
   * {@code ## Spawn}); {@link #arenaToWorld} translates to world space.
   *
   * @param arenaName arena registry key (folder name under
   *     {@code zone/arenas/})
   * @param freq player frequency; wraps via
   *     {@link Math#floorMod(int, int)} so any non-negative or
   *     negative value resolves to a valid team index when typed
   *     spawn data is present
   * @return world-space {@link Vec3d} on the gameplay plane, or
   *     {@code null} if the arena isn't loaded (no entity / no
   *     {@code ArenaMap}). Callers fall back as they see fit
   *     (typically world origin).
   */
  @Nullable
  public Vec3d getArenaSpawn(final String arenaName, final int freq) {
    final ArenaRecord rec = registry.get(arenaName);
    if (rec == null || rec.entityId == null) {
      log.warn("getArenaSpawn: arena '{}' not loaded", arenaName);
      return null;
    }
    final ArenaMap map = ed.getComponent(rec.entityId, ArenaMap.class);
    if (map == null) {
      log.warn("getArenaSpawn: arena '{}' has no ArenaMap component", arenaName);
      return null;
    }

    final SpawnConfig spawn =
        configRegistry.forArena(new ArenaId(arenaName, rec.entityId)).spawn();
    final TeamSpawn team = spawn.forFreq(freq);
    if (team != null) {
      final double[] xy = sampleTeamSpawn(team);
      return arenaToWorld(map, xy[0], xy[1]);
    }

    // Legacy fallback — un-migrated arena (no spawn.groovy authored).
    return arenaToWorld(map, rec.config.spawnX(), rec.config.spawnZ());
  }

  /**
   * Uniform-disc sample around a team's spawn centre. Returns the
   * exact centre when {@link TeamSpawn#radiusTiles()} is {@code 0}
   * (point spawn) so authors get deterministic behaviour without
   * needing to seed an RNG.
   */
  private static double[] sampleTeamSpawn(final TeamSpawn team) {
    final int radius = team.radiusTiles();
    if (radius <= 0) {
      return new double[] {team.x(), team.y()};
    }
    // sqrt(rand) gives a uniform area distribution over the disc;
    // omitting the sqrt would cluster samples toward the centre.
    final double r = radius * Math.sqrt(Math.random());
    final double theta = Math.random() * 2.0 * Math.PI;
    return new double[] {team.x() + r * Math.cos(theta), team.y() + r * Math.sin(theta)};
  }

  /**
   * Convert arena-local {@code (x, z)} to a world-space {@link Vec3d} on the gameplay
   * plane. Arena-local convention: {@code (0, 0) = NW corner}, {@code (TILE_SIZE,
   * TILE_SIZE) = SE corner}. The render flips world {@code (max-X, max-Z)} onto the
   * NW screen corner (camera looks down {@code -Y} with both axes inverted vs jME's
   * default), so we anchor arena coords at {@link ArenaMap#getMax() ArenaMap.max}
   * and decrement.
   */
  public static Vec3d arenaToWorld(final ArenaMap map, final double localX, final double localZ) {
    return new Vec3d(
        map.getMax().x - localX,
        InfinityConstants.GAMEPLAY_Y,
        map.getMax().z - localZ);
  }

  /**
   * Return the typed arena-scope config for the named arena. Returns
   * {@link ArenaConfig#EMPTY} when the arena is unknown or hasn't reached the
   * {@code LOADED} state yet, so hot-path callers can skip null-checks and
   * just read {@code .wallFriction()} / {@code .mapFile()} / etc. uniformly.
   *
   * <p>Read-only view of {@code rec.config}; the config is replaced atomically
   * (whole-record swap) when an arena reloads, so a concurrent reader on the
   * physics thread never sees a torn record.
   */
  public ArenaConfig getArenaConfig(final String arenaName) {
    final ArenaRecord rec = registry.get(arenaName);
    if (rec == null) {
      return ArenaConfig.EMPTY;
    }
    return rec.config;
  }

  /**
   * Look up the {@link ArenaMap} component for the named arena, or {@code null} if the arena
   * isn't loaded. Convenience accessor for callers that need the arena's world bounds without
   * walking {@code arenaEntities} themselves.
   */
  @Nullable
  public ArenaMap getArenaMap(final String arenaName) {
    final ArenaRecord rec = registry.get(arenaName);
    if (rec == null || rec.entityId == null) {
      return null;
    }
    return ed.getComponent(rec.entityId, ArenaMap.class);
  }

  /**
   * Inverse of {@link #arenaToWorld}: project a world-space coordinate to its
   * arena-local equivalent within the named arena. Returns {@code null} if the arena
   * isn't loaded or the world coord is outside the arena's bounds. Used by the client
   * HUD to show "you are at arena (X, Z)" alongside the world coord.
   */
  @Nullable
  public Vec3d worldToArena(final String arenaName, final Vec3d world) {
    final ArenaRecord rec = registry.get(arenaName);
    if (rec == null || rec.entityId == null) {
      return null;
    }
    final ArenaMap map = ed.getComponent(rec.entityId, ArenaMap.class);
    if (map == null) {
      return null;
    }
    final Vec3d max = map.getMax();
    final Vec3d min = map.getMin();
    if (world.x < min.x || world.x > max.x || world.z < min.z || world.z > max.z) {
      return null;
    }
    return new Vec3d(max.x - world.x, world.y, max.z - world.z);
  }

  /* ---------------------------------------------------------------- */
  /* Desired-state API                                                */
  /* ---------------------------------------------------------------- */

  /**
   * Declare whether the named arena should be running. Idempotent; the reconciler (called from
   * {@link #update}) makes reality match. If the arena is not yet in the registry it is created.
   */
  public void setDesired(final String arenaName, final boolean desired) {
    registry.computeIfAbsent(arenaName, ArenaRecord::new).desired = desired;
  }

  /**
   * Programmatic entry point for loading an arena: flips desired=true and reconciles now. The
   * returned string reflects the state reached by that reconcile step.
   */
  public String loadArena(final String arenaName) {
    setDesired(arenaName, true);
    reconcile(arenaName);
    return describe(arenaName);
  }

  /* ---------------------------------------------------------------- */
  /* Bootstrap: discovery + zone.conf                                 */
  /* ---------------------------------------------------------------- */

  private void bootstrap() {
    try {
      discoverArenas();
    } catch (final Exception e) {
      log.warn("Arena discovery failed", e);
    }
    try {
      applyZoneStartupConfig();
    } catch (final Exception e) {
      log.warn("zone.conf startup apply failed", e);
    }
  }

  /** Scan {@code arenas/&#47;arena.conf} on the classpath and register each folder. */
  private void discoverArenas() throws Exception {
    final URL root = Thread.currentThread().getContextClassLoader().getResource(ARENA_ROOT);
    if (root == null) {
      log.warn("No '{}' resource root on classpath; arena discovery skipped", ARENA_ROOT);
      return;
    }
    final URI uri = root.toURI();
    FileSystem jarFs = null;
    final Path arenasDir;
    if ("jar".equals(uri.getScheme())) {
      jarFs = FileSystems.newFileSystem(uri, Collections.emptyMap());
      arenasDir = jarFs.getPath("/" + ARENA_ROOT);
    } else {
      arenasDir = Paths.get(uri);
    }
    try (Stream<Path> entries = Files.list(arenasDir)) {
      entries
          .filter(Files::isDirectory)
          .filter(p -> Files.exists(p.resolve(ARENA_CONF)))
          .map(p -> stripTrailingSlash(p.getFileName().toString()))
          .forEach(name -> registry.computeIfAbsent(name, ArenaRecord::new));
    } finally {
      if (jarFs != null) {
        jarFs.close();
      }
    }
    log.info(
        "Discovered {} arena(s): {}",
        registry.size(),
        registry.keySet().stream().sorted().collect(Collectors.toList()));
  }

  private static String stripTrailingSlash(final String s) {
    return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
  }

  /**
   * Resolve the arena's typed config from {@code arenas/<arenaName>/arena.groovy}.
   * Pure parse — no side effects. Fragment loading + typed-record assembly
   * happens later via {@link ConfigRegistrySystem#load}.
   *
   * @return the parsed config, or {@code null} if the Groovy file is missing
   *     entirely; callers fail-fast in that case (no INI fallback —
   *     {@code arena.conf} support was retired in zone-arena-to-groovy #3)
   */
  @Nullable
  private ArenaConfig loadArenaConfig(final String arenaName) {
    return arenaLoader.load(arenaName);
  }

  /**
   * Load {@code zone.groovy} into {@link #zoneConfig} and set desired=true for each
   * arena in {@code autoLoad}. Idempotent — safe to call once at startup.
   */
  private void applyZoneStartupConfig() {
    zoneConfig = new GroovyZoneLoader().load();
    if (zoneConfig.autoLoadArenas().isEmpty()) {
      return;
    }
    for (final String name : zoneConfig.autoLoadArenas()) {
      setDesired(name, true);
    }
    log.info("zone.groovy AutoLoad: {}", zoneConfig.autoLoadArenas());
  }

  /**
   * The zone-scope config loaded at startup. Returns {@link ZoneConfig#EMPTY}
   * before {@link #applyZoneStartupConfig()} runs (idempotent fallback so
   * callers don't need null guards).
   */
  public ZoneConfig getZoneConfig() {
    return zoneConfig;
  }

  /* ---------------------------------------------------------------- */
  /* Reconciler                                                       */
  /* ---------------------------------------------------------------- */

  private void reconcileAll() {
    for (final ArenaRecord rec : registry.values()) {
      reconcile(rec.name);
    }
  }

  private void reconcile(final String arenaName) {
    final ArenaRecord rec = registry.get(arenaName);
    if (rec == null) {
      return;
    }
    if (rec.desired && rec.state == ArenaState.NOT_LOADED) {
      doLoad(rec);
    } else if (!rec.desired && rec.state == ArenaState.LOADED) {
      doUnload(rec);
    }
    // LOADING / UNLOADING / FAILED are currently terminal-within-a-tick; no-op.
  }

  private void doLoad(final ArenaRecord rec) {
    rec.state = ArenaState.LOADING;
    final MapSystem maps = getSystem(MapSystem.class);
    EntityId arena = null;
    try {
      final int allocatedSlot = allocateSlot();
      if (allocatedSlot < 0) {
        fail(rec, null, "No free arena slot (MAX_ARENAS=" + InfinityConstants.MAX_ARENAS + ")");
        return;
      }
      rec.arenaIndex = allocatedSlot;

      // arena.groovy is the only authoring surface for arena-scope config —
      // INI arena.conf was retired in zone-arena-to-groovy #3. A missing
      // arena.groovy is a configuration error rather than a fallback case.
      final ArenaConfig groovyConfig = loadArenaConfig(rec.name);
      if (groovyConfig == null) {
        fail(rec, null, "No arena.groovy found for arena '" + rec.name + "'");
        return;
      }
      rec.config = groovyConfig;
      final String mapFile = rec.config.mapFile();

      arena = ed.createEntity();
      final ArenaId arenaId = new ArenaId(rec.name, EntityId.NULL_ID);
      ed.setComponent(arena, arenaId);
      configRegistry.load(arenaId, rec.config);
      final String shipsScript =
          rec.config.shipsScript().isBlank() ? null : rec.config.shipsScript();
      registerFileWatch(
          arenaId,
          shipsScript,
          () -> {
            configRegistry.load(arenaId, rec.config);
            final int reprojected = getSystem(ShipSpawnSystem.class).reprojectAll();
            log.info(
                "{} changed for arena {}; reprojected {} ship(s)",
                shipsScript, arenaId.getArena(), reprojected);
          });
      // Watch every Groovy includeFragment so editing a fragment file at
      // runtime triggers a full re-load through ConfigRegistrySystem.
      // Consumers re-read on next consumption — no event/callback fires.
      // Non-Groovy fragments aren't supported by the loader anymore, so the
      // filter is just defence in depth against a stale arena.groovy.
      for (final String fragmentPath : rec.config.fragmentIncludes()) {
        if (fragmentPath != null && fragmentPath.endsWith(".groovy")) {
          registerFileWatch(
              arenaId,
              fragmentPath,
              () -> {
                configRegistry.load(arenaId, rec.config);
                log.info(
                    "{} changed for arena {}; settings reloaded",
                    fragmentPath, arenaId.getArena());
              });
        }
      }

      if (!maps.loadMap(mapFile, rec.arenaIndex)) {
        fail(rec, arena, "loadMap returned false for " + mapFile);
        return;
      }
      final Vec3d maxB = maps.getMapBoundsMax(mapFile);
      final Vec3d minB = maps.getMapBoundsMin(mapFile);
      ed.setComponent(arena, new ArenaMap(minB, maxB, mapFile, rec.arenaIndex));
      // Slice U1 — radar reads ArenaFootprint to draw the arena's footprint as a
      // closed polygon (interior fill + outline). Geometry is the same min/max
      // rectangle ArenaMap carries; published as a separate component so the
      // radar consumes presentation data, not gameplay state.
      ed.setComponent(arena, ArenaFootprint.rectangle(minB, maxB));

      rec.entityId = arena;

      // Arena ghost-cube: covers the full map bounds (1 TILE_SIZE per arena slot).
      // Anchored at the map's min-corner (`minB`); scale `2 × TILE_SIZE` because
      // MBlockShape.createCube uses cell-scale = extents/2, so passing 2×edge
      // produces an edge-sized cube. Each arena slot is one TILE_GRID cell, so
      // every loaded arena gets its own 1024×1024×1024 cube positioned by
      // `MapSystem.calculateNextOffset`'s spiral layout.
      //
      // Sphere-vs-cube contacts route through ContactSystem (verified 2026-04-26 —
      // the earlier "Type.Blocks bypasses ContactSystem" finding only applied to
      // Blocks-vs-Blocks). The Sensor marker makes ContactSystem disable the
      // contact so the cube doesn't block ships, while still fanning out to
      // ArenaMembershipSystem for enter/leave events.
      ed.setComponent(arena, new Mass(0));
      // SpawnPosition keyed to TILE_GRID so the coarse populator's per-bin query
      // (Filters.fieldEquals(SpawnPosition, "binId", coarseBin.cellId)) matches.
      ed.setComponent(arena, new SpawnPosition(WorldGrids.TILE_GRID, minB));
      ed.setComponent(
          arena, ShapeInfo.create(ShapeNames.ARENA, InfinityConstants.TILE_SIZE * 2.0, ed));
      ed.setComponent(arena, new Sensor());
      // Routes the entity to the coarse static-only bin index (1024-tile cells)
      // so contact-gen sees it from any fine bin within its bounds, not just
      // the corner LEAF_GRID cell containing the body's center.
      ed.setComponent(arena, new LargeObject());
      // Set LargeGridCell explicitly. moss's LargeGridIndexSystem is supposed
      // to produce this from the SpawnPosition + LargeObject change events,
      // but its `return` (instead of `continue`) when a duplicate id is polled
      // means the second arena loaded in the same frame can be skipped.
      // Setting it directly here is idempotent and avoids the race.
      ed.setComponent(arena, LargeGridCell.create(WorldGrids.TILE_GRID, minB));
      log.info(
          "Arena {} ghost-cube placed: anchor={} edge={} bounds=[{}..{}] (sensor)",
          rec.name, minB, InfinityConstants.TILE_SIZE, minB, maxB);

      materializePrizeSpawners(rec, arena);

      rec.state = ArenaState.LOADED;
      rec.lastError = null;
      log.info("Arena {} loaded with map {} at slot {}", rec.name, mapFile, rec.arenaIndex);
    } catch (final Exception e) {
      fail(rec, arena, e.toString());
      log.error("Arena {} load failed", rec.name, e);
    }
  }

  /**
   * Translate each {@link SpawnerSpec} from the arena's typed config into
   * a real spawner entity inside the loaded arena. Arena-local {@code (x, z)}
   * is mapped to world coords via {@link #arenaToWorld}, then handed to
   * {@link GameEntities#createSpawner(EntityData, EntityId,
   * PhysicsSpace, long, Vec3d, double, boolean, double, int, long,
   * java.util.Map)} so the
   * resulting spawner carries the per-spawner {@code maxCount} and (optional)
   * {@code PrizeDecayMillis}. The spawner is tagged with the arena's
   * {@link ArenaId} so {@code PrizeSystem}'s membership-aware lookups (e.g.
   * the {@code ShipConfig} read in {@code handleAcquireBomb}) keep working
   * for the prizes it produces.
   */
  private void materializePrizeSpawners(final ArenaRecord rec, final EntityId arenaEntity) {
    final List<SpawnerSpec> specs = rec.config.spawners();
    if (specs == null || specs.isEmpty()) {
      return;
    }
    final ArenaMap map = ed.getComponent(arenaEntity, ArenaMap.class);
    if (map == null) {
      log.warn(
          "Arena {} has spawners but no ArenaMap; skipping {} spawner(s)",
          rec.name, specs.size());
      return;
    }
    @SuppressWarnings("rawtypes")
    final PhysicsSpace phys = getSystem(PhysicsSpace.class, true);
    final long now = getSystem(InfinityTimeSystem.class).getTime();
    final ArenaId arenaId = new ArenaId(rec.name, arenaEntity);
    for (final SpawnerSpec spec : specs) {
      final Vec3d worldPos = arenaToWorld(map, spec.x(), spec.z());
      final EntityId spawnerId =
          GameEntities.createSpawner(
              ed,
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
              spec.hidden());
      ed.setComponent(spawnerId, arenaId);
      log.info(
          "Arena {} prize spawner {} placed at arena({},{}) world{} radius={} max={} ttlMs={}"
              + " overrides={}",
          rec.name,
          spawnerId,
          spec.x(), spec.z(),
          worldPos,
          spec.radius(),
          spec.maxCount(),
          spec.ttlMillis(),
          spec.weightOverrides().isEmpty() ? "<none>" : spec.weightOverrides());
    }
  }

  private void fail(final ArenaRecord rec, final EntityId arena, final String reason) {
    if (arena != null) {
      ed.removeEntity(arena);
    }
    releaseSlot(rec);
    rec.state = ArenaState.FAILED;
    rec.lastError = reason;
  }

  private void doUnload(final ArenaRecord rec) {
    rec.state = ArenaState.UNLOADING;
    unregisterScriptWatch(rec.name);
    try {
      getSystem(MapSystem.class).unloadMap(rec.config.mapFile());
      if (rec.entityId != null) {
        ed.removeEntity(rec.entityId);
        rec.entityId = null;
      }
      releaseSlot(rec);
      rec.state = ArenaState.NOT_LOADED;
      rec.lastError = null;
      log.info("Arena {} unloaded", rec.name);
    } catch (final Exception e) {
      rec.state = ArenaState.FAILED;
      rec.lastError = e.toString();
      log.error("Arena {} unload failed", rec.name, e);
    }
  }

  /** Find and reserve a free arena slot. Returns the index or {@code -1} if the table is full. */
  private int allocateSlot() {
    for (int i = 0; i < arenaSlots.length; i++) {
      if (!arenaSlots[i]) {
        arenaSlots[i] = true;
        return i;
      }
    }
    return -1;
  }

  /** Release the slot this record held (idempotent — no-op if it wasn't holding one). */
  private void releaseSlot(final ArenaRecord rec) {
    if (rec.arenaIndex >= 0 && rec.arenaIndex < arenaSlots.length) {
      arenaSlots[rec.arenaIndex] = false;
    }
    rec.arenaIndex = -1;
  }

  private String describe(final String arenaName) {
    final ArenaRecord rec = registry.get(arenaName);
    if (rec == null) {
      return "Arena " + arenaName + " not in registry";
    }
    switch (rec.state) {
      case LOADED:
        return "Arena " + arenaName + " loaded";
      case LOADING:
        return "Arena " + arenaName + " loading";
      case UNLOADING:
        return "Arena " + arenaName + " unloading";
      case NOT_LOADED:
        return "Arena " + arenaName + " not loaded";
      case FAILED:
        return "Arena " + arenaName + " failed: " + rec.lastError;
      default:
        return "Arena " + arenaName + " state=" + rec.state;
    }
  }

  /* ---------------------------------------------------------------- */
  /* Chat command handlers — imperative face over declarative core    */
  /* ---------------------------------------------------------------- */

  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String loadArenaByNameCommand(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    return loadArena(matcher.group(1));
  }

  /**
   * {@code ~arenas} — list every loaded arena with its world bounds + centre,
   * marking the arena that contains the player's avatar with {@code [you]}.
   * Helps operators navigate a multi-arena zone (e.g. {@code testarena} +
   * {@code trench} + {@code deva} all autoLoaded).
   */
  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String listArenasCommand(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    arenaEntities.applyChanges();
    if (arenaEntities.isEmpty()) {
      return "No arenas loaded.";
    }

    Vec3d avatarPos = null;
    if (avatarEntityId != null) {
      final BodyPosition bp = ed.getComponent(avatarEntityId, BodyPosition.class);
      if (bp != null) {
        avatarPos = bp.getLastLocation();
      }
    }

    final StringBuilder out = new StringBuilder();
    out.append("Loaded arenas (").append(arenaEntities.size()).append("):\n");
    for (final Entity arena : arenaEntities) {
      final ArenaId arenaId = arena.get(ArenaId.class);
      final ArenaMap map = arena.get(ArenaMap.class);
      final Vec3d min = map.getMin();
      final Vec3d max = map.getMax();
      final boolean youAreHere =
          avatarPos != null
              && avatarPos.x >= min.x
              && avatarPos.x < max.x
              && avatarPos.z >= min.z
              && avatarPos.z < max.z;
      out.append("  ")
          .append(arenaId.getArena())
          .append(" — bounds=(")
          .append((int) min.x).append(',').append((int) min.z)
          .append(")..(")
          .append((int) max.x).append(',').append((int) max.z)
          .append(") centre=(")
          .append((int) ((min.x + max.x) / 2)).append(',').append((int) ((min.z + max.z) / 2))
          .append(')');
      if (youAreHere) {
        out.append(" [you]");
      }
      out.append('\n');
    }
    return out.toString().trim();
  }

  /**
   * {@code ~loadMap <mapFile>} — the map's base name is used as the arena name, per current
   * convention.
   */
  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String loadArenaByMapCommand(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    final String mapFile = matcher.group(1);
    final String arenaName = mapFile.substring(0, mapFile.lastIndexOf('.'));
    return loadArena(arenaName);
  }

  /** {@code ~unloadMap <mapFile>} — resolves to its owning arena and flips desired=false. */
  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String unloadArenaByMapCommand(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    final String mapFile = matcher.group(1);
    final String arenaName = findArenaByMap(mapFile);
    if (arenaName == null) {
      return "No arena currently loaded with map " + mapFile;
    }
    setDesired(arenaName, false);
    reconcile(arenaName);
    return describe(arenaName);
  }

  /**
   * {@code ~swapMap <arenaName> <newMap>} — replace the map of a loaded arena in place. The arena
   * entity, name, and settings are preserved; only the underlying map cells change.
   */
  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String swapArenaCommand(
      final EntityId id, final EntityId avatarEntityId, final Matcher matcher) {
    final String arenaName = matcher.group(1);
    final String newMap = matcher.group(2);

    final ArenaRecord rec = registry.get(arenaName);
    if (rec == null || rec.state != ArenaState.LOADED) {
      return "Arena " + arenaName + " is not loaded";
    }

    final String oldMap = rec.config.mapFile();
    if (oldMap.equals(newMap)) {
      return "Arena " + arenaName + " already uses map " + newMap;
    }
    if (!getSystem(MapSystem.class).swapMap(oldMap, newMap, rec.arenaIndex)) {
      return "Cannot swap: " + newMap + " has an invalid extension or swap failed";
    }
    // Update the typed config — single source of truth for in-memory arena state.
    rec.config = new ArenaConfig(
        newMap,
        rec.config.shipsScript(),
        rec.config.spawnX(),
        rec.config.spawnZ(),
        rec.config.fragmentIncludes(),
        rec.config.wallFriction(),
        rec.config.spawners(),
        rec.config.friendlyFire());
    return "Arena " + arenaName + " map swapped from " + oldMap + " to " + newMap;
  }

  /** Scan open arenas for one whose current map file equals {@code mapFile}. */
  private String findArenaByMap(final String mapFile) {
    for (final ArenaRecord rec : registry.values()) {
      if (rec.state != ArenaState.LOADED) {
        continue;
      }
      if (mapFile.equals(rec.config.mapFile())) {
        return rec.name;
      }
    }
    return null;
  }

  /* ---------------------------------------------------------------- */
  /* ArenaManager API                                                 */
  /* ---------------------------------------------------------------- */

  @Override
  public String[] getActiveArenas() {
    return registry.values().stream()
        .filter(r -> r.state == ArenaState.LOADED)
        .map(r -> r.name)
        .toArray(String[]::new);
  }

  @Override
  public String getDefaultArenaId() {
    return DEFAULT_ARENA_ID;
  }
}
