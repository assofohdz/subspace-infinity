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

package infinity.client.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import infinity.client.ConnectionState;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client-side read-only view of the server's active-arena dictionary. Subscribes to entities
 * carrying both {@link ArenaId} and {@link ArenaMap}, synced from the server's {@code ArenaSystem}
 * via Zay-ES.
 *
 * <p>Other client states query this via {@link #getArenas()} to answer "which arenas are live and
 * where do they live in world-space?" — without re-reading server-only configuration.
 *
 * <p>This state stops at dictionary maintenance. It does not pick an "active" arena for the
 * player (descoped), nor does it react to arena settings changes (descoped). Consumers that need
 * tileset refresh on arena arrival (e.g. {@link LocalViewState}) inspect the map each tick.
 */
public class ArenaRegistryState extends BaseAppState {

  static final Logger log = LoggerFactory.getLogger(ArenaRegistryState.class);

  /** Immutable snapshot of one arena's server-published identity and bounds. */
  public static final class ArenaSnapshot {
    public final String arenaName;
    public final String mapFile;
    public final Vec3d min;
    public final Vec3d max;
    public final int arenaIndex;

    ArenaSnapshot(
        final String arenaName,
        final String mapFile,
        final Vec3d min,
        final Vec3d max,
        final int arenaIndex) {
      this.arenaName = arenaName;
      this.mapFile = mapFile;
      this.min = min;
      this.max = max;
      this.arenaIndex = arenaIndex;
    }

    @Override
    public String toString() {
      return "Arena{name=" + arenaName + ", slot=" + arenaIndex + ", map=" + mapFile
          + ", min=" + min + ", max=" + max + "}";
    }
  }

  private final ConcurrentHashMap<EntityId, ArenaSnapshot> arenas = new ConcurrentHashMap<>();
  private EntityData ed;
  private EntitySet arenaEntities;

  @Override
  protected void initialize(final Application app) {
    ed = getState(ConnectionState.class).getEntityData();
    arenaEntities = ed.getEntities(ArenaId.class, ArenaMap.class);
  }

  @Override
  protected void cleanup(final Application app) {
    if (arenaEntities != null) {
      arenaEntities.release();
      arenaEntities = null;
    }
    arenas.clear();
  }

  @Override
  protected void onEnable() {
    // No-op.
  }

  @Override
  protected void onDisable() {
    // No-op.
  }

  @Override
  public void update(final float tpf) {
    if (arenaEntities == null) {
      return;
    }
    if (arenaEntities.applyChanges()) {
      for (final Entity e : arenaEntities.getAddedEntities()) {
        indexEntity(e);
      }
      for (final Entity e : arenaEntities.getChangedEntities()) {
        indexEntity(e);
      }
      for (final Entity e : arenaEntities.getRemovedEntities()) {
        arenas.remove(e.getId());
        log.info("Arena entity {} removed from client registry", e.getId());
      }
    }
  }

  private void indexEntity(final Entity e) {
    final ArenaId id = e.get(ArenaId.class);
    final ArenaMap map = e.get(ArenaMap.class);
    if (id == null || map == null) {
      return;
    }
    final ArenaSnapshot prev = arenas.put(
        e.getId(),
        new ArenaSnapshot(
            id.getArena(), map.getMapFile(), map.getMin(), map.getMax(), map.getArenaIndex()));
    if (prev == null) {
      log.info(
          "Arena entity {} registered: name={} slot={} map={}",
          e.getId(),
          id.getArena(),
          map.getArenaIndex(),
          map.getMapFile());
    }
  }

  /** Read-only view of all currently-known active arenas keyed by their server entity id. */
  public Map<EntityId, ArenaSnapshot> getArenas() {
    return Collections.unmodifiableMap(arenas);
  }

  /** Returns the first arena's snapshot, or {@code null} if no arenas are yet known. */
  public ArenaSnapshot getFirstArena() {
    for (final ArenaSnapshot snap : arenas.values()) {
      return snap;
    }
    return null;
  }
}
