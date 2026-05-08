// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

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
import java.util.concurrent.ConcurrentMap;
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

  private final ConcurrentMap<EntityId, ArenaSnapshot> arenas = new ConcurrentHashMap<>();
  private EntitySet arenaEntities;

  @Override
  protected void initialize(final Application app) {
    final EntityData ed = getState(ConnectionState.class).getEntityData();
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
        if (log.isInfoEnabled()) {
          log.info("Arena entity {} removed from client registry", e.getId());
        }
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
    if (prev == null && log.isInfoEnabled()) {
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

  /** Returns the snapshot for the named arena, or {@code null} if unknown locally. */
  public ArenaSnapshot byName(final String arenaName) {
    if (arenaName == null) {
      return null;
    }
    for (final ArenaSnapshot snap : arenas.values()) {
      if (arenaName.equals(snap.arenaName)) {
        return snap;
      }
    }
    return null;
  }
}
