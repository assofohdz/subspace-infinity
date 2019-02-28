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
package infinity.es.states.arena;

import com.jme3.asset.AssetManager;
import com.jme3.system.JmeSystem;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ethereal.zone.ZoneKey;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.ServerGameConstants;
import infinity.TimeState;
import infinity.api.es.Position;
import infinity.api.es.subspace.ArenaId;
import infinity.api.sim.ArenaManager;
import infinity.api.sim.CoreGameConstants;
import infinity.api.sim.ModuleGameEntities;
import infinity.map.LevelLoader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import org.dyn4j.geometry.Vector2;
import tiled.core.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * State to keep track of different arenas. Arenas are composed of a tileset and
 * a ruleset. This state keeps track of where the next arena can be loaded
 *
 * @author Asser
 */
public class ArenaState extends AbstractGameSystem implements ArenaManager {

    private Map map;
    private EntityData ed;
    private EntitySet arenaEntities;
    private EntitySet staticPositions;
    private java.util.Map<Vector2, EntityId> index = new ConcurrentHashMap<>();
    private AssetManager am;
    static Logger log = LoggerFactory.getLogger(ArenaState.class);
    private SimTime time;

    private HashMap<String, EntityId> currentOpenArenas = new HashMap<>();

    private HashMap<ZoneKey, Long> zones = new HashMap<>();

    private boolean createdDefaultArena = false;

    @Override
    protected void initialize() {

        this.ed = getSystem(EntityData.class);

        arenaEntities = ed.getEntities(ArenaId.class); //This filters all entities that are in arenas

        AssetManager am = JmeSystem.newAssetManager(Thread.currentThread().getContextClassLoader().getResource("com/jme3/asset/Desktop.cfg"));

        am.registerLoader(LevelLoader.class, "lvl");

        staticPositions = ed.getEntities(Position.class);

    }

    public EntityId getEntityId(Vector2 coord) {
        return index.get(coord);
    }

    @Override
    protected void terminate() {
        // Release the entity set we grabbed previously
        arenaEntities.release();
        arenaEntities = null;
    }

    @Override
    public void update(SimTime tpf) {
        time = tpf;
        arenaEntities.applyChanges();

        if (!createdDefaultArena) {
            this.openArena(getDefaultArenaId());
            createdDefaultArena = true;
        }

        if (staticPositions.applyChanges()) {
            for (Entity e : staticPositions.getAddedEntities()) {
                Position pos = e.get(Position.class);

                ZoneKey zone = ServerGameConstants.ZONE_GRID.worldToKey(pos.getLocation());
                long cellId = zone.toLongId();

                zones.put(zone, cellId);

                Position newPos = pos.newCellId(cellId);
                ed.setComponent(e.getId(), newPos);
            }

            log.info("Zones: " + zones.toString());
        }

    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public String[] getActiveArenas() {
        return (String[]) currentOpenArenas.keySet().toArray();
    }

    private void openArena(String arenaId) {
        ModuleGameEntities.createArena(ed, arenaId, new Vec3d(0, 0, 0), time.getTime());
    }

    private void closeArena(String arenaId) {
        ed.removeEntity(currentOpenArenas.get(arenaId));

        currentOpenArenas.remove(arenaId);
    }

    @Override
    public String getDefaultArenaId() {
        return CoreGameConstants.DEFAULTARENAID;
    }
}
