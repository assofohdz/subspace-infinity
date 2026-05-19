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
import infinity.config.ThorConfig;
import infinity.es.arena.ArenaId;
import infinity.modules.ArenaModuleDeclarations;
import infinity.systems.SettingsSystem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Holds one {@link ConfigRegistry} snapshot per arena; atomic-replace via {@link #replace}; {@link #load} is the single load + hot-reload entry point. */
public class ConfigRegistrySystem extends AbstractGameSystem {

  private static final Logger log = LoggerFactory.getLogger(ConfigRegistrySystem.class);

  private static final String ARENA_ID = "arenaId";

  private final ConcurrentMap<String, ConfigRegistry> byArena = new ConcurrentHashMap<>();

  private SettingsSystem settings;
  private GroovyShipLoader shipLoader;

  // Fragment basename → adapter + slot. Adding a new *Adapter is one line here + one slot in ConfigRegistry.SLOTS.
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
          FragmentBinding.of("thor.groovy", ThorConfig.class, ThorAdapter.INSTANCE),
          FragmentBinding.of("prize.groovy", PrizeConfig.class, PrizeAdapter.INSTANCE),
          FragmentBinding.of(
              "prize-weights.groovy",
              PrizeWeightsConfig.class,
              PrizeWeightsAdapter.INSTANCE));

  private static final Map<String, FragmentBinding<?>> BY_BASENAME = indexByBasename();

  private static Map<String, FragmentBinding<?>> indexByBasename() {
    final Map<String, FragmentBinding<?>> map = new LinkedHashMap<>();
    for (final FragmentBinding<?> binding : FRAGMENT_BINDINGS) {
      map.put(binding.basename(), binding);
    }
    return Map.copyOf(map);
  }

  /** Basename → {@code *Config} slot + adapter; {@link #install} avoids unchecked casts at call sites. */
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

    ConfigRegistry install(final ConfigRegistry current, final String classpathPath) {
      final T parsed = GroovySettingsHost.INSTANCE.load(adapter, classpathPath);
      final T value = parsed != null ? parsed : adapter.empty();
      return current.with(configType, value);
    }
  }

  @Override
  protected void initialize() {
    // Collaborators are registered after this system in GameServer.
    settings = getSystem(SettingsSystem.class);
    shipLoader = getSystem(GroovyShipLoader.class);
  }

  @Override
  protected void terminate() {
    byArena.clear();
  }

  /** {@link ConfigRegistry#EMPTY} for unknown arenas — never null. */
  public ConfigRegistry forArena(final ArenaId arenaId) {
    Objects.requireNonNull(arenaId, ARENA_ID);
    return byArena.getOrDefault(arenaId.getArena(), ConfigRegistry.EMPTY);
  }

  /** Atomic swap; readers see either the old or new snapshot. */
  public void replace(final ArenaId arenaId, final ConfigRegistry snapshot) {
    Objects.requireNonNull(arenaId, ARENA_ID);
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

  public void remove(final ArenaId arenaId) {
    Objects.requireNonNull(arenaId, ARENA_ID);
    byArena.remove(arenaId.getArena());
  }

  /** Three-phase: legacy INI fragments → typed ships → typed per-fragment adapters. Atomic per call. */
  public void load(final ArenaId arenaId, final ArenaConfig arenaConfig) {
    Objects.requireNonNull(arenaId, ARENA_ID);
    Objects.requireNonNull(arenaConfig, "arenaConfig");
    final String arenaName = arenaId.getArena();

    // Phase 1: legacy INI fragments — skip any with a typed adapter (Phase 3 handles those).
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

    // Phase 4: arena-level inline blocks (parsed from arena.groovy, not a fragment include).
    current = current.with(ArenaModuleDeclarations.class, arenaConfig.modules());

    replace(arenaId, current);
  }

  private static String basenameOf(final String path) {
    final int slash = path.lastIndexOf('/');
    return slash >= 0 ? path.substring(slash + 1) : path;
  }
}
