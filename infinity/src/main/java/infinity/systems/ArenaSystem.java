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
import com.simsilica.mathd.GridCell;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mworld.WorldGrids;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.ShapeNames;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.es.arena.ArenaSettings;
import infinity.es.ship.Player;
import infinity.server.chat.InfinityChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.ArenaManager;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandTriFunction;
import infinity.sim.CoreGameConstants;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.ini4j.Ini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * State to keep track of different arenas. Arenas are composed of a tileset and a ruleset and a
 * location (since areanas are 1024x1024. This state keeps track of where the next arena can be
 * loaded and associates rulesets to each loaded arena
 *
 * @author Asser
 */
public class ArenaSystem extends AbstractGameSystem implements ArenaManager {

  static Logger log = LoggerFactory.getLogger(ArenaSystem.class);
  private final java.util.Map<Vec3d, EntityId> index = new ConcurrentHashMap<>();
  private final HashMap<String, EntityId> currentOpenArenas = new HashMap<>();
  private final HashMap<EntityId, GridCell> arenaCells = new HashMap<>();
  private EntityData ed;
  private EntitySet arenaEntities;
  private EntitySet playerEntities;
  private final Pattern loadMap = Pattern.compile("\\~loadMap\\s(\\w+.(?:lvl|lvz))");
  private final Pattern unloadMap = Pattern.compile("\\~unloadMap\\s(\\w+.(?:lvl|lvz))");
  private final Pattern swapMap =
      Pattern.compile("\\~swapMap\\s([\\w()\\-]+)\\s+(\\w+\\.(?:lvl|lvz))");
  private final Pattern loadArenaByName = Pattern.compile("\\~loadArena\\s([\\w()\\-]+)");

  @Override
  protected void initialize() {

    ChatHostedPoster chat = getSystem(InfinityChatHostedService.class);

    ed = getSystem(EntityData.class);
    // This filters all entities that are in arenas
    arenaEntities = ed.getEntities(ArenaId.class);
    // This filters all entities that are players
    playerEntities = ed.getEntities(Player.class, BodyPosition.class);

    // Register consuming methods for patterns
    chat.registerPatternTriConsumer(
        loadMap,
        "The command to load a new map is ~loadMap <mapName>, where <mapName> is the name "
            + "of the map you want to load",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::loadArena));
    chat.registerPatternTriConsumer(
        unloadMap,
        "The command to unload a new map is ~unloadMap <mapName>, where <mapName> is the "
            + "name of the map you want to unload",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::unloadArena));
    chat.registerPatternTriConsumer(
        swapMap,
        "Swap the map of a loaded arena: ~swapMap <arenaName> <newMap>. The arena's identity and "
            + "settings stay the same; only the underlying map is replaced.",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::swapArena));
    chat.registerPatternTriConsumer(
        loadArenaByName,
        "The command to load an arena by name is ~loadArena <arenaName>. Reads "
            + "arenas/<arenaName>/arena.conf, loads the map declared by its [General] Map= key "
            + "(falling back to <arenaName>.lvl), and attaches the settings.",
        new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::loadArenaByNameCommand));
  }

  /**
   * Handles {@code ~loadMap <mapFile>}. Treats the map's base name as the arena name, which is the
   * convention today. Settings are loaded from {@code arenas/<arenaName>/arena.conf}.
   */
  private String loadArena(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    final String mapFile = matcher.group(1);
    final String arenaName = mapFile.substring(0, mapFile.lastIndexOf('.'));
    getSystem(SettingsSystem.class).loadSettings(playerEntityId, arenaName);
    return doLoadArena(playerEntityId, arenaName, mapFile);
  }

  /**
   * Programmatic entry point for loading an arena by name (no player requester). Used by
   * server-initiated loads like {@code BasicEnvironment}'s startup bootstrap.
   */
  public String loadArena(final String arenaName) {
    return loadArenaInternal(EntityId.NULL_ID, arenaName);
  }

  /**
   * Handles {@code ~loadArena <arenaName>}. Reads {@code arenas/<arenaName>/arena.conf}, pulls the
   * map to load from the {@code [General] Map=} key (falling back to {@code <arenaName>.lvl}), and
   * builds the arena entity.
   */
  private String loadArenaByNameCommand(
      final EntityId playerEntityId, final EntityId avatarEntityId, final Matcher matcher) {
    return loadArenaInternal(playerEntityId, matcher.group(1));
  }

  private String loadArenaInternal(final EntityId requester, final String arenaName) {
    final SettingsSystem settings = getSystem(SettingsSystem.class);
    settings.loadSettings(requester, arenaName);
    final String mapFile = settings.getString(arenaName, "General", "Map", arenaName + ".lvl");
    return doLoadArena(requester, arenaName, mapFile);
  }

  /**
   * Builds the arena entity from pre-loaded settings plus the given map file. The caller must have
   * already invoked {@link SettingsSystem#loadSettings(EntityId, String)} for {@code arenaName}.
   */
  private String doLoadArena(
      final EntityId playerEntityId, final String arenaName, final String mapFile) {
    final EntityId arena = ed.createEntity();
    ed.setComponent(arena, new ArenaId(arenaName, playerEntityId));

    getSystem(MapSystem.class).loadMap(mapFile);
    final Vec3d mapBoundsMax = getSystem(MapSystem.class).getMapBoundsMax(mapFile);
    final Vec3d mapBoundsMin = getSystem(MapSystem.class).getMapBoundsMin(mapFile);
    ed.setComponent(arena, new ArenaMap(mapBoundsMin, mapBoundsMax));

    final Ini ini = getSystem(SettingsSystem.class).getIni(arenaName);
    ed.setComponents(arena, new ArenaSettings(arenaName, ini));

    final GridCell cell =
        WorldGrids.TILE_GRID.getContainingCell(mapBoundsMax.add(mapBoundsMin).divide(2));
    arenaCells.put(arena, cell);

    ed.setComponent(arena, new Mass(0));
    ed.setComponent(arena, new SpawnPosition(WorldGrids.LEAF_GRID, new Vec3d()));
    ed.setComponent(arena, ShapeInfo.create(ShapeNames.ARENA, 1, ed));

    currentOpenArenas.put(arenaName, arena);

    return "Arena " + arenaName + " loaded with map " + mapFile;
  }

  /**
   * Handles {@code ~unloadMap <mapFile>}. Resolves the map to its owning arena (the one whose
   * {@code [General] Map=} currently matches) and unloads both the map and the arena entity.
   */
  private String unloadArena(
      final EntityId id, final EntityId avatarEntityId, final Matcher matcher) {
    final String mapFile = matcher.group(1);
    final String arenaName = findArenaByMap(mapFile);
    if (arenaName == null) {
      return "No arena currently loaded with map " + mapFile;
    }
    if (!getSystem(MapSystem.class).unloadMap(mapFile)) {
      return "Map " + mapFile + " is not loaded";
    }
    final EntityId arena = currentOpenArenas.remove(arenaName);
    if (arena != null) {
      arenaCells.remove(arena);
      ed.removeEntity(arena);
    }
    return "Arena " + arenaName + " (map " + mapFile + ") unloaded";
  }

  /**
   * Handles {@code ~swapMap <arenaName> <newMap>}. Replaces the map of an already-loaded arena at
   * the same tile slot. The arena entity, name, and settings are preserved; only the underlying
   * map cells change. The arena's {@code [General] Map=} key is updated in-memory so later reads
   * reflect the swap (not persisted to disk).
   */
  private String swapArena(
      final EntityId id, final EntityId avatarEntityId, final Matcher matcher) {
    final String arenaName = matcher.group(1);
    final String newMap = matcher.group(2);

    final EntityId arena = currentOpenArenas.get(arenaName);
    if (arena == null) {
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
        ed.getComponent(arena, ArenaId.class), "General", "Map", newMap);

    return "Arena " + arenaName + " map swapped from " + oldMap + " to " + newMap;
  }

  /** Scan open arenas for one whose current {@code [General] Map=} equals {@code mapFile}. */
  private String findArenaByMap(final String mapFile) {
    final SettingsSystem settings = getSystem(SettingsSystem.class);
    for (final String arenaName : currentOpenArenas.keySet()) {
      final String current = settings.getString(arenaName, "General", "Map", arenaName + ".lvl");
      if (mapFile.equals(current)) {
        return arenaName;
      }
    }
    return null;
  }

  public EntityId getEntityId(final Vec3d coord) {
    return index.get(coord);
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
    playerEntities.applyChanges();
    arenaEntities.applyChanges();
  }

  @Override
  public void start() {
    // Auto-generated method stub
  }

  @Override
  public void stop() {
    // Auto-generated method stub
  }

  @Override
  public String[] getActiveArenas() {
    return currentOpenArenas.keySet().toArray(new String[0]);
  }

  @Override
  public String getDefaultArenaId() {
    return CoreGameConstants.DEFAULTARENAID;
  }
}
