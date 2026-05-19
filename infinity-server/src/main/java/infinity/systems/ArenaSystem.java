// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.sim.SimTime;
import infinity.InfinityConstants;
import infinity.config.ArenaConfig;
import infinity.config.SpawnerSpec;
import infinity.config.ZoneConfig;
import infinity.es.arena.ArenaId;
import infinity.systems.ship.ShipSpawnSystem;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.GroovyArenaLoader;
import infinity.settings.GroovyFileWatcher;
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

/** Arena lifecycle manager — registry of arenas + per-tick reconcile of {@code desired} ↔ {@link ArenaState}. */
public class ArenaSystem extends BaseInfinitySystem implements ArenaManager {

  static final Logger log = LoggerFactory.getLogger(ArenaSystem.class);

  public enum ArenaState {
    NOT_LOADED, LOADING, LOADED, UNLOADING, FAILED
  }

  static final class ArenaRecord {
    final String name;
    boolean desired;
    ArenaState state = ArenaState.NOT_LOADED;
    EntityId entityId;
    int arenaIndex = -1; // assigned by the slot allocator at load-time; -1 when not loaded
    String lastError;
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
   * {@link #zoneWatcher}. Volatile because the watcher writes from
   * {@link #update} while consumers may read from different threads (chat
   * command handlers, network sessions). Other systems reach this via
   * {@link #getZoneConfig()} so consumers stay decoupled from the Groovy
   * loader and from the zone.groovy path.
   */
  private volatile ZoneConfig zoneConfig = ZoneConfig.EMPTY;

  /**
   * Hot-reload watcher for {@code zone/zone.groovy}. Mirrors the
   * {@link infinity.settings.EngineConfigSystem} pattern (single-file
   * watcher, no per-arena map). {@code null} when the file isn't on disk
   * (production / classpath-only deployments — live reload is silently
   * disabled).
   */
  @Nullable private GroovyFileWatcher<ZoneConfig> zoneWatcher;

  /**
   * Slot allocator for arena indices. Each {@code true} entry means the slot is in use by a
   * currently-loaded arena. Indices map 1:1 to the per-arena block-type ranges on the client
   * (tiles 1..190 of arena N live at block types {@code TILE_TYPE_BASE + N * 190}..+189).
   * Released on unload so the next load can reuse the slot.
   */
  private final boolean[] arenaSlots = new boolean[InfinityConstants.MAX_ARENAS];

  private EntityData ed;
  private EntitySet playerEntities;
  private ConfigRegistrySystem configRegistry;
  private final GroovyArenaLoader arenaLoader = new GroovyArenaLoader();
  private boolean bootstrapped;

  private final ArenaReloadWatcher reloadWatcher = new ArenaReloadWatcher(this);

  private final ArenaSpatialIndex spatialIndex = new ArenaSpatialIndex(this);

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    // spatialIndex looks up ArenaModuleSystem lazily — ArenaSystem boots before
    // ArenaModuleSystem in GameServer's registration order, so capturing here
    // would always be null. The index resolves per-call via this::getModuleSystem.
    spatialIndex.initialize(ed, this::getModuleSystem);
    playerEntities = ed.getEntities(Player.class, BodyPosition.class);
    configRegistry = requireSystem(ConfigRegistrySystem.class);
  }

  /** Lazy lookup so spatialIndex resolves the module system after both systems have initialized. */
  @javax.annotation.Nullable
  infinity.modules.ArenaModuleSystem getModuleSystem() {
    return getSystem(infinity.modules.ArenaModuleSystem.class);
  }

  @Override
  protected void terminate() {
    spatialIndex.terminate();

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
    spatialIndex.applyChanges();
    reconcileAll();
    reloadWatcher.pollIfDue(tpf.getTime(), zoneConfig.scriptPollIntervalNanos());
    if (zoneWatcher != null) {
      zoneWatcher.poll();
    }
  }

  /** Reapplies typed config and reprojects every live ship. */
  public void handleShipsScriptReload(final ArenaId arenaId, final String shipsScript) {
    final ArenaRecord rec = registry.get(arenaId.getArena());
    if (rec == null) {
      return;
    }
    configRegistry.load(arenaId, rec.config);
    final int reprojected = requireSystem(ShipSpawnSystem.class).reprojectAll();
    if (log.isInfoEnabled()) {
      log.info(
          "{} changed for arena {}; reprojected {} ship(s)",
          shipsScript, arenaId.getArena(), reprojected);
    }
  }

  /** Rebuilds the merged settings store; consumers re-read on next use. */
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

  /* ---------------------------------------------------------------- */
  /* Spatial queries — thin forwarders to {@link ArenaSpatialIndex}.  */
  /* The implementations live there (round 25 class-CC slice); these  */
  /* delegates keep existing callers' call shape stable.              */
  /* ---------------------------------------------------------------- */

  /** See {@link ArenaSpatialIndex#findArenaAt}. */
  @Nullable
  public ArenaId findArenaAt(final Vec3d position) {
    return spatialIndex.findArenaAt(position);
  }

  /** See {@link ArenaSpatialIndex#findArenaEntityAt}. */
  @Nullable
  public EntityId findArenaEntityAt(final Vec3d position) {
    return spatialIndex.findArenaEntityAt(position);
  }

  /** See {@link ArenaSpatialIndex#getArenaSpawn}. */
  @Nullable
  public Vec3d getArenaSpawn(final String arenaName, final int freq) {
    return spatialIndex.getArenaSpawn(arenaName, freq);
  }

  /** See {@link ArenaSpatialIndex#arenaToWorld}. */
  public static Vec3d arenaToWorld(final ArenaMap map, final double localX, final double localZ) {
    return ArenaSpatialIndex.arenaToWorld(map, localX, localZ);
  }

  /** {@link ArenaConfig#EMPTY} when unknown / not-yet-loaded — never null. */
  public ArenaConfig getArenaConfig(final String arenaName) {
    final ArenaRecord rec = registry.get(arenaName);
    if (rec == null) {
      return ArenaConfig.EMPTY;
    }
    return rec.config;
  }

  /**
   * Per-arena wall friction without exposing {@link ArenaConfig} to the caller. Lets hot-path
   * consumers (e.g. {@code ContactSystem}) avoid importing {@code infinity.config..} per ADR-0002.
   * Falls back to {@link infinity.config.PhysicsDefaults#DEFAULT_WALL_FRICTION} on unknown arena.
   */
  public double getWallFriction(final String arenaName) {
    return getArenaConfig(arenaName).wallFriction();
  }

  /** FF tri-state ({@code 0}=off, {@code 1}=splash-only, {@code 2}=all); {@link ArenaConfig#EMPTY} default on unknown. */
  public int getFriendlyFireMode(final String arenaName) {
    return getArenaConfig(arenaName).friendlyFire();
  }

  /** See {@link ArenaSpatialIndex#getArenaMap}. */
  @Nullable
  public ArenaMap getArenaMap(final String arenaName) {
    return spatialIndex.getArenaMap(arenaName);
  }

  /** See {@link ArenaSpatialIndex#worldToArena}. */
  @Nullable
  public Vec3d worldToArena(final String arenaName, final Vec3d world) {
    return spatialIndex.worldToArena(arenaName, world);
  }

  @Nullable
  ArenaRecord lookupRecord(final String arenaName) {
    return registry.get(arenaName);
  }

  ConfigRegistrySystem getConfigRegistry() {
    return configRegistry;
  }

  /** Declare whether the named arena should be running; reconcile makes reality match. */
  public void setDesired(final String arenaName, final boolean desired) {
    registry.computeIfAbsent(arenaName, ArenaRecord::new).desired = desired;
  }

  /** Flips desired=true and reconciles now; returns the reached state. */
  public String loadArena(final String arenaName) {
    setDesired(arenaName, true);
    reconcile(arenaName);
    return describe(arenaName);
  }

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

  /** Pure parse; {@code null} when arena.groovy is missing (callers fail-fast). */
  @Nullable
  private ArenaConfig loadArenaConfig(final String arenaName) {
    return arenaLoader.load(arenaName);
  }

  /**
   * Loads zone.groovy, sets desired=true for each autoLoad arena, arms hot-reload.
   * <p>Reload semantics: only the in-memory {@link ZoneConfig} snapshot is
   * replaced. {@code autoLoad} is NOT re-applied (startup-only intent —
   * already-loaded arenas don't get unloaded if the list shrinks). Hot-reloadable
   * knobs are those consumers re-read each call (repelFriendlies,
   * scriptPollIntervalSeconds, enterSpawn).
   */
  private void applyZoneStartupConfig() {
    zoneConfig = new GroovyZoneLoader().load();
    final java.nio.file.Path onDisk =
        infinity.settings.GroovySettingsHost.INSTANCE.resolveOnDisk(GroovyZoneLoader.DEFAULT_PATH);
    if (onDisk != null) {
      final GroovyFileWatcher<ZoneConfig> w =
          new GroovyFileWatcher<>(
              onDisk,
              () -> new GroovyZoneLoader().load(),
              refreshed -> {
                zoneConfig = refreshed;
                log.info(
                    "zone.groovy reloaded (autoLoad list NOT re-applied;"
                        + " only the in-memory ZoneConfig snapshot is updated)");
              });
      if (w.arm()) {
        zoneWatcher = w;
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

  /** {@link ZoneConfig#EMPTY} before startup-apply runs — never null. */
  public ZoneConfig getZoneConfig() {
    return zoneConfig;
  }

  private void reconcileAll() {
    for (final ArenaRecord rec : registry.values()) {
      reconcile(rec.name);
    }
  }

  /** Drives one arena's state toward its desired bit. */
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
    final MapSystem maps = requireSystem(MapSystem.class);
    EntityId arena = null;
    try {
      final int allocatedSlot = allocateSlot();
      if (allocatedSlot < 0) {
        fail(rec, null, "No free arena slot (MAX_ARENAS=" + InfinityConstants.MAX_ARENAS + ")");
        return;
      }
      rec.arenaIndex = allocatedSlot;

      // arena.groovy is the only authoring surface; missing arena.groovy is a config error, not a fallback.
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
      // Ghost-cube: Sensor marker makes ContactSystem disable contacts (cube doesn't block);
      // LargeGridCell set directly to dodge moss dropping the second arena loaded in the same frame.
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

  /** Materializes each {@link SpawnerSpec} as a real spawner entity tagged with the arena's {@link ArenaId}. */
  private void materializePrizeSpawners(final ArenaRecord rec, final EntityId arenaEntity) {
    @SuppressWarnings("rawtypes")
    final PhysicsSpace phys = requireSystem(PhysicsSpace.class);
    ArenaLogic.materializeSpawners(
        ed, phys, requireSystem(InfinityTimeSystem.class).getTime(),
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
      requireSystem(MapSystem.class).unloadMap(rec.config.mapFile());
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

  private int allocateSlot() {
    for (int i = 0; i < arenaSlots.length; i++) {
      if (!arenaSlots[i]) {
        arenaSlots[i] = true;
        return i;
      }
    }
    return -1;
  }

  private void releaseSlot(final ArenaRecord rec) {
    if (rec.arenaIndex >= 0 && rec.arenaIndex < arenaSlots.length) {
      arenaSlots[rec.arenaIndex] = false;
    }
    rec.arenaIndex = -1;
  }

  @Nullable
  public ArenaState getArenaState(final String arenaName) {
    final ArenaRecord rec = registry.get(arenaName);
    return rec == null ? null : rec.state;
  }

  @Nullable
  public String getArenaError(final String arenaName) {
    final ArenaRecord rec = registry.get(arenaName);
    return rec == null ? null : rec.lastError;
  }

  /** In-place map swap; preserves arena identity + settings. */
  public String swapArenaMap(final String arenaName, final String newMap) {
    final ArenaRecord rec = registry.get(arenaName);
    if (rec == null) {
      return "Arena " + arenaName + " is not loaded";
    }
    final ArenaLogic.SwapMapOutcome outcome = ArenaLogic.swapArenaMap(
        rec.state, rec.config, arenaName, newMap,
        () -> requireSystem(MapSystem.class).swapMap(rec.config.mapFile(), newMap, rec.arenaIndex));
    if (outcome.config != null) {
      rec.config = outcome.config;
    }
    return outcome.message;
  }

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
