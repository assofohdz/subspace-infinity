// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.InfinityConstants;
import infinity.config.ArenaConfig;
import infinity.config.SpawnerSpec;
import infinity.config.SpawnConfig;
import infinity.config.ZoneConfig;
import infinity.es.arena.ArenaId;
import infinity.systems.ship.ShipSpawnSystem;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.GroovyArenaLoader;
import infinity.settings.GroovyZoneLoader;
import infinity.es.arena.ArenaMap;
import infinity.es.ship.Player;
import infinity.sim.ArenaManager;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import java.util.stream.Collectors;
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
   * Cached zone config, loaded at startup and hot-reloaded by the per-tick
   * mtime watcher (see {@link #pollZoneGroovyReload}). Volatile because the
   * watcher writes from {@link #update} while consumers may read from
   * different threads (chat command handlers, network sessions). Other
   * systems reach this via {@link #getZoneConfig()} so consumers stay
   * decoupled from the Groovy loader and from the zone.groovy path.
   */
  private volatile ZoneConfig zoneConfig = ZoneConfig.EMPTY;

  /**
   * Hot-reload state for {@code zone/zone.groovy}. Mirrors the
   * {@link infinity.settings.EngineConfigSystem} pattern (single-file
   * watcher, no per-arena map). Resolved at bootstrap; {@code null} when
   * the file isn't on disk (production / classpath-only deployments —
   * live reload is silently disabled).
   */
  @Nullable private java.nio.file.Path zoneGroovyPath;
  @Nullable private java.nio.file.attribute.FileTime zoneGroovyMtime;

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
   * Hot-reload watcher for Groovy files (the arena's {@code ships.groovy} +
   * every {@code .groovy} fragment in {@code arena.groovy}'s
   * {@code includeFragment} list). Round 24 split: state + polling cadence
   * + register/unregister methods all moved to {@link ArenaReloadWatcher}.
   * Reload callbacks delegate back to the public
   * {@link #handleShipsScriptReload} / {@link #handleFragmentReload}
   * methods on this class so the watcher stays thin.
   */
  private final ArenaReloadWatcher reloadWatcher = new ArenaReloadWatcher(this);

  // Chat command patterns + handlers moved to ArenaCommandsSystem in
  // round 23 (class-level CC split mirroring ChecksShipsSystem). This
  // class now exposes the desired-state mutators + lookup accessors that
  // ArenaCommandsSystem needs; the chat-binding glue lives there.

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class);
    arenaEntities = ed.getEntities(ArenaId.class, ArenaMap.class);
    playerEntities = ed.getEntities(Player.class, BodyPosition.class);
    configRegistry = getSystem(ConfigRegistrySystem.class);
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
    reloadWatcher.pollIfDue(tpf.getTime(), zoneConfig.scriptPollIntervalNanos());
    pollZoneGroovyReload();
  }

  /**
   * Re-reads {@code zone/zone.groovy} when its mtime changes and atomically
   * replaces the held {@link #zoneConfig} snapshot. Per-tick poll cadence
   * (rate-limited via {@link infinity.systems.ArenaReloadWatcher#pollIfDue}'s
   * mechanism is not reused here because that watcher's queue is per-arena;
   * the zone-tier file is single-instance and rarely changes, so a stat()
   * per tick is cheap enough).
   *
   * <p>Reload semantics: only the in-memory {@link ZoneConfig} snapshot is
   * replaced. {@code autoLoad} is NOT re-applied (autoLoad is a
   * startup-only intent — already-loaded arenas don't get unloaded if the
   * list shrinks). Hot-reloadable knobs are the ones consumers re-read
   * each call (repelFriendlies, scriptPollIntervalSeconds, enterSpawn).
   */
  private void pollZoneGroovyReload() {
    if (zoneGroovyPath == null) {
      return;
    }
    final java.nio.file.attribute.FileTime current;
    try {
      current = java.nio.file.Files.getLastModifiedTime(zoneGroovyPath);
    } catch (final java.io.IOException e) {
      log.debug("stat() on {} failed: {}", zoneGroovyPath, e.toString());
      return;
    }
    if (current.equals(zoneGroovyMtime)) {
      return;
    }
    zoneGroovyMtime = current;
    final ZoneConfig refreshed = new GroovyZoneLoader().load();
    zoneConfig = refreshed;
    log.info("zone.groovy reloaded (autoLoad list NOT re-applied; only the in-memory ZoneConfig snapshot is updated)");
  }

  /**
   * Register a per-arena watch on a Groovy file so a dev-mode edit fires
   * {@code onChanged} on the next throttled poll. No-op if the file isn't
   * reachable on disk (production / classpath-only deployments) or the path
   * is blank.
   */
  /* ---------------------------------------------------------------- */
  /* Hot-reload callbacks (invoked by ArenaReloadWatcher)             */
  /* ---------------------------------------------------------------- */

  /**
   * Reload callback fired by {@link ArenaReloadWatcher} when an arena's
   * {@code shipsScript} file changes on disk. Re-applies the typed config
   * via {@code ConfigRegistrySystem.load} and reprojects every live ship
   * via {@link ShipSpawnSystem#reprojectAll}.
   *
   * <p>Looks up the arena record fresh so a swap-map / hot-edit cycle
   * picks up the latest {@link ArenaConfig} rather than a stale snapshot
   * captured at watch-registration time. No-op if the arena has been
   * unloaded between watch and callback (record gone from registry).
   */
  public void handleShipsScriptReload(final ArenaId arenaId, final String shipsScript) {
    final ArenaRecord rec = registry.get(arenaId.getArena());
    if (rec == null) {
      return;
    }
    configRegistry.load(arenaId, rec.config);
    final int reprojected = getSystem(ShipSpawnSystem.class).reprojectAll();
    if (log.isInfoEnabled()) {
      log.info(
          "{} changed for arena {}; reprojected {} ship(s)",
          shipsScript, arenaId.getArena(), reprojected);
    }
  }

  /**
   * Reload callback fired by {@link ArenaReloadWatcher} when a fragment
   * referenced by {@code arena.groovy}'s {@code includeFragment} list
   * changes on disk. Asks {@code ConfigRegistrySystem} to rebuild the
   * merged settings store; consumers re-read on next consumption — no
   * event/callback fires beyond this.
   */
  public void handleFragmentReload(final ArenaId arenaId, final String fragmentPath) {
    final ArenaRecord rec = registry.get(arenaId.getArena());
    if (rec == null) {
      return;
    }
    configRegistry.load(arenaId, rec.config);
    if (log.isInfoEnabled()) {
      log.info(
          "{} changed for arena {}; settings reloaded",
          fragmentPath, arenaId.getArena());
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
      if (ArenaLogic.containsXZ(arena.get(ArenaMap.class), position)) {
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
    return ArenaLogic.resolveArenaSpawn(
        spawn, freq, map, rec.config.spawnX(), rec.config.spawnZ());
  }

  // sampleTeamSpawn moved to ArenaLogic.sampleTeamSpawn.

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
    final ArenaMap map = getArenaMap(arenaName);
    return map == null ? null : ArenaLogic.worldToArenaLocal(map, world);
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

  /**
   * Render a one-line state description for {@code arenaName}. Used by
   * {@link #loadArena} and (via the public {@link #getArenaState} / {@link
   * #getArenaError} accessors) by {@link ArenaCommandsSystem}'s identical
   * helper. Inline here to keep {@code loadArena}'s public-facing string
   * stable without coupling ArenaSystem to ArenaCommandsSystem.
   */
  private String describe(final String arenaName) {
    return ArenaLogic.describeArena(
        getArenaState(arenaName), arenaName, () -> getArenaError(arenaName));
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
  private void discoverArenas() throws IOException, URISyntaxException {
    ArenaLogic.discoverArenaNames(
        ARENA_ROOT,
        ARENA_CONF,
        name -> registry.computeIfAbsent(name, ArenaRecord::new),
        log);
    if (log.isInfoEnabled()) {
      log.info(
          "Discovered {} arena(s): {}",
          registry.size(),
          registry.keySet().stream().sorted().collect(Collectors.toList()));
    }
  }

  // stripTrailingSlash moved to ArenaLogic.stripTrailingSlash.

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
   *
   * <p>Also resolves {@code zone/zone.groovy} on disk and stores its initial
   * mtime so {@link #pollZoneGroovyReload} can detect subsequent edits.
   * Resolution falls back to {@code null} when the file is classpath-only
   * (production deployments without a dist install of {@code zone/}); live
   * reload is silently disabled in that case.
   */
  private void applyZoneStartupConfig() {
    zoneConfig = new GroovyZoneLoader().load();
    zoneGroovyPath = infinity.settings.GroovySettingsHost.INSTANCE.resolveOnDisk(GroovyZoneLoader.DEFAULT_PATH);
    if (zoneGroovyPath != null) {
      try {
        zoneGroovyMtime = java.nio.file.Files.getLastModifiedTime(zoneGroovyPath);
        log.info("Watching {} for zone-tier hot-reload", zoneGroovyPath);
      } catch (final java.io.IOException e) {
        log.debug("Initial stat() on {} failed: {}; live reload disabled", zoneGroovyPath, e.toString());
        zoneGroovyPath = null;
      }
    } else {
      log.debug("{} not on disk; zone-tier live reload disabled for this run", GroovyZoneLoader.DEFAULT_PATH);
    }
    if (zoneConfig.autoLoadArenas().isEmpty()) {
      return;
    }
    for (final String name : zoneConfig.autoLoadArenas()) {
      setDesired(name, true);
    }
    if (log.isInfoEnabled()) {
      log.info("zone.groovy AutoLoad: {}", zoneConfig.autoLoadArenas());
    }
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

  /**
   * Drive one arena's state toward its declared {@code desired} bit. Called
   * each tick by {@link #reconcileAll} and synchronously by {@link #loadArena}
   * + {@link ArenaCommandsSystem}'s {@code ~unloadMap} so chat handlers can
   * return an accurate post-action status string. Package-private so
   * {@link ArenaCommandsSystem} (same package) can reach it without exposing
   * it on the public API.
   */
  void reconcile(final String arenaName) {
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
      reloadWatcher.registerArenaReloadWatches(
          arenaId, rec.config.shipsScript(), rec.config.fragmentIncludes());

      if (!maps.loadMap(mapFile, rec.arenaIndex)) {
        fail(rec, arena, "loadMap returned false for " + mapFile);
        return;
      }
      // Ghost-cube setup: covers the full map bounds (1 TILE_SIZE per arena slot).
      // Sphere-vs-cube contacts route through ContactSystem; Sensor marker
      // makes ContactSystem disable the contact so the cube doesn't block ships,
      // while still fanning out to ArenaMembershipSystem for enter/leave events.
      // LargeGridCell is set directly to avoid moss's LargeGridIndexSystem
      // dropping the second arena loaded in the same frame.
      ArenaLogic.configureGhostCube(
          ed, arena,
          maps.getMapBoundsMin(mapFile), maps.getMapBoundsMax(mapFile),
          rec.arenaIndex, mapFile, rec.name, log);
      rec.entityId = arena;

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
   * {@link infinity.sim.MapFactory#createSpawner(EntityData, EntityId,
   * PhysicsSpace, long, Vec3d, double, boolean, double, int, long,
   * java.util.Map, int, double, int, boolean)} so the
   * resulting spawner carries the per-spawner {@code maxCount} and (optional)
   * {@code PrizeDecayMillis}. The spawner is tagged with the arena's
   * {@link ArenaId} so {@code PrizeSystem}'s membership-aware lookups (e.g.
   * the {@code ShipConfig} read in {@code handleAcquireBomb}) keep working
   * for the prizes it produces.
   */
  private void materializePrizeSpawners(final ArenaRecord rec, final EntityId arenaEntity) {
    @SuppressWarnings("rawtypes")
    final PhysicsSpace phys = getSystem(PhysicsSpace.class, true);
    ArenaLogic.materializeSpawners(
        ed, phys, getSystem(InfinityTimeSystem.class).getTime(),
        new ArenaId(rec.name, arenaEntity),
        ed.getComponent(arenaEntity, ArenaMap.class),
        rec.name, rec.config.spawners(), log);
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
    reloadWatcher.unregisterScriptWatch(rec.name);
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

  /* ---------------------------------------------------------------- */
  /* Public accessors for ArenaCommandsSystem                         */
  /* ---------------------------------------------------------------- */

  /**
   * Lifecycle state of the named arena, or {@code null} if the arena is not
   * in the registry. Exposed for {@link ArenaCommandsSystem}'s {@code describe}
   * helper so command handlers can render state-aware status strings without
   * reaching into the private registry.
   */
  @Nullable
  public ArenaState getArenaState(final String arenaName) {
    final ArenaRecord rec = registry.get(arenaName);
    return rec == null ? null : rec.state;
  }

  /**
   * Last error message recorded for an arena in {@link ArenaState#FAILED}
   * state, or {@code null} if no error / arena absent. Companion to
   * {@link #getArenaState} for the FAILED branch of the state-render helper.
   */
  @Nullable
  public String getArenaError(final String arenaName) {
    final ArenaRecord rec = registry.get(arenaName);
    return rec == null ? null : rec.lastError;
  }

  /**
   * In-place map swap for a loaded arena. Returns a human-readable status
   * string (rendered by {@code ~swapMap} chat command). The arena entity,
   * name, and settings are preserved; only the underlying map cells change
   * via {@link MapSystem#swapMap}, and {@link ArenaRecord#config} is
   * rebuilt with the new map name.
   */
  public String swapArenaMap(final String arenaName, final String newMap) {
    final ArenaRecord rec = registry.get(arenaName);
    if (rec == null) {
      return "Arena " + arenaName + " is not loaded";
    }
    final ArenaLogic.SwapMapOutcome outcome = ArenaLogic.swapArenaMap(
        rec.state, rec.config, rec.arenaIndex, arenaName, newMap,
        () -> getSystem(MapSystem.class).swapMap(rec.config.mapFile(), newMap, rec.arenaIndex));
    if (outcome.config != null) {
      rec.config = outcome.config;
    }
    return outcome.message;
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
