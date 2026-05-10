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
 * Spatial-query subsystem extracted from {@link ArenaSystem} (round 25
 * class-CC slice — third and last originally-proposed extraction after
 * {@link ArenaCommandsSystem} and {@link ArenaReloadWatcher}). Owns the
 * {@code (ArenaId + ArenaMap)} EntitySet that backs world↔arena lookups
 * and the per-arena spawn-coord resolution.
 *
 * <p>Not a {@link com.simsilica.sim.AbstractGameSystem} — it's plumbing
 * held as a field on {@link ArenaSystem} so the lifecycle stays a single
 * unit (initialize / terminate / per-tick {@code applyChanges} run from
 * ArenaSystem). Mirrors the {@link ArenaReloadWatcher} precedent.
 *
 * <p>Public-facing accessors stay on {@link ArenaSystem} as thin
 * forwarders (see {@link ArenaSystem#findArenaAt} et al.) so existing
 * callers — {@code GameSessionHostedService}, {@code WarpSystem},
 * {@code AvatarSystem}, {@code ArenaMembershipSystem} — keep their
 * {@code arenaSystem.findArenaAt(...)} call shape. The forwarders move
 * the implementation out of ArenaSystem (slimming its class CC budget)
 * without churning every call site.
 *
 * <p>Thread model: mutated only on the sim thread (matches ArenaSystem's
 * own discipline). EntitySet read without re-applying changes (relies on
 * {@link ArenaSystem#update} having done so this tick); arenas don't
 * move within a tick so a one-tick-stale read is harmless.
 */
final class ArenaSpatialIndex {

  static final Logger log = LoggerFactory.getLogger(ArenaSpatialIndex.class);

  private final ArenaSystem arenaSystem;

  /**
   * EntitySet of all loaded arenas (filter {@code ArenaId + ArenaMap}).
   * Owned here; created in {@link #initialize(EntityData)} and released
   * in {@link #terminate()}. Per-tick {@link EntitySet#applyChanges} is
   * driven from {@link ArenaSystem#update}.
   */
  private EntitySet arenaEntities;
  private EntityData ed;

  ArenaSpatialIndex(final ArenaSystem arenaSystem) {
    this.arenaSystem = arenaSystem;
  }

  /**
   * Acquire the EntitySet. Called from {@link ArenaSystem#initialize}
   * once the EntityData reference is available.
   */
  void initialize(final EntityData ed) {
    this.ed = ed;
    this.arenaEntities = ed.getEntities(ArenaId.class, ArenaMap.class);
  }

  /**
   * Release the EntitySet. Called from {@link ArenaSystem#terminate}.
   * Idempotent — safe to call when {@link #initialize} never ran.
   */
  void terminate() {
    if (arenaEntities != null) {
      arenaEntities.release();
      arenaEntities = null;
    }
  }

  /**
   * Per-tick change drain. Called from {@link ArenaSystem#update} so
   * spatial queries on this tick see the latest arena set without each
   * caller having to remember to apply changes themselves.
   */
  void applyChanges() {
    if (arenaEntities != null) {
      arenaEntities.applyChanges();
    }
  }

  /**
   * Resolve a world-space position to the {@link ArenaId} of the loaded
   * arena whose {@link ArenaMap} bounds contain the point on the
   * gameplay plane (X/Z; Y is ignored since gameplay is flat per
   * {@code InfinityConstants.GAMEPLAY_Y}). Returns {@code null} when the
   * point sits outside every loaded arena — by design ships are allowed
   * to roam in no-arena void space.
   *
   * <p>First match wins — adjacent arenas share only a 2-cell gutter, so
   * any overlap is intentional and either pick is correct.
   */
  @Nullable
  ArenaId findArenaAt(final Vec3d position) {
    final Entity arena = findArenaEntity(position);
    return arena == null ? null : arena.get(ArenaId.class);
  }

  /**
   * Sibling of {@link #findArenaAt} that returns the arena entity's id
   * rather than its {@link ArenaId} component. Used by warp-driven
   * membership reconciliation ({@code ArenaMembershipSystem.markEntered})
   * which needs the entity reference to mirror what a contact-driven
   * enter would have produced.
   */
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

  /**
   * Resolve the world-space spawn coordinate for the named arena and a
   * given player frequency. Single source of truth for both the
   * connect-time spawn ({@code GameSessionHostedService.resolveInitialSpawn}
   * via the {@code zone.groovy enterSpawn} arena pointer) and in-arena
   * ship-change / respawns ({@code AvatarSystem.requestShipChange} with
   * the ship's own {@code ArenaId} and {@link infinity.es.ship.Frequency}).
   *
   * <p>Two-tier lookup:
   * <ol>
   *   <li><b>Typed Pattern 4 first.</b> Read the arena's
   *       {@link SpawnConfig} from {@link ConfigRegistrySystem}. If
   *       {@link SpawnConfig#teams()} is non-empty, look up the team via
   *       {@link SpawnConfig#forFreq(int)} (Subspace canonical wraparound:
   *       {@code freq % teams.size()}). When the team's
   *       {@code radiusTiles > 0}, sample uniformly inside the disc;
   *       {@code radiusTiles == 0} means exact-point spawn.
   *   <li><b>Legacy fallback.</b> No typed {@code spawn.groovy} authored →
   *       fall back to {@code ArenaConfig.spawnX/spawnZ} (the
   *       single-spawn-point directive in {@code arena.groovy}).
   *       Preserves behaviour for arenas not yet migrated to typed spawn
   *       data — most notably the {@code (default)} arena and the
   *       SVS-family presets.
   * </ol>
   *
   * @param arenaName arena registry key (folder name under
   *     {@code zone/arenas/})
   * @param freq player frequency; wraps via
   *     {@link Math#floorMod(int, int)} so any non-negative or negative
   *     value resolves to a valid team index when typed spawn data is
   *     present
   * @return world-space {@link Vec3d} on the gameplay plane, or
   *     {@code null} if the arena isn't loaded (no entity / no
   *     {@code ArenaMap}). Callers fall back as they see fit (typically
   *     world origin).
   */
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

  /**
   * Look up the {@link ArenaMap} component for the named arena, or
   * {@code null} if the arena isn't loaded. Convenience accessor for
   * callers that need the arena's world bounds without walking
   * {@code arenaEntities} themselves.
   */
  @Nullable
  ArenaMap getArenaMap(final String arenaName) {
    final ArenaSystem.ArenaRecord rec = arenaSystem.lookupRecord(arenaName);
    if (rec == null || rec.entityId == null) {
      return null;
    }
    return ed.getComponent(rec.entityId, ArenaMap.class);
  }

  /**
   * Inverse of {@link #arenaToWorld}: project a world-space coordinate
   * to its arena-local equivalent within the named arena. Returns
   * {@code null} if the arena isn't loaded or the world coord is outside
   * the arena's bounds. Used by the client HUD to show "you are at arena
   * (X, Z)" alongside the world coord.
   */
  @Nullable
  Vec3d worldToArena(final String arenaName, final Vec3d world) {
    final ArenaMap map = getArenaMap(arenaName);
    return map == null ? null : ArenaLogic.worldToArenaLocal(map, world);
  }

  /**
   * Convert arena-local {@code (x, z)} to a world-space {@link Vec3d} on
   * the gameplay plane. Arena-local convention: {@code (0, 0) = NW
   * corner}, {@code (TILE_SIZE, TILE_SIZE) = SE corner}. The render flips
   * world {@code (max-X, max-Z)} onto the NW screen corner (camera looks
   * down {@code -Y} with both axes inverted vs jME's default), so we
   * anchor arena coords at {@link ArenaMap#getMax() ArenaMap.max} and
   * decrement.
   */
  static Vec3d arenaToWorld(final ArenaMap map, final double localX, final double localZ) {
    return new Vec3d(
        map.getMax().x - localX,
        InfinityConstants.GAMEPLAY_Y,
        map.getMax().z - localZ);
  }
}
