// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import com.simsilica.sim.AbstractGameSystem;
import infinity.es.arena.ArenaId;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds one {@link ConfigRegistry} snapshot per arena. Owned by the game
 * server; populated by the config layer (Groovy loader, admin reload) and
 * queried by spawn systems.
 *
 * <p>Snapshots are installed by atomic reference swap via {@link #replace} —
 * readers never see a half-updated state. Each swap replaces the entire
 * snapshot for the arena; there are no partial updates.
 */
public class ConfigRegistrySystem extends AbstractGameSystem {

  private static final Logger log = LoggerFactory.getLogger(ConfigRegistrySystem.class);

  private final ConcurrentMap<String, ConfigRegistry> byArena = new ConcurrentHashMap<>();

  @Override
  protected void initialize() {
    // No startup work — registries are populated as arenas load.
  }

  @Override
  protected void terminate() {
    byArena.clear();
  }

  /**
   * Return the current config snapshot for {@code arenaId}. Returns
   * {@link ConfigRegistry#EMPTY} if nothing has populated this arena yet —
   * callers should treat missing entries within the snapshot as
   * "use built-in defaults" rather than "arena is misconfigured".
   */
  public ConfigRegistry forArena(final ArenaId arenaId) {
    Objects.requireNonNull(arenaId, "arenaId");
    return byArena.getOrDefault(arenaId.getArena(), ConfigRegistry.EMPTY);
  }

  /**
   * Atomically replace the snapshot for {@code arenaId}. Concurrent readers
   * see either the old snapshot or the new one, never a mix. Idempotent if
   * the same snapshot reference is installed twice.
   */
  public void replace(final ArenaId arenaId, final ConfigRegistry snapshot) {
    Objects.requireNonNull(arenaId, "arenaId");
    Objects.requireNonNull(snapshot, "snapshot");
    final ConfigRegistry previous = byArena.put(arenaId.getArena(), snapshot);
    if (log.isDebugEnabled()) {
      log.debug(
          "Installed config snapshot for arena {} (ships: {}, previous: {})",
          arenaId.getArena(),
          snapshot.configuredShips(),
          previous == null ? "none" : "replaced");
    }
  }

  /** Drop the snapshot for {@code arenaId} (e.g. on arena unload). */
  public void remove(final ArenaId arenaId) {
    Objects.requireNonNull(arenaId, "arenaId");
    byArena.remove(arenaId.getArena());
  }
}
