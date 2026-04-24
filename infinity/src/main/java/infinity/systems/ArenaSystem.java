/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.systems;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mworld.WorldGrids;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.ShapeNames;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.es.arena.ArenaSettings;
import infinity.es.ship.Player;
import infinity.server.AssetLoaderService;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.ArenaManager;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandTriFunction;
import infinity.sim.CoreGameConstants;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.ini4j.Ini;
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
    String lastError;

    ArenaRecord(final String name) {
      this.name = name;
    }
  }

  private static final String ZONE_CONFIG_PATH = "zone.conf";
  private static final String ARENA_ROOT = "arenas";
  private static final String ARENA_CONF = "arena.conf";

  private final Map<String, ArenaRecord> registry = new ConcurrentHashMap<>();

  private EntityData ed;
  private EntitySet arenaEntities;
  private EntitySet playerEntities;
  private boolean bootstrapped;

  private final Pattern loadMap = Pattern.compile("\\~loadMap\\s(\\w+.(?:lvl|lvz))");
  private final Pattern unloadMap = Pattern.compile("\\~unloadMap\\s(\\w+.(?:lvl|lvz))");
  private final Pattern swapMap =
      Pattern.compile("\\~swapMap\\s([\\w()\\-]+)\\s+(\\w+\\.(?:lvl|lvz))");
  private final Pattern loadArenaByName = Pattern.compile("\\~loadArena\\s([\\w()\\-]+)");

  @Override
  protected void initialize() {
    final ChatHostedPoster chat = getSystem(InfinityChatHostedService.class);
    ed = getSystem(EntityData.class);
    arenaEntities = ed.getEntities(ArenaId.class);
    playerEntities = ed.getEntities(Player.class, BodyPosition.class);

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
    // SettingsSystem/AssetLoaderService are registered after ArenaSystem, so their loaders aren't
    // ready at initialize() time. Defer discovery + startup config to the first tick.
    if (!bootstrapped) {
      bootstrap();
      bootstrapped = true;
    }
    playerEntities.applyChanges();
    arenaEntities.applyChanges();
    reconcileAll();
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

  /** Read {@code zone.conf} and set desired=true for each arena in {@code [Startup] AutoLoad=}. */
  private void applyZoneStartupConfig() {
    final AssetLoaderService assets = getSystem(AssetLoaderService.class);
    Ini zone = null;
    try {
      zone = (Ini) assets.loadAsset(ZONE_CONFIG_PATH);
    } catch (final Exception e) {
      log.info("{} not present; no auto-load list ({})", ZONE_CONFIG_PATH, e.getMessage());
      return;
    }
    if (zone == null) {
      return;
    }
    final String autoLoad = zone.fetch("Startup", "AutoLoad");
    if (autoLoad == null || autoLoad.isBlank()) {
      return;
    }
    for (final String raw : autoLoad.split("[,\\s]+")) {
      final String name = raw.trim();
      if (!name.isEmpty()) {
        setDesired(name, true);
      }
    }
    log.info("zone.conf AutoLoad: {}", autoLoad);
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
    final SettingsSystem settings = getSystem(SettingsSystem.class);
    final MapSystem maps = getSystem(MapSystem.class);
    EntityId arena = null;
    try {
      settings.loadSettings(EntityId.NULL_ID, rec.name);
      final String mapFile = settings.getString(rec.name, "General", "Map", rec.name + ".lvl");

      arena = ed.createEntity();
      ed.setComponent(arena, new ArenaId(rec.name, EntityId.NULL_ID));

      if (!maps.loadMap(mapFile)) {
        fail(rec, arena, "loadMap returned false for " + mapFile);
        return;
      }
      final Vec3d maxB = maps.getMapBoundsMax(mapFile);
      final Vec3d minB = maps.getMapBoundsMin(mapFile);
      ed.setComponent(arena, new ArenaMap(minB, maxB, mapFile));

      final Ini ini = settings.getIni(rec.name);
      ed.setComponent(arena, new ArenaSettings(rec.name, ini));

      rec.entityId = arena;

      ed.setComponent(arena, new Mass(0));
      ed.setComponent(arena, new SpawnPosition(WorldGrids.LEAF_GRID, new Vec3d()));
      ed.setComponent(arena, ShapeInfo.create(ShapeNames.ARENA, 1, ed));

      rec.state = ArenaState.LOADED;
      rec.lastError = null;
      log.info("Arena {} loaded with map {}", rec.name, mapFile);
    } catch (final Exception e) {
      fail(rec, arena, e.toString());
      log.error("Arena " + rec.name + " load failed", e);
    }
  }

  private void fail(final ArenaRecord rec, final EntityId arena, final String reason) {
    if (arena != null) {
      ed.removeEntity(arena);
    }
    rec.state = ArenaState.FAILED;
    rec.lastError = reason;
  }

  private void doUnload(final ArenaRecord rec) {
    rec.state = ArenaState.UNLOADING;
    try {
      final SettingsSystem settings = getSystem(SettingsSystem.class);
      final String mapFile = settings.getString(rec.name, "General", "Map", rec.name + ".lvl");
      getSystem(MapSystem.class).unloadMap(mapFile);
      if (rec.entityId != null) {
        ed.removeEntity(rec.entityId);
        rec.entityId = null;
      }
      rec.state = ArenaState.NOT_LOADED;
      rec.lastError = null;
      log.info("Arena {} unloaded", rec.name);
    } catch (final Exception e) {
      rec.state = ArenaState.FAILED;
      rec.lastError = e.toString();
      log.error("Arena " + rec.name + " unload failed", e);
    }
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

  private String loadArenaByNameCommand(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    return loadArena(matcher.group(1));
  }

  /**
   * {@code ~loadMap <mapFile>} — the map's base name is used as the arena name, per current
   * convention.
   */
  private String loadArenaByMapCommand(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    final String mapFile = matcher.group(1);
    final String arenaName = mapFile.substring(0, mapFile.lastIndexOf('.'));
    return loadArena(arenaName);
  }

  /** {@code ~unloadMap <mapFile>} — resolves to its owning arena and flips desired=false. */
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
  private String swapArenaCommand(
      final EntityId id, final EntityId avatarEntityId, final Matcher matcher) {
    final String arenaName = matcher.group(1);
    final String newMap = matcher.group(2);

    final ArenaRecord rec = registry.get(arenaName);
    if (rec == null || rec.state != ArenaState.LOADED) {
      return "Arena " + arenaName + " is not loaded";
    }

    final SettingsSystem settings = getSystem(SettingsSystem.class);
    final String oldMap = settings.getString(arenaName, "General", "Map", arenaName + ".lvl");
    if (oldMap.equals(newMap)) {
      return "Arena " + arenaName + " already uses map " + newMap;
    }
    if (!getSystem(MapSystem.class).swapMap(oldMap, newMap)) {
      return "Cannot swap: " + newMap + " has an invalid extension or swap failed";
    }
    settings.setSetting(
        ed.getComponent(rec.entityId, ArenaId.class), "General", "Map", newMap);
    return "Arena " + arenaName + " map swapped from " + oldMap + " to " + newMap;
  }

  /** Scan open arenas for one whose current {@code [General] Map=} equals {@code mapFile}. */
  private String findArenaByMap(final String mapFile) {
    final SettingsSystem settings = getSystem(SettingsSystem.class);
    for (final ArenaRecord rec : registry.values()) {
      if (rec.state != ArenaState.LOADED) {
        continue;
      }
      final String current = settings.getString(rec.name, "General", "Map", rec.name + ".lvl");
      if (mapFile.equals(current)) {
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
    return CoreGameConstants.DEFAULTARENAID;
  }
}
