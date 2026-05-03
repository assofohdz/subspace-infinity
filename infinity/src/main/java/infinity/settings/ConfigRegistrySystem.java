// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import com.simsilica.sim.AbstractGameSystem;
import infinity.config.ArenaConfig;
import infinity.es.arena.ArenaId;
import infinity.systems.SettingsSystem;
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
 *
 * <p><b>Load orchestration</b> ({@link #load}) is the single entry point for
 * building an arena's snapshot from its {@link ArenaConfig}: ships via the
 * typed {@link GroovyShipLoader}, weapons + prize via the still-INI-routed
 * {@link GroovyWeaponsLoader} compat shim. Same method serves initial load
 * and hot-reload. The compat shim disappears in slice B4 once every
 * fragment section has its own typed adapter (B1–B3).
 */
public class ConfigRegistrySystem extends AbstractGameSystem {

  private static final Logger log = LoggerFactory.getLogger(ConfigRegistrySystem.class);

  private final ConcurrentMap<String, ConfigRegistry> byArena = new ConcurrentHashMap<>();

  private SettingsSystem settings;
  private GroovyShipLoader shipLoader;
  private GroovyWeaponsLoader weaponsLoader;

  @Override
  protected void initialize() {
    // Collaborators are registered after this system in GameServer; pull them
    // here once the server has wired everything up.
    settings = getSystem(SettingsSystem.class);
    shipLoader = getSystem(GroovyShipLoader.class);
    weaponsLoader = getSystem(GroovyWeaponsLoader.class);
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

  /**
   * Load and install the per-arena {@link ConfigRegistry} snapshot from its
   * {@link ArenaConfig}. Single entry point for both initial arena load and
   * hot-reload (the file watcher in {@code ArenaSystem} calls this on every
   * watched-file change). Atomic full-replace per call — readers see the
   * old or the new snapshot, never a torn state.
   *
   * <p>Three-phase orchestration:
   *
   * <ol>
   *   <li><b>Fragment Ini load</b> — {@link SettingsSystem#loadFragments}
   *       reads each {@link ArenaConfig#fragmentIncludes()} path and merges
   *       the {@code Ini} store under {@code arenaName}. Legacy compat path;
   *       slice B4 deletes once every fragment section has its own typed
   *       adapter.
   *   <li><b>Ships</b> — {@link GroovyShipLoader#apply} parses the typed
   *       {@code ships.groovy} (referenced via {@link ArenaConfig#shipsScript()})
   *       and installs a ship-only snapshot via {@link #replace}.
   *   <li><b>Weapons + prize compat</b> — {@link GroovyWeaponsLoader}'s
   *       per-section {@code loadBullet} / {@code loadBomb} / {@code loadMine}
   *       / {@code loadBurst} / {@code loadRepel} / {@code loadPrize} methods
   *       derive each typed sub-record from the merged {@code Ini}; the
   *       results are layered onto the ship snapshot via the per-slot
   *       {@code ConfigRegistry.with*} updaters.
   * </ol>
   *
   * <p>The dispatch table for typed per-fragment adapters
   * (see {@code .scratch/settings-pipeline-slices.md} slice B0 design) is
   * deferred to slice B1 — until per-section typed adapters exist
   * ({@code BulletAdapter}, {@code BombAdapter}, …) the table would have only
   * one effective entry ({@code ships.groovy}, handled separately because it
   * lives outside {@code fragmentIncludes()}). Adding it now is premature
   * abstraction; B1 introduces it alongside its first per-fragment adapter.
   */
  public void load(final ArenaId arenaId, final ArenaConfig arenaConfig) {
    Objects.requireNonNull(arenaId, "arenaId");
    Objects.requireNonNull(arenaConfig, "arenaConfig");
    final String arenaName = arenaId.getArena();

    // Phase 1: fragment Ini load (legacy compat; B4 deletes)
    settings.loadFragments(arenaName, arenaConfig.fragmentIncludes());

    // Phase 2: ships via typed loader (installs ship-only snapshot)
    final String shipsScript =
        arenaConfig.shipsScript().isBlank() ? null : arenaConfig.shipsScript();
    shipLoader.apply(arenaId, shipsScript);

    // Phase 3: weapons + prize compat shim (B1b narrows per-section as typed
    // adapters land; B4 deletes once every section has its own adapter).
    // GravBomb and Thor have no Subspace fragment section today (gravbombs
    // share [Bomb] tuning in VIE; Thors are an Infinity addition without a
    // canonical section), so they keep their *Config.DEFAULTS until either
    // gets its own typed slot.
    final ConfigRegistry current = forArena(arenaId);
    replace(
        arenaId,
        current
            .withBullet(weaponsLoader.loadBullet(settings, arenaName))
            .withBomb(weaponsLoader.loadBomb(settings, arenaName))
            .withMine(weaponsLoader.loadMine(settings, arenaName))
            .withBurst(weaponsLoader.loadBurst(settings, arenaName))
            .withRepel(weaponsLoader.loadRepel(settings, arenaName))
            .withPrize(weaponsLoader.loadPrize(settings, arenaName)));
  }
}
