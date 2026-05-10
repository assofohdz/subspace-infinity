// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import com.simsilica.sim.AbstractGameSystem;
import infinity.config.ArenaConfig;
import infinity.config.BombConfig;
import infinity.config.BrickConfig;
import infinity.config.BulletConfig;
import infinity.config.BurstFireConfig;
import infinity.config.DecoyConfig;
import infinity.config.MineConfig;
import infinity.config.PortalConfig;
import infinity.config.PrizeConfig;
import infinity.config.PrizeWeightsConfig;
import infinity.config.RepelConfig;
import infinity.config.RocketConfig;
import infinity.config.SpawnConfig;
import infinity.es.arena.ArenaId;
import infinity.systems.SettingsSystem;
import java.util.LinkedHashMap;
import java.util.List;
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
   * Centralized binding from fragment basename → typed adapter +
   * {@link ConfigRegistry} slot. One entry per typed per-fragment loader;
   * adding a new {@code *Adapter} costs one line here (plus the slot in
   * {@link ConfigRegistry#SLOTS}).
   *
   * <p>Each B1-X vertical slice ({@code .scratch/settings-pipeline-slices.md})
   * adds one entry here and deletes the corresponding section's legacy
   * {@code GroovyWeaponsLoader.load*} call from {@link #load}'s Phase 3
   * compat shim. When the shim is empty the loader itself disappears (B4).
   */
  private static final List<FragmentBinding<?>> FRAGMENT_BINDINGS =
      List.of(
          FragmentBinding.of("bullet.groovy", BulletConfig.class, BulletAdapter.INSTANCE),
          FragmentBinding.of("bomb.groovy", BombConfig.class, BombAdapter.INSTANCE),
          FragmentBinding.of("mine.groovy", MineConfig.class, MineAdapter.INSTANCE),
          FragmentBinding.of("burst.groovy", BurstFireConfig.class, BurstAdapter.INSTANCE),
          FragmentBinding.of("repel.groovy", RepelConfig.class, RepelAdapter.INSTANCE),
          FragmentBinding.of("rocket.groovy", RocketConfig.class, RocketAdapter.INSTANCE),
          FragmentBinding.of("brick.groovy", BrickConfig.class, BrickAdapter.INSTANCE),
          FragmentBinding.of("decoy.groovy", DecoyConfig.class, DecoyAdapter.INSTANCE),
          FragmentBinding.of("portal.groovy", PortalConfig.class, PortalAdapter.INSTANCE),
          FragmentBinding.of("prize.groovy", PrizeConfig.class, PrizeAdapter.INSTANCE),
          FragmentBinding.of(
              "prize-weights.groovy",
              PrizeWeightsConfig.class,
              PrizeWeightsAdapter.INSTANCE),
          FragmentBinding.of("spawn.groovy", SpawnConfig.class, SpawnAdapter.INSTANCE));

  /** Indexed view of {@link #FRAGMENT_BINDINGS} for O(1) basename lookup. */
  private static final Map<String, FragmentBinding<?>> BY_BASENAME = indexByBasename();

  private static Map<String, FragmentBinding<?>> indexByBasename() {
    final Map<String, FragmentBinding<?>> map = new LinkedHashMap<>();
    for (final FragmentBinding<?> binding : FRAGMENT_BINDINGS) {
      map.put(binding.basename(), binding);
    }
    return Map.copyOf(map);
  }

  /**
   * Per-fragment binding — pairs a fragment basename (e.g. {@code "bullet.groovy"})
   * with the {@code *Config} slot type and the typed adapter that produces
   * it. Generic {@link #install} threads through the typed value without
   * unchecked casts in the call site.
   */
  private static final class FragmentBinding<T> {
    private final String basename;
    private final Class<T> configType;
    private final GroovySettingsAdapter<T, ?> adapter;

    private FragmentBinding(
        final String basename,
        final Class<T> configType,
        final GroovySettingsAdapter<T, ?> adapter) {
      this.basename = basename;
      this.configType = configType;
      this.adapter = adapter;
    }

    static <T> FragmentBinding<T> of(
        final String basename,
        final Class<T> configType,
        final GroovySettingsAdapter<T, ?> adapter) {
      return new FragmentBinding<>(basename, configType, adapter);
    }

    String basename() {
      return basename;
    }

    /**
     * Load the fragment at {@code classpathPath} via the bound adapter and
     * install the parsed value (or the adapter's {@code empty()} sentinel
     * if the host returned {@code null}) into {@code current}'s slot.
     */
    ConfigRegistry install(final ConfigRegistry current, final String classpathPath) {
      final T parsed = GroovySettingsHost.INSTANCE.load(adapter, classpathPath);
      final T value = parsed != null ? parsed : adapter.empty();
      return current.with(configType, value);
    }
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
   *   <li><b>Typed fragments</b> — {@link #FRAGMENT_BINDINGS} drives
   *       per-section parsing. Each entry knows its slot key + adapter, so
   *       the orchestration loop is one line.
   * </ol>
   */
  public void load(final ArenaId arenaId, final ArenaConfig arenaConfig) {
    Objects.requireNonNull(arenaId, "arenaId");
    Objects.requireNonNull(arenaConfig, "arenaConfig");
    final String arenaName = arenaId.getArena();

    // Phase 1: fragment Ini load (legacy compat; B4 deletes). Skips fragments
    // that have a typed adapter — those are handled in Phase 3 and would
    // fail INI-mirror parse since their content uses typed-DSL blocks
    // (e.g. `bullet { damageLevel 200 }`) instead of `section('Bullet')`.
    final java.util.List<String> iniFragments = new java.util.ArrayList<>();
    for (final String path : arenaConfig.fragmentIncludes()) {
      if (path != null && !BY_BASENAME.containsKey(basenameOf(path))) {
        iniFragments.add(path);
      }
    }
    settings.loadFragments(arenaName, iniFragments);

    // Phase 2: ships via typed loader (installs ship-only snapshot)
    final String shipsScript =
        arenaConfig.shipsScript().isBlank() ? null : arenaConfig.shipsScript();
    shipLoader.apply(arenaId, shipsScript);

    // Phase 3: typed adapters via fragment binding table.
    ConfigRegistry current = forArena(arenaId);
    for (final String path : arenaConfig.fragmentIncludes()) {
      if (path == null) {
        continue;
      }
      final FragmentBinding<?> binding = BY_BASENAME.get(basenameOf(path));
      if (binding != null) {
        current = binding.install(current, path);
      }
    }

    replace(arenaId, current);
  }

  private static String basenameOf(final String path) {
    final int slash = path.lastIndexOf('/');
    return slash >= 0 ? path.substring(slash + 1) : path;
  }
}
