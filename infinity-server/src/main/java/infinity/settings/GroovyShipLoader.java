// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.BombLevel;
import infinity.BulletLevel;
import infinity.Ship;
import infinity.config.ShipConfig;
import infinity.es.arena.ArenaId;
import java.util.List;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Evaluates {@code ships.groovy} for an arena and installs the resulting {@link ConfigRegistry}; never throws (installs {@link #FALLBACK} on any failure). */
public final class GroovyShipLoader {

  private static final Logger log = LoggerFactory.getLogger(GroovyShipLoader.class);
  private static final ShipAdapter ADAPTER = new ShipAdapter();

  // Re-exports kept for test/stable-API references.
  static final double DEFAULT_RADAR_RANGE = ShipConfigBuilder.DEFAULT_RADAR_RANGE;

  public static final ConfigRegistry FALLBACK = ShipFallback.FALLBACK;

  private final ConfigRegistrySystem configRegistry;

  public GroovyShipLoader(final ConfigRegistrySystem configRegistry) {
    this.configRegistry = configRegistry;
  }

  /** Loads the ship config for {@code arenaId}; missing/broken → install {@link #FALLBACK}. Never throws. */
  public void apply(final ArenaId arenaId, @Nullable final String classpathPath) {
    if (classpathPath == null || classpathPath.isBlank()) {
      installFallback(
          arenaId,
          "No [Scripts] Ships configured for arena {}; installing fallback defaults",
          arenaId.getArena());
      return;
    }

    final ConfigRegistry snapshot = GroovySettingsHost.INSTANCE.load(ADAPTER, classpathPath);
    if (snapshot == null) {
      // Missing — host's not-found log is debug-level; surface the fallback
      // install at warn with arena context so unmigrated arenas are visible.
      installFallback(
          arenaId,
          "ships.groovy for arena {} not found at {}; installing fallback defaults",
          arenaId.getArena(),
          classpathPath);
      return;
    }
    if (snapshot == FALLBACK) {
      // Broken — host already logged the exception at warn. Add an arena-
      // context warn so a tail of the log shows which arena got the fallback.
      installFallback(
          arenaId,
          "ships.groovy for arena {} at {} failed to evaluate; installed fallback defaults",
          arenaId.getArena(),
          classpathPath);
      return;
    }

    configRegistry.replace(arenaId, snapshot);
    logSuccess(classpathPath, arenaId, snapshot);
  }

  private void installFallback(final ArenaId arenaId, final String fmt, final Object... args) {
    if (log.isWarnEnabled()) {
      log.warn(fmt, args);
    }
    configRegistry.replace(arenaId, FALLBACK);
  }

  private static void logSuccess(
      final String classpathPath, final ArenaId arenaId, final ConfigRegistry snapshot) {
    if (log.isInfoEnabled()) {
      log.info(
          "Applied {} for arena {} ({} ships configured)",
          classpathPath,
          arenaId.getArena(),
          snapshot.configuredShips().size());
      for (final Ship ship : snapshot.configuredShips()) {
        log.info("  parsed config: {} -> {}", ship, snapshot.getShip(ship));
      }
    }
  }

  /** DSL adapter for {@code ship(Ship.X){…}}. */
  private static final class ShipAdapter
      implements GroovySettingsAdapter<ConfigRegistry, ConfigRegistry.Builder> {

    @Override
    public List<String> allowedImports() {
      return List.of(Ship.class.getName(), BombLevel.class.getName(), BulletLevel.class.getName());
    }

    @Override
    public ConfigRegistry.Builder bind(final Binding binding) {
      final ConfigRegistry.Builder builder = ConfigRegistry.builder();
      binding.setVariable("ship", new ShipClosure(builder));
      return builder;
    }

    @Override
    public ConfigRegistry extract(final ConfigRegistry.Builder accumulator) {
      return accumulator.build();
    }

    @Override
    public ConfigRegistry empty() {
      return FALLBACK;
    }
  }

  /** Backing closure for {@code ship(Ship.X){…}} — builds one {@link ShipConfig} into the registry. */
  private static final class ShipClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final transient ConfigRegistry.Builder registryBuilder;

    ShipClosure(final ConfigRegistry.Builder registryBuilder) {
      super(null);
      this.registryBuilder = registryBuilder;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final Ship type, final Closure<?> body) {
      final ShipConfigBuilder shipBuilder = new ShipConfigBuilder(type);
      body.setDelegate(shipBuilder);
      body.setResolveStrategy(DELEGATE_FIRST);
      body.call();
      registryBuilder.ship(type, shipBuilder.build());
      return null;
    }
  }
}
