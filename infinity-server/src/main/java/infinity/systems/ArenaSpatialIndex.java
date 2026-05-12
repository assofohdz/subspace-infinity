// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import infinity.InfinityConstants;
import infinity.config.SpawnConfig;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.settings.ConfigRegistrySystem;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spatial-query plumbing for {@link ArenaSystem} — owns the
 * {@code (ArenaId + ArenaMap)} EntitySet + per-arena spawn resolution.
 * Mutated only on the sim thread (matches ArenaSystem); reads rely on
 * {@link ArenaSystem#update} having applied changes this tick. Arenas
 * don't move within a tick, so a one-tick-stale read is harmless.
 */
final class ArenaSpatialIndex {

  static final Logger log = LoggerFactory.getLogger(ArenaSpatialIndex.class);

  private final ArenaSystem arenaSystem;

  private EntitySet arenaEntities;
  private EntityData ed;

  ArenaSpatialIndex(final ArenaSystem arenaSystem) {
    this.arenaSystem = arenaSystem;
  }

  void initialize(final EntityData ed) {
    this.ed = ed;
    this.arenaEntities = ed.getEntities(ArenaId.class, ArenaMap.class);
  }

  void terminate() {
    if (arenaEntities != null) {
      arenaEntities.release();
      arenaEntities = null;
    }
  }

  void applyChanges() {
    if (arenaEntities != null) {
      arenaEntities.applyChanges();
    }
  }

  /** X/Z point-in-bounds; first match wins (adjacent arenas share a 2-cell gutter). {@code null} = no-arena void. */
  @Nullable
  ArenaId findArenaAt(final Vec3d position) {
    final Entity arena = findArenaEntity(position);
    return arena == null ? null : arena.get(ArenaId.class);
  }

  /** Sibling of {@link #findArenaAt} returning the arena entity id (for {@code ArenaMembershipSystem.markEntered}). */
  @Nullable
  EntityId findArenaEntityAt(final Vec3d position) {
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

  /** Two-tier: typed {@link SpawnConfig} (team disc by freq wraparound) → legacy {@code ArenaConfig.spawnX/spawnZ}. */
  @Nullable
  Vec3d getArenaSpawn(final String arenaName, final int freq) {
    final ArenaSystem.ArenaRecord rec = arenaSystem.lookupRecord(arenaName);
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
        arenaSystem.getConfigRegistry().forArena(new ArenaId(arenaName, rec.entityId)).spawn();
    return ArenaLogic.resolveArenaSpawn(
        spawn, freq, map, rec.config.spawnX(), rec.config.spawnZ());
  }

  @Nullable
  ArenaMap getArenaMap(final String arenaName) {
    final ArenaSystem.ArenaRecord rec = arenaSystem.lookupRecord(arenaName);
    if (rec == null || rec.entityId == null) {
      return null;
    }
    return ed.getComponent(rec.entityId, ArenaMap.class);
  }

  /** Inverse of {@link #arenaToWorld}; {@code null} if outside the arena's bounds. */
  @Nullable
  Vec3d worldToArena(final String arenaName, final Vec3d world) {
    final ArenaMap map = getArenaMap(arenaName);
    return map == null ? null : ArenaLogic.worldToArenaLocal(map, world);
  }

  /** Arena-local {@code (0,0)=NW, (TILE_SIZE,TILE_SIZE)=SE}; render flips world max-X/Z onto the NW screen corner, so anchor at {@link ArenaMap#getMax()}. */
  static Vec3d arenaToWorld(final ArenaMap map, final double localX, final double localZ) {
    return new Vec3d(
        map.getMax().x - localX,
        InfinityConstants.GAMEPLAY_Y,
        map.getMax().z - localZ);
  }
}
