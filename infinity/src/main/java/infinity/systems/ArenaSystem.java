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

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.es.arena.ArenaSettings;
import infinity.server.chat.ChatHostedService;
import infinity.sim.AccessLevel;
import infinity.sim.ArenaManager;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandConsumer;
import infinity.sim.CoreGameConstants;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.apache.commons.collections4.map.ListOrderedMap;
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
  private final ListOrderedMap<String, String> arenas = new ListOrderedMap<>();
  private final Pattern loadArena = Pattern.compile("\\~loadArena\\s(\\w+.(?:lvl|lvz))");
  private final Pattern unloadArena = Pattern.compile("\\~unloadArena\\s(\\w+.(?:lvl|lvz))");
  private EntityData ed;
  private EntitySet arenaEntities;
  private ChatHostedPoster chat;

  @Override
  protected void initialize() {

    this.chat = getSystem(ChatHostedService.class);

    ed = getSystem(EntityData.class);

    arenaEntities = ed.getEntities(ArenaId.class); // This filters all entities that are in arenas

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
  public void update(final SimTime tpf) {}

  @Override
  public void start() {}

  @Override
  public void stop() {}

  @Override
  public String[] getActiveArenas() {
    return (String[]) currentOpenArenas.keySet().toArray();
  }

  @SuppressWarnings("unused")
  private void closeArena(final String arenaId) {
    ed.removeEntity(currentOpenArenas.get(arenaId));
    currentOpenArenas.remove(arenaId);
  }

  @Override
  public String getDefaultArenaId() {
    return CoreGameConstants.DEFAULTARENAID;
  }
}
