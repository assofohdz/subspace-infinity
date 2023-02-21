/*
 * Copyright (c) 2018, Asser Fahrenholz
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

  @Override
  protected void initialize() {

    ChatHostedPoster chat = getSystem(InfinityChatHostedService.class);

    ed = getSystem(EntityData.class);
    // This filters all entities that are in arenas
    arenaEntities = ed.getEntities(ArenaId.class);
    // This filters all entities that are players
    playerEntities = ed.getEntities(Player.class, BodyPosition.class);

    arenaEntities = ed.getEntities(ArenaId.class); // This filters all arena entities

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
  }

  /**
   * This method will load up a new Arena entity and attach the right components, then call
   * MapSystem and load the map, then call SettingsSystem and load the settings for this arena.
   *
   * @param playerEntityId EntityId of the player that sent the command
   * @param matcher Matcher that contains the map name
   */
  private String loadArena(final EntityId playerEntityId, EntityId avatarEntityId, final Matcher matcher) {
    String map = matcher.group(1);
    // First create the map entity
    EntityId arena = ed.createEntity();
    ed.setComponent(arena, new ArenaId(map, playerEntityId));

    // Then load the map
    getSystem(MapSystem.class).loadMap(playerEntityId, avatarEntityId, map);
    Vec3d mapBoundsMax = getSystem(MapSystem.class).getMapBoundsMax(map);
    Vec3d mapBoundsMin = getSystem(MapSystem.class).getMapBoundsMin(map);
    // Add mapbounds information to the arena entity
    ed.setComponent(arena, new ArenaMap(mapBoundsMax, mapBoundsMin));

    // Then load the settings (remove file ending first)
    getSystem(SettingsSystem.class)
        .loadSettings(playerEntityId, map.substring(0, map.lastIndexOf('.')));
    Ini ini = getSystem(SettingsSystem.class).getIni(map);
    // Add settings information to the arena entity
    ed.setComponents(arena, new ArenaSettings(map, ini));

    // Get the containing cell for this arena add it to our arenaindex
    GridCell cell =
        WorldGrids.TILE_GRID.getContainingCell(
            mapBoundsMax.subtract(mapBoundsMin).divide(2));
    arenaCells.put(arena, cell);

    // Add this arena as a shape we can use to detect players entering/leaving the arena
    ed.setComponent(arena, new Mass(0));
    ed.setComponent(arena, new SpawnPosition(WorldGrids.LEAF_GRID, new Vec3d()));
    ed.setComponent(arena, ShapeInfo.create(ShapeNames.ARENA, 1, ed));

    return "Map " + map + " loaded";
  }

  /**
   * This method will unload an arena and remove the related arena entity. It will then call the
   * MapSystem.java to unload the map and the SettingsSystem.java to unload the settings
   *
   * @param id EntityId of the player that sent the command
   * @param matcher Matcher that contains the map name
   */
  private String unloadArena(final EntityId id, EntityId avatarEntityId, Matcher matcher) {
    String map = matcher.group(1);
    // First unload the map
    getSystem(MapSystem.class).unloadMap(id, avatarEntityId, matcher);
    // TODO: unload settings

    // Then remove the arena entity
    ed.removeEntity(currentOpenArenas.get(map));

    return "Map " + map + " unloaded";
  }

  public EntityId getEntityId(final Vec3d coord) {
    return index.get(coord);
  }

  @Override
  protected void terminate() {
    // TODO Auto-generated method stub
  }

  @Override
  public void update(final SimTime tpf) {
    playerEntities.applyChanges();
    arenaEntities.applyChanges();
  }

  @Override
  public void start() {
    // Grab the entity set we need
    arenaEntities = ed.getEntities(ArenaId.class);
    // This filters all entities that are players
    playerEntities = ed.getEntities(Player.class, BodyPosition.class);
  }

  @Override
  public void stop() {
    // Release the entity set we grabbed previously
    arenaEntities.release();
    arenaEntities = null;
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
