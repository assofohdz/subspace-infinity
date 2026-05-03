// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.ArenaConfig;
import infinity.config.PrizeSpawnerSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates a per-arena Groovy config and returns a typed {@link ArenaConfig}.
 * Thin facade over {@link GroovySettingsHost} — the host owns I/O, hardening
 * and error handling; this class supplies the {@code arena { … }} DSL.
 *
 * <p>Returns {@code null} when the arena.groovy file is missing — callers
 * (e.g. {@code ArenaSystem.loadArenaConfig}) fail-fast on that since the
 * legacy {@code arena.conf} INI fallback was retired in
 * zone-arena-to-groovy #3. Returns {@link ArenaConfig#EMPTY} on parse / eval
 * failure (logged) so a broken Groovy file uses defaults rather than failing
 * the whole arena.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * arena {
 *     map '04-2026-trench/pub2025.lvl'
 *     shipsScript '/conf/trench-04-2026/ships.groovy'
 *     spawn 1000, 20
 *     wallFriction 0.0   // tangential friction on ship-vs-wall hits (0 = slidey)
 *     includeFragment '/conf/trench-04-2026/trench.conf'
 *     // includeFragment '/conf/another.conf' — repeat as needed
 *     prizeSpawners {
 *         spawn x: 512, z: 512, radius: 100, maxCount: 5, intervalMs: 2000, ttlMs: 10000
 *         spawn x:  50, z:  50, radius: 100, maxCount: 5, intervalMs: 2000, ttlMs: 10000
 *     }
 * }
 * }</pre>
 *
 * <p>All directives are optional; an entirely empty script yields
 * {@link ArenaConfig#EMPTY}. Multiple {@code includeFragment} calls accumulate
 * in declaration order.
 */
public final class GroovyArenaLoader {

  /**
   * Classpath path under {@code zone/} where each arena's Groovy config lives.
   * The arena name is interpolated into the {@code <name>} slot.
   */
  public static final String ARENA_GROOVY_TEMPLATE = "/arenas/%s/arena.groovy";

  private static final Logger log = LoggerFactory.getLogger(GroovyArenaLoader.class);
  private static final ArenaAdapter ADAPTER = new ArenaAdapter();

  /**
   * Try to load and parse {@code /arenas/<arenaName>/arena.groovy}.
   *
   * @return the parsed {@link ArenaConfig}, or {@code null} if no Groovy file
   *     exists for this arena (caller fails fast — INI fallback is retired).
   *     Returns {@link ArenaConfig#EMPTY} on parse / eval failure (logged) so
   *     callers don't silently fail-fast on a broken Groovy file.
   */
  @Nullable
  public ArenaConfig load(final String arenaName) {
    return load(arenaName, String.format(ARENA_GROOVY_TEMPLATE, arenaName));
  }

  /** Same as {@link #load(String)} but with an explicit classpath path. */
  @Nullable
  public ArenaConfig load(final String arenaName, final String classpathPath) {
    final ArenaConfig cfg = GroovySettingsHost.INSTANCE.load(ADAPTER, classpathPath);
    if (cfg == null) {
      log.debug("{} not found for arena {}; null signals fail-fast", classpathPath, arenaName);
      return null;
    }
    if (cfg != ArenaConfig.EMPTY) {
      log.info(
          "Applied {} for arena {}: map='{}', ships='{}', spawn=({},{}), wallFriction={},"
              + " fragments={}",
          classpathPath,
          arenaName,
          cfg.mapFile(),
          cfg.shipsScript(),
          cfg.spawnX(),
          cfg.spawnZ(),
          cfg.wallFriction(),
          cfg.fragmentIncludes());
    }
    return cfg;
  }

  /**
   * Test-only entry point: parse {@code source} as if it had come from a file
   * at {@code virtualPath}. Returns {@link ArenaConfig#EMPTY} on parse / eval
   * failure, mirroring {@link #load}'s broken-script branch — lets tests
   * exercise that branch without writing to disk.
   */
  ArenaConfig evaluateSourceForTest(final String source, final String virtualPath) {
    return GroovySettingsHost.INSTANCE.evaluate(ADAPTER, source, virtualPath);
  }

  /** Adapter holding the {@code arena { … }} DSL semantics. */
  private static final class ArenaAdapter
      implements GroovySettingsAdapter<ArenaConfig, ArenaConfigBuilder> {

    @Override
    public List<String> allowedImports() {
      // arena.groovy uses no explicit imports; auto-imports cover String/List.
      return Collections.emptyList();
    }

    @Override
    public ArenaConfigBuilder bind(final Binding binding) {
      final ArenaConfigBuilder builder = new ArenaConfigBuilder();
      binding.setVariable("arena", new ArenaClosure(builder));
      return builder;
    }

    @Override
    public ArenaConfig extract(final ArenaConfigBuilder accumulator) {
      return accumulator.build();
    }

    @Override
    public ArenaConfig empty() {
      return ArenaConfig.EMPTY;
    }
  }

  /** Bound to the {@code arena} variable; mirrors {@code ZoneClosure}. */
  private static final class ArenaClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final ArenaConfigBuilder builder;

    ArenaClosure(final ArenaConfigBuilder builder) {
      super(null);
      this.builder = builder;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final Closure<?> body) {
      body.setDelegate(builder);
      body.setResolveStrategy(DELEGATE_FIRST);
      body.call();
      return null;
    }
  }

  /**
   * Delegate for the {@code arena { ... }} block. Fields default to
   * {@link ArenaConfig#EMPTY}'s values so partial scripts are valid; missing
   * directives mean "use the empty default."
   */
  public static final class ArenaConfigBuilder {

    private String mapFile = "";
    private String shipsScript = "";
    // Default to the arena's centre tile so an arena.groovy that omits the
    // `spawn` directive puts players in the middle of the map instead of the
    // NW corner. Mirrors ArenaConfig.EMPTY's spawn fallback.
    private int spawnX = ArenaConfig.EMPTY.spawnX();
    private int spawnZ = ArenaConfig.EMPTY.spawnZ();
    private final List<String> fragmentIncludes = new ArrayList<>();
    private double wallFriction = ArenaConfig.EMPTY.wallFriction();
    private final List<PrizeSpawnerSpec> prizeSpawners = new ArrayList<>();

    // Package-private so tests can build configs without the full GroovyShell.
    ArenaConfigBuilder() {}

    public void map(final String mapFile) {
      this.mapFile = mapFile == null ? "" : mapFile.trim();
    }

    public void shipsScript(final String shipsScript) {
      this.shipsScript = shipsScript == null ? "" : shipsScript.trim();
    }

    public void spawn(final int x, final int z) {
      this.spawnX = x;
      this.spawnZ = z;
    }

    public void includeFragment(final String classpathPath) {
      if (classpathPath != null && !classpathPath.isBlank()) {
        fragmentIncludes.add(classpathPath.trim());
      }
    }

    /**
     * Per-contact fraction of <i>tangential</i> velocity drained on
     * ship-vs-wall hits (sliding-deceleration). Not the resolver's standard
     * Coulomb friction — that would torque the body's heading at off-center
     * contact points, which is wrong for arcade ship physics. See
     * {@link ArenaConfig#wallFriction()} for the full semantic and tuning
     * guidance. {@code 0.0} (default) keeps walls frictionless. Values
     * outside {@code [0, 1]} are rejected.
     */
    public void wallFriction(final Number value) {
      if (value == null) {
        throw new IllegalArgumentException("wallFriction requires a number");
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v < 0.0 || v > 1.0) {
        throw new IllegalArgumentException(
            "wallFriction must be a finite value in [0, 1]; got " + value);
      }
      this.wallFriction = v;
    }

    /**
     * {@code prizeSpawners { spawn x:..., z:..., ... }} block. Each
     * {@code spawn} call inside the closure appends a {@link PrizeSpawnerSpec}
     * entry that {@code ArenaSystem} materializes into a real spawner entity
     * at arena-load time.
     */
    public void prizeSpawners(final Closure<?> body) {
      final PrizeSpawnersBlock block = new PrizeSpawnersBlock(prizeSpawners);
      body.setDelegate(block);
      body.setResolveStrategy(Closure.DELEGATE_FIRST);
      body.call();
    }

    ArenaConfig build() {
      return new ArenaConfig(
          mapFile,
          shipsScript,
          spawnX,
          spawnZ,
          List.copyOf(fragmentIncludes),
          wallFriction,
          List.copyOf(prizeSpawners));
    }
  }

  /**
   * Delegate for the {@code prizeSpawners { ... }} block. Each {@code spawn}
   * call inside it accepts a Groovy named-argument map and appends a typed
   * {@link PrizeSpawnerSpec} entry to the parent builder's list.
   */
  public static final class PrizeSpawnersBlock {

    private final List<PrizeSpawnerSpec> entries;

    PrizeSpawnersBlock(final List<PrizeSpawnerSpec> entries) {
      this.entries = entries;
    }

    /**
     * {@code spawn x: 512, z: 512, radius: 100, maxCount: 5, intervalMs: 2000,
     * ttlMs: 10000, onRing: false, weights: [Bomb: 100, Gun: 100]}.
     * {@code onRing} defaults to {@code false} (uniform-disc spawn);
     * {@code ttlMs} defaults to {@code 0} (use the global
     * {@code GameEntities.PRIZE_DEFAULT_DECAY_MS}); {@code weights} defaults to an
     * empty map (no override; use the arena's {@code [PrizeWeight]} defaults).
     */
    public void spawn(final Map<String, ?> args) {
      entries.add(
          new PrizeSpawnerSpec(
              intArg(args, "x"),
              intArg(args, "z"),
              doubleArg(args, "radius"),
              intArg(args, "maxCount"),
              doubleArg(args, "intervalMs"),
              longArg(args, "ttlMs", 0L),
              boolArg(args, "onRing", false),
              weightsArg(args, "weights")));
    }

    private static int intArg(final Map<String, ?> args, final String key) {
      final Object v = args.get(key);
      if (v instanceof Number n) {
        return n.intValue();
      }
      throw new IllegalArgumentException(
          "prizeSpawners.spawn missing numeric '" + key + "' (got " + v + ")");
    }

    private static double doubleArg(final Map<String, ?> args, final String key) {
      final Object v = args.get(key);
      if (v instanceof Number n) {
        return n.doubleValue();
      }
      throw new IllegalArgumentException(
          "prizeSpawners.spawn missing numeric '" + key + "' (got " + v + ")");
    }

    private static long longArg(final Map<String, ?> args, final String key, final long fallback) {
      final Object v = args.get(key);
      if (v == null) {
        return fallback;
      }
      if (v instanceof Number n) {
        return n.longValue();
      }
      throw new IllegalArgumentException(
          "prizeSpawners.spawn '" + key + "' must be numeric (got " + v + ")");
    }

    private static boolean boolArg(
        final Map<String, ?> args, final String key, final boolean fallback) {
      final Object v = args.get(key);
      if (v == null) {
        return fallback;
      }
      if (v instanceof Boolean b) {
        return b;
      }
      throw new IllegalArgumentException(
          "prizeSpawners.spawn '" + key + "' must be a boolean (got " + v + ")");
    }

    /**
     * Coerce {@code args[key]} into a {@code Map<String, Integer>}. Missing
     * key → empty map (use arena defaults). Groovy's {@code [k: v]} literals
     * arrive here as {@code Map<String, Object>}; values must be numeric.
     */
    private static java.util.Map<String, Integer> weightsArg(
        final Map<String, ?> args, final String key) {
      final Object v = args.get(key);
      if (v == null) {
        return java.util.Map.of();
      }
      if (!(v instanceof Map<?, ?> raw)) {
        throw new IllegalArgumentException(
            "prizeSpawners.spawn '" + key + "' must be a [String: int] map (got " + v + ")");
      }
      final java.util.Map<String, Integer> out = new java.util.HashMap<>();
      for (final Map.Entry<?, ?> e : raw.entrySet()) {
        if (!(e.getKey() instanceof String k)) {
          throw new IllegalArgumentException(
              "prizeSpawners.spawn '" + key + "' has non-String key: " + e.getKey());
        }
        if (!(e.getValue() instanceof Number n)) {
          throw new IllegalArgumentException(
              "prizeSpawners.spawn '"
                  + key
                  + "' value for '"
                  + k
                  + "' must be numeric (got "
                  + e.getValue()
                  + ")");
        }
        out.put(k, n.intValue());
      }
      return out;
    }
  }
}
