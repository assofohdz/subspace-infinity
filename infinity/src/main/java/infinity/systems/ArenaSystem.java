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
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.GridCell;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.InfinityConstants;
import infinity.es.Ghost;
import infinity.es.ShapeNames;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.es.arena.ArenaSettings;
import infinity.es.ship.Player;
import infinity.server.chat.ChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.ArenaManager;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandConsumer;
import infinity.sim.CoreGameConstants;
import infinity.sim.GameEntities;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
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
  private final Pattern loadArena = Pattern.compile("\\~loadArena\\s(\\w+.(?:lvl|lvz))");
  private final Pattern unloadArena = Pattern.compile("\\~unloadArena\\s(\\w+.(?:lvl|lvz))");
  private final HashMap<EntityId, GridCell> arenaCells = new HashMap<>();
  private EntityData ed;
  private EntitySet arenaEntities;
  private EntitySet playerEntities;
  private ChatHostedPoster chat;
  private double timeSinceLastSettingsUpdateMs = 0;
  private long time;

  @Override
  protected void initialize() {

    this.chat = getSystem(ChatHostedService.class);

    ed = getSystem(EntityData.class);
    // This filters all entities that are in arenas
    arenaEntities = ed.getEntities(ArenaId.class);
    // This filters all entities that are players
    playerEntities = ed.getEntities(Player.class, BodyPosition.class);

    // Register consuming methods for patterns
    chat.registerPatternBiConsumer(
        loadArena,
        "The command to load a new map is ~loadArena <mapName>, where <mapName> is the "
            + "name of the map you want to load",
        new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, map) -> loadArena(id, map)));
    chat.registerPatternBiConsumer(
        unloadArena,
        "The command to unload a new map is ~unloadArena <mapName>, where <mapName> is the "
            + "name of the map you want to unload",
        new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, map) -> unloadArena(id, map)));

    arenaEntities = ed.getEntities(ArenaId.class); // This filters all arena entities
  }

  /**
   * This method will load up a new Arena entity and attach the right components, then call
   * MapSystem and load the map, then call SettingsSystem and load the settings for this arena.
   *
   * @param requester EntityId of the player that sent the command
   * @param map String of the map name
   */
  private void loadArena(final EntityId requester, final String map) {
    // First create the map entity
    EntityId arena = ed.createEntity();
    ed.setComponent(arena, new ArenaId(map, requester));

    // Then load the map
    getSystem(MapSystem.class).loadMap(requester, map);
    Vec3d mapBoundsMax = getSystem(MapSystem.class).getMapBoundsMax(map);
    Vec3d mapBoundsMin = getSystem(MapSystem.class).getMapBoundsMin(map);
    // Add mapbounds information to the arena entity
    ed.setComponent(arena, new ArenaMap(mapBoundsMax, mapBoundsMin));

    // Then load the settings (remove file ending first)
    getSystem(SettingsSystem.class).loadSettings(requester, map.substring(0, map.lastIndexOf('.')));
    Ini ini = getSystem(SettingsSystem.class).getIni(map);
    // Add settings information to the arena entity
    ed.setComponents(arena, new ArenaSettings(map, ini));

    // Get the containing cell for this arena add it to our arenaindex
    GridCell cell =
        InfinityConstants.LARGE_OBJECT_GRID.getContainingCell(
            mapBoundsMax.subtract(mapBoundsMin).divide(2));
    arenaCells.put(arena, cell);

    Vec3d arenaMid = mapBoundsMax.add(mapBoundsMin).divide(2);

    // Add this arena as a shape we can use to detect players entering/leaving the arena
    ed.setComponent(arena, new Ghost());
    ed.setComponent(arena, new Mass(0));
    ed.setComponent(arena, new SpawnPosition(InfinityConstants.PHYSICS_GRID, new Vec3d()));
    ed.setComponent(arena, ShapeInfo.create(ShapeNames.ARENA, 1, ed));
  }

  /**
   * This method will unload an arena and remove the related arena entity. It will then call the
   * MapSystem.java to unload the map and the SettingsSystem.java to unload the settings
   *
   * @param id EntityId of the player that sent the command
   * @param map String of the map name
   */
  private void unloadArena(final EntityId id, final String map) {}

  public EntityId getEntityId(final Vec3d coord) {
    return index.get(coord);
  }

  @Override
  protected void terminate() {
    // Release the entity set we grabbed previously
    arenaEntities.release();
    arenaEntities = null;
  }

  @Override
  public void update(final SimTime tpf) {
    this.time = tpf.getTime();
    
    playerEntities.applyChanges();
    arenaEntities.applyChanges();

    /*
     * This is a bit of a hack, but it works. We want to update the settings every 5 seconds, but
     * we don't want to do it every frame. So we keep track of the time since last update and if
     * it's more than 5 seconds we update the settings.
     */
    /*
    if (timeSinceLastSettingsUpdateMs > CoreGameConstants.UPDATE_SETTINGS_INTERVAL_MS) {
      // Update the filter and search for ships we need to update
      for (Entity arena : arenaEntities) {
        GridCell cell = arenaCells.get(arena.getId());
        log.info("Checking players in: " + arena.get(ArenaId.class).getArenaBaseName());

        for (Entity player : playerEntities) {
          if (cell.contains(player.get(BodyPosition.class).getLastLocation())) {
            // Update the settings for this player
            // getSystem(SettingsSystem.class).updateSettings(player.getId(),
            // arena.get(ArenaId.class).getArenaBaseName());
          }
        }

        // arenaFilters.add(filter);
      }
      // reset our timer
      timeSinceLastSettingsUpdateMs = 0;
    } else {
      // Add tpf in ms to our time since last update
      timeSinceLastSettingsUpdateMs += tpf.getTpf()*1000;
    }
    */
  }

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Override
  public String[] getActiveArenas() {
    return (String[]) currentOpenArenas.keySet().toArray();
  }

  private void closeArena(final String arenaId) {
    ed.removeEntity(currentOpenArenas.get(arenaId));
    currentOpenArenas.remove(arenaId);
  }

  @Override
  public String getDefaultArenaId() {
    return CoreGameConstants.DEFAULTARENAID;
  }
}
