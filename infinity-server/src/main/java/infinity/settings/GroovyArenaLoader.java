// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.ArenaConfig;
import infinity.config.SpawnerSpec;
import infinity.modules.ArenaModuleDeclarations;
import infinity.modules.ModuleSpec;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Evaluates {@code /arenas/<name>/arena.groovy} into an {@link ArenaConfig}; {@code null} on miss, {@link ArenaConfig#EMPTY} on broken. */
public final class GroovyArenaLoader {

  public static final String ARENA_GROOVY_TEMPLATE = "/arenas/%s/arena.groovy";

  private static final Logger log = LoggerFactory.getLogger(GroovyArenaLoader.class);
  private static final ArenaAdapter ADAPTER = new ArenaAdapter();

  /** {@code null} on missing file, {@link ArenaConfig#EMPTY} on broken script. */
  @Nullable
  public ArenaConfig load(final String arenaName) {
    return load(arenaName, String.format(ARENA_GROOVY_TEMPLATE, arenaName));
  }

  @Nullable
  public ArenaConfig load(final String arenaName, final String classpathPath) {
    final ArenaConfig cfg = GroovySettingsHost.INSTANCE.load(ADAPTER, classpathPath);
    if (cfg == null) {
      log.debug("{} not found for arena {}; null signals fail-fast", classpathPath, arenaName);
      return null;
    }
    if (cfg != ArenaConfig.EMPTY && log.isInfoEnabled()) {
      log.info(
          "Applied {} for arena {}: map='{}', ships='{}', spawn=({},{}), wallFriction={},"
              + " friendlyFire={}, fragments={}",
          classpathPath,
          arenaName,
          cfg.mapFile(),
          cfg.shipsScript(),
          cfg.spawnX(),
          cfg.spawnZ(),
          cfg.wallFriction(),
          cfg.friendlyFire(),
          cfg.fragmentIncludes());
    }
    return cfg;
  }

  /** Test seam — evaluate a literal source string with no on-disk file. */
  ArenaConfig evaluateSourceForTest(final String source, final String virtualPath) {
    return GroovySettingsHost.INSTANCE.evaluate(ADAPTER, source, virtualPath);
  }

  /** DSL adapter for {@code arena{…}}. */
  private static final class ArenaAdapter
      implements GroovySettingsAdapter<ArenaConfig, ArenaConfigBuilder> {

    @Override
    public List<String> allowedImports() {
      // Ship enum lets module kwargs refer to e.g. Ship.WARBIRD without an import line.
      return List.of("infinity.Ship");
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

  /** Backing closure for the {@code arena{…}} DSL block. */
  private static final class ArenaClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final transient ArenaConfigBuilder builder;

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

  /** Delegate for {@code arena{…}}; fields default to {@link ArenaConfig#EMPTY}. */
  public static final class ArenaConfigBuilder {

    private String mapFile = "";
    private String shipsScript = "";
    // Default to arena centre (matches ArenaConfig.EMPTY) so an omitted `spawn` directive avoids the NW corner.
    private int spawnX = ArenaConfig.EMPTY.spawnX();
    private int spawnZ = ArenaConfig.EMPTY.spawnZ();
    private final List<String> fragmentIncludes = new ArrayList<>();
    private double wallFriction = ArenaConfig.EMPTY.wallFriction();
    private final List<SpawnerSpec> spawners = new ArrayList<>();
    private int friendlyFire = ArenaConfig.EMPTY.friendlyFire();
    private final ModuleDeclarationsBuilder modules = new ModuleDeclarationsBuilder();

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

    /** Tangential-velocity drain fraction on ship-vs-wall hits, {@code [0, 1]}; see {@link ArenaConfig#wallFriction()}. */
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
     * Tri-state friendly-fire: 0=off, 1=bomb splash only, 2=all weapons.
     * Infinity-specific divergence from Subspace canon (which uses per-weapon
     * flags) — see {@code .scratch/subspace-ini-reference/REFERENCE.md}.
     */
    public void friendlyFire(final Number value) {
      if (value == null) {
        throw new IllegalArgumentException("friendlyFire requires a number (0, 1, or 2)");
      }
      final int v = value.intValue();
      if (v < 0 || v > 2) {
        throw new IllegalArgumentException(
            "friendlyFire must be 0 (off), 1 (bomb splash only), or 2 (all); got " + value);
      }
      this.friendlyFire = v;
    }

    /**
     * {@code spawners { spawn x:..., z:..., ... }} block. Each
     * {@code spawn} call inside the closure appends a {@link SpawnerSpec}
     * entry that {@code ArenaSystem} materializes into a real spawner entity
     * at arena-load time.
     */
    public void spawners(final Closure<?> body) {
      final SpawnersBlock block = new SpawnersBlock(spawners);
      body.setDelegate(block);
      body.setResolveStrategy(Closure.DELEGATE_FIRST);
      body.call();
    }

    // -- ADR-0008 module DSL --------------------------------------------------
    // Each statement: `<category> '<id>', kw: v, ...` (kwargs map first per
    // Groovy's named-arg convention) OR `<category> '<id>'` (bare). Single-pick
    // categories last-wins (per ADR-0008 DSL decision); layered append;
    // mechanics keyed by module id.

    public void teamSetup(final Map<String, Object> kwargs, final String id) {
      modules.teamSetup = Optional.of(new ModuleSpec(id, kwargs));
    }

    public void teamSetup(final String id) {
      teamSetup(Map.of(), id);
    }

    public void roster(final Map<String, Object> kwargs, final String id) {
      modules.roster = Optional.of(new ModuleSpec(id, kwargs));
    }

    public void roster(final String id) {
      roster(Map.of(), id);
    }

    public void respawnPolicy(final Map<String, Object> kwargs, final String id) {
      modules.respawnPolicy = Optional.of(new ModuleSpec(id, kwargs));
    }

    public void respawnPolicy(final String id) {
      respawnPolicy(Map.of(), id);
    }

    public void roundStructure(final Map<String, Object> kwargs, final String id) {
      modules.roundStructure = Optional.of(new ModuleSpec(id, kwargs));
    }

    public void roundStructure(final String id) {
      roundStructure(Map.of(), id);
    }

    public void matchStructure(final Map<String, Object> kwargs, final String id) {
      modules.matchStructure = Optional.of(new ModuleSpec(id, kwargs));
    }

    public void matchStructure(final String id) {
      matchStructure(Map.of(), id);
    }

    public void spawnPlacement(final Map<String, Object> kwargs, final String id) {
      modules.spawnPlacement = Optional.of(new ModuleSpec(id, kwargs));
    }

    public void spawnPlacement(final String id) {
      spawnPlacement(Map.of(), id);
    }

    public void shop(final Map<String, Object> kwargs, final String id) {
      modules.shop = Optional.of(new ModuleSpec(id, kwargs));
    }

    public void shop(final String id) {
      shop(Map.of(), id);
    }

    public void scoring(final Map<String, Object> kwargs, final String id) {
      modules.scoring.add(new ModuleSpec(id, kwargs));
    }

    public void scoring(final String id) {
      scoring(Map.of(), id);
    }

    public void winCondition(final Map<String, Object> kwargs, final String id) {
      modules.winConditions.add(new ModuleSpec(id, kwargs));
    }

    public void winCondition(final String id) {
      winCondition(Map.of(), id);
    }

    public void mechanic(final Map<String, Object> kwargs, final String id) {
      modules.mechanics.put(id, new ModuleSpec(id, kwargs));
    }

    public void mechanic(final String id) {
      mechanic(Map.of(), id);
    }
    // -------------------------------------------------------------------------

    ArenaConfig build() {
      return new ArenaConfig(
          mapFile,
          shipsScript,
          spawnX,
          spawnZ,
          List.copyOf(fragmentIncludes),
          wallFriction,
          List.copyOf(spawners),
          friendlyFire,
          modules.build());
    }
  }

  /** Internal collector for {@code ArenaConfigBuilder}'s module DSL statements. */
  private static final class ModuleDeclarationsBuilder {
    Optional<ModuleSpec> teamSetup = Optional.empty();
    Optional<ModuleSpec> roster = Optional.empty();
    Optional<ModuleSpec> respawnPolicy = Optional.empty();
    Optional<ModuleSpec> roundStructure = Optional.empty();
    Optional<ModuleSpec> matchStructure = Optional.empty();
    Optional<ModuleSpec> spawnPlacement = Optional.empty();
    Optional<ModuleSpec> shop = Optional.empty();
    final List<ModuleSpec> scoring = new ArrayList<>();
    final List<ModuleSpec> winConditions = new ArrayList<>();
    final Map<String, ModuleSpec> mechanics = new LinkedHashMap<>();

    ArenaModuleDeclarations build() {
      return new ArenaModuleDeclarations(
          teamSetup,
          roster,
          respawnPolicy,
          roundStructure,
          matchStructure,
          spawnPlacement,
          shop,
          scoring,
          winConditions,
          mechanics);
    }
  }

  /**
   * Delegate for the {@code spawners { ... }} block. Each {@code spawn}
   * call inside it accepts a Groovy named-argument map and appends a typed
   * {@link SpawnerSpec} entry to the parent builder's list.
   */
  public static final class SpawnersBlock {

    private static final String SPAWN_PREFIX = "spawners.spawn '";
    private static final String MUST_BE_NUMERIC = "' must be numeric (got ";

    private final List<SpawnerSpec> entries;

    SpawnersBlock(final List<SpawnerSpec> entries) {
      this.entries = entries;
    }

    /**
     * {@code spawn x: 512, z: 512, radius: 100, maxCount: 5, intervalMs: 2000,
     * ttlMs: 10000, onRing: false, weights: [Bomb: 100, Gun: 100],
     * countPerPlayer: 2, radiusPerPlayer: 50, regenBatch: 3, hidden: true}.
     *
     * <p>Required: {@code x}, {@code z}, {@code radius}, {@code maxCount},
     * {@code intervalMs}.
     * Optional with defaults: {@code ttlMs} (0 = use the global
     * {@code MapFactory.PRIZE_DEFAULT_DECAY_MS}); {@code onRing} (false = uniform
     * within disc); {@code weights} (empty map = use arena {@code [PrizeWeight]}
     * defaults); {@code countPerPlayer} (0 = no count scaling);
     * {@code radiusPerPlayer} (0 = no radius scaling); {@code regenBatch} (1 = one
     * prize per interval); {@code hidden} (false = visible to clients).
     *
     * <p>Slice 8d (C2) added the four scaling/visibility fields. See
     * {@link SpawnerSpec} for the additive-scaling formula and Subspace canon
     * mapping.
     */
    public void spawn(final Map<String, ?> args) {
      entries.add(
          new SpawnerSpec(
              intArg(args, "x"),
              intArg(args, "z"),
              doubleArg(args, "radius"),
              intArg(args, "maxCount"),
              doubleArg(args, "intervalMs"),
              longArg(args, "ttlMs", 0L),
              boolArg(args, "onRing", false),
              weightsArg(args, "weights"),
              intArg(args, "countPerPlayer", 0),
              doubleArg(args, "radiusPerPlayer", 0.0),
              intArg(args, "regenBatch", 1),
              boolArg(args, "hidden", false)));
    }

    private static int intArg(final Map<String, ?> args, final String key) {
      final Object v = args.get(key);
      if (v instanceof Number n) {
        return n.intValue();
      }
      throw new IllegalArgumentException(
          "spawners.spawn missing numeric '" + key + "' (got " + v + ")");
    }

    private static int intArg(final Map<String, ?> args, final String key, final int fallback) {
      final Object v = args.get(key);
      if (v == null) {
        return fallback;
      }
      if (v instanceof Number n) {
        return n.intValue();
      }
      throw new IllegalArgumentException(
          SPAWN_PREFIX + key + MUST_BE_NUMERIC + v + ")");
    }

    private static double doubleArg(final Map<String, ?> args, final String key) {
      final Object v = args.get(key);
      if (v instanceof Number n) {
        return n.doubleValue();
      }
      throw new IllegalArgumentException(
          "spawners.spawn missing numeric '" + key + "' (got " + v + ")");
    }

    private static double doubleArg(
        final Map<String, ?> args, final String key, final double fallback) {
      final Object v = args.get(key);
      if (v == null) {
        return fallback;
      }
      if (v instanceof Number n) {
        return n.doubleValue();
      }
      throw new IllegalArgumentException(
          SPAWN_PREFIX + key + MUST_BE_NUMERIC + v + ")");
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
          SPAWN_PREFIX + key + MUST_BE_NUMERIC + v + ")");
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
          SPAWN_PREFIX + key + "' must be a boolean (got " + v + ")");
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
            SPAWN_PREFIX + key + "' must be a [String: int] map (got " + v + ")");
      }
      final java.util.Map<String, Integer> out = new java.util.HashMap<>();
      for (final Map.Entry<?, ?> e : raw.entrySet()) {
        if (!(e.getKey() instanceof String k)) {
          throw new IllegalArgumentException(
              SPAWN_PREFIX + key + "' has non-String key: " + e.getKey());
        }
        if (!(e.getValue() instanceof Number n)) {
          throw new IllegalArgumentException(
              SPAWN_PREFIX
                  + key
                  + "' value for '"
                  + k
                  + MUST_BE_NUMERIC
                  + e.getValue()
                  + ")");
        }
        out.put(k, n.intValue());
      }
      return out;
    }
  }
}
