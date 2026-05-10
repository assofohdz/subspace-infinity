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

/**
 * Evaluates an arena's Groovy ship config and installs the result into
 * {@link ConfigRegistrySystem}. Thin facade over {@link GroovySettingsHost} —
 * the host owns I/O, hardening and error handling; this class supplies the
 * {@code ship(Ship.X) { … }} DSL via {@link ShipConfigBuilder} and composes
 * the result with {@code ConfigRegistrySystem.replace}.
 *
 * <p>Failure handling — any failure (no path configured, missing file, parse
 * error, eval error) logs a warning and installs the {@link ShipFallback#FALLBACK}
 * snapshot so the arena stays playable. Callers never see an exception.
 *
 * <p>Script DSL — see {@link ShipConfigBuilder} for per-block stat / weapon /
 * inventory / status methods. Example shape:
 *
 * <pre>{@code
 * ship(Ship.WARBIRD) {
 *     rotation initial: 210, max: 300, upgrade: 40
 *     thrust   initial: 16,  max: 19,  upgrade: 2
 *     speed    initial: 2010, max: 3250, upgrade: 250
 *     recharge initial: 400,  max: 1150, upgrade: 166
 *     energy   initial: 1000, max: 1700, upgrade: 100
 *     linearDamping       0.99
 *     turnResponsiveness  8.0
 *     bounceRestitution   1.0
 *     radarRange          250
 *     bombs   start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 10, fireDelay: 25, speed: 2000, thrust: 400
 *     bullets start: BulletLevel.LEVEL_1, max: BulletLevel.LEVEL_4, cost: 10, fireDelay: 25
 *     mines   start: BombLevel.BOMB_1, max: BombLevel.BOMB_4, cost: 50, fireDelay: 500, speed: 0
 *     bursts  start: 5, max: 5
 *     thors   start: 2, max: 2, fireDelay: 1000
 *     repels  start: 10, max: 20
 * }
 * }</pre>
 *
 * <p>The {@code Ship}, {@code BombLevel}, and {@code BulletLevel} enums are
 * added as default imports (and whitelisted) by the host. See
 * {@link ShipConfigBuilder} Javadoc for default-handling rules and the
 * canonical-Subspace mapping of each block.
 */
public final class GroovyShipLoader {

  private static final Logger log = LoggerFactory.getLogger(GroovyShipLoader.class);
  private static final ShipAdapter ADAPTER = new ShipAdapter();

  /**
   * Re-export of {@link ShipConfigBuilder#DEFAULT_RADAR_RANGE} so the
   * {@code GroovyShipLoaderRadarTest} contract — checking that the FALLBACK
   * radar range matches the documented default — keeps a stable
   * fully-qualified reference. Other defaults live on
   * {@link ShipConfigBuilder} (movement / feel) or {@link ShipFallback}
   * (weapon / inventory).
   */
  static final double DEFAULT_RADAR_RANGE = ShipConfigBuilder.DEFAULT_RADAR_RANGE;

  /**
   * Re-export of {@link ShipFallback#FALLBACK} for callers that historically
   * referenced {@code GroovyShipLoader.FALLBACK}. Public surface — kept as a
   * stable lookup for arena-bootstrap code, doc references in
   * {@link infinity.config.ArenaConfig}, and the radar test contract.
   */
  public static final ConfigRegistry FALLBACK = ShipFallback.FALLBACK;

  private final ConfigRegistrySystem configRegistry;

  public GroovyShipLoader(final ConfigRegistrySystem configRegistry) {
    this.configRegistry = configRegistry;
  }

  /**
   * Load and install the ship config for {@code arenaId} from the given
   * classpath path. {@code null} or missing/broken script → log a warning
   * and install {@link #FALLBACK}. Never throws.
   *
   * @param arenaId the arena to populate
   * @param classpathPath classpath-absolute path to the script (e.g.
   *     {@code "/conf/trench-04-2026/ships.groovy"}), or {@code null} if
   *     no script is configured for this arena
   */
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

  /**
   * Common "log warn + install FALLBACK" path for the three fallback branches
   * of {@link #apply}. Extracted to keep apply's flow readable and to centralise
   * the warn-guard discipline.
   */
  private void installFallback(final ArenaId arenaId, final String fmt, final Object... args) {
    if (log.isWarnEnabled()) {
      log.warn(fmt, args);
    }
    configRegistry.replace(arenaId, FALLBACK);
  }

  /**
   * Logs a successful ships.groovy load: one INFO header and one INFO line per
   * configured ship. Guarded so we don't pay the iteration when info is off.
   */
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

  /** Adapter holding the {@code ship(Ship.X) { … }} DSL semantics. */
  private static final class ShipAdapter
      implements GroovySettingsAdapter<ConfigRegistry, ConfigRegistry.Builder> {

    @Override
    public List<String> allowedImports() {
      // Scripts reference three enums directly: Ship (for the ship() block
      // arg), BombLevel (for bombs/mines start/max), and BulletLevel (for bullets
      // start/max). The host adds each as a default import (so
      // `Ship.WARBIRD` / `BombLevel.BOMB_1` / `BulletLevel.LEVEL_1` work without
      // explicit `import` lines) AND whitelists them so an explicit import
      // would also be valid.
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

  /**
   * Bound to the {@code ship} variable in the script. Takes a {@link Ship}
   * type and a configuring closure; builds a {@link ShipConfig} and adds it
   * to the registry snapshot under construction.
   */
  private static final class ShipClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final ConfigRegistry.Builder registryBuilder;

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
