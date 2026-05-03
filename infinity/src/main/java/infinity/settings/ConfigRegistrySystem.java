// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import com.simsilica.sim.AbstractGameSystem;
import infinity.config.ArenaConfig;
import infinity.config.BombConfig;
import infinity.config.BulletConfig;
import infinity.config.BurstFireConfig;
import infinity.config.MineConfig;
import infinity.config.PrizeConfig;
import infinity.config.PrizeWeightsConfig;
import infinity.config.RepelConfig;
import infinity.es.arena.ArenaId;
import infinity.systems.SettingsSystem;
import java.util.Map;
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

  /**
   * Centralized dispatch for typed per-fragment adapters. Key = fragment
   * basename (e.g. {@code "bullet.groovy"}); value = installer that loads
   * the file via {@link GroovySettingsHost} and writes the parsed record
   * into the {@link ConfigRegistry} under construction.
   *
   * <p>Each B1-X vertical slice ({@code .scratch/settings-pipeline-slices.md})
   * adds one entry here and deletes the corresponding section's legacy
   * {@code GroovyWeaponsLoader.load*} call from {@link #load}'s Phase 3
   * compat shim. When the shim is empty the loader itself disappears (B4).
   */
  private static final Map<String, FragmentInstaller> DISPATCH =
      Map.of(
          "bullet.groovy",
          (current, path) -> {
            final BulletConfig parsed =
                GroovySettingsHost.INSTANCE.load(BulletAdapter.INSTANCE, path);
            return current.withBullet(parsed != null ? parsed : BulletConfig.DEFAULTS);
          },
          "bomb.groovy",
          (current, path) -> {
            final BombConfig parsed =
                GroovySettingsHost.INSTANCE.load(BombAdapter.INSTANCE, path);
            return current.withBomb(parsed != null ? parsed : BombConfig.DEFAULTS);
          },
          "mine.groovy",
          (current, path) -> {
            final MineConfig parsed =
                GroovySettingsHost.INSTANCE.load(MineAdapter.INSTANCE, path);
            return current.withMine(parsed != null ? parsed : MineConfig.DEFAULTS);
          },
          "burst.groovy",
          (current, path) -> {
            final BurstFireConfig parsed =
                GroovySettingsHost.INSTANCE.load(BurstAdapter.INSTANCE, path);
            return current.withBurst(parsed != null ? parsed : BurstFireConfig.DEFAULTS);
          },
          "repel.groovy",
          (current, path) -> {
            final RepelConfig parsed =
                GroovySettingsHost.INSTANCE.load(RepelAdapter.INSTANCE, path);
            return current.withRepel(parsed != null ? parsed : RepelConfig.DEFAULTS);
          },
          "prize.groovy",
          (current, path) -> {
            final PrizeConfig parsed =
                GroovySettingsHost.INSTANCE.load(PrizeAdapter.INSTANCE, path);
            return current.withPrize(parsed != null ? parsed : PrizeConfig.DEFAULTS);
          },
          "prize-weights.groovy",
          (current, path) -> {
            final PrizeWeightsConfig parsed =
                GroovySettingsHost.INSTANCE.load(PrizeWeightsAdapter.INSTANCE, path);
            return current.withPrizeWeights(
                parsed != null ? parsed : PrizeWeightsConfig.DEFAULTS);
          });

  /** Functional contract for a typed fragment installer. */
  @FunctionalInterface
  private interface FragmentInstaller {
    ConfigRegistry install(ConfigRegistry current, String classpathPath);
  }

  @Override
  protected void initialize() {
    // Collaborators are registered after this system in GameServer; pull them
    // here once the server has wired everything up.
    settings = getSystem(SettingsSystem.class);
    shipLoader = getSystem(GroovyShipLoader.class);
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

    // Phase 1: fragment Ini load (legacy compat; B4 deletes). Skips fragments
    // that have a typed adapter — those are handled in Phase 3b's dispatch
    // and would fail INI-mirror parse since their content uses typed-DSL
    // blocks (e.g. `bullet { damageLevel 200 }`) instead of `section('Bullet')`.
    final java.util.List<String> iniFragments = new java.util.ArrayList<>();
    for (final String path : arenaConfig.fragmentIncludes()) {
      if (path != null && !DISPATCH.containsKey(basenameOf(path))) {
        iniFragments.add(path);
      }
    }
    settings.loadFragments(arenaName, iniFragments);

    // Phase 2: ships via typed loader (installs ship-only snapshot)
    final String shipsScript =
        arenaConfig.shipsScript().isBlank() ? null : arenaConfig.shipsScript();
    shipLoader.apply(arenaId, shipsScript);

    // Phase 3a: legacy compat shim — pulls per-section records from the merged
    // Ini for sections that don't yet have a typed adapter. Each B1-X vertical
    // slice removes the corresponding withX call from this chain. GravBomb +
    // Thor have no Subspace fragment section (gravbombs share [Bomb] tuning
    // in VIE; Thors are an Infinity addition), so they stay on DEFAULTS via
    // ConfigRegistry.Builder's defaults until either gets its own typed slot.
    // Phase 3a: legacy compat shim is now empty — every weapon-projectile +
    // prize section migrated to its own typed adapter (B1-Bullet through
    // B1-Prize). The starting snapshot for typed dispatch is just the current
    // ConfigRegistry. GroovyWeaponsLoader is fully unused — B4 deletes it.
    ConfigRegistry current = forArena(arenaId);

    // Phase 3b: typed adapters via dispatch table. Runs AFTER the compat shim
    // so typed values overwrite legacy defaults during transitional states.
    for (final String path : arenaConfig.fragmentIncludes()) {
      if (path == null) continue;
      final FragmentInstaller installer = DISPATCH.get(basenameOf(path));
      if (installer != null) {
        current = installer.install(current, path);
      }
    }

    replace(arenaId, current);
  }

  private static String basenameOf(final String path) {
    final int slash = path.lastIndexOf('/');
    return slash >= 0 ? path.substring(slash + 1) : path;
  }
}
