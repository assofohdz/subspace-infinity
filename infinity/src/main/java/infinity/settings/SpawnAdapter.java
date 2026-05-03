// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.SpawnConfig;
import infinity.config.TeamSpawn;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Typed Groovy adapter for {@code spawn.groovy} fragments. Parses a
 * {@code spawn { … }} block into a {@link SpawnConfig} record.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * spawn {
 *     warpRadiusLimit 1024                       // [Misc] WarpRadiusLimit (tiles; 1024 = no cap)
 *     team x: 512, y: 512, radius: 256           // freq 0 (and 4, 8, …)
 *     team x: 768, y: 256, radius: 128           // freq 1
 *     team x: 256, y: 768, radius: 128           // freq 2
 *     team x: 768, y: 768, radius: 128           // freq 3
 *     // …add as many as you want; lookup wraps via freq % teams.size()
 * }
 * }</pre>
 *
 * <p>Subspace canon authors 4 teams ({@code [Spawn] Team0..3}); the
 * typed DSL accepts any length, so operators can author 1, 2, 3, 4, N
 * entries. The frequency-wraparound rule from canon ("Freq 4 → Team0,
 * Freq 5 → Team1, …") generalizes naturally —
 * {@link SpawnConfig#forFreq(int)} computes
 * {@code Math.floorMod(freq, teams.size())}.
 *
 * <p>Coordinates are <em>arena-local tiles</em> per REFERENCE.md
 * {@code ## Spawn}; {@code ArenaSystem.getArenaSpawn} translates to
 * world via {@code arenaToWorld}.
 */
public final class SpawnAdapter
    implements GroovySettingsAdapter<SpawnConfig, SpawnAdapter.SpawnBuilder> {

  /** Stateless; safe to share across calls. */
  public static final SpawnAdapter INSTANCE = new SpawnAdapter();

  private SpawnAdapter() {}

  @Override
  public List<String> allowedImports() {
    return List.of();
  }

  @Override
  public SpawnBuilder bind(final Binding binding) {
    final SpawnBuilder builder = new SpawnBuilder();
    binding.setVariable("spawn", new SpawnClosure(builder));
    return builder;
  }

  @Override
  public SpawnConfig extract(final SpawnBuilder accumulator) {
    return accumulator.build();
  }

  @Override
  public SpawnConfig empty() {
    return SpawnConfig.DEFAULTS;
  }

  /** Bound to the {@code spawn} variable in the script. */
  private static final class SpawnClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final SpawnBuilder builder;

    SpawnClosure(final SpawnBuilder builder) {
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

  /** Delegate for the {@code spawn { ... }} block. */
  public static final class SpawnBuilder {

    private final List<TeamSpawn> teams = new ArrayList<>();
    private int warpRadiusLimit = SpawnConfig.WARP_RADIUS_UNLIMITED;

    SpawnBuilder() {}

    /**
     * {@code [Misc] WarpRadiusLimit} in tiles; {@code 1024} = no cap
     * per REFERENCE.md ({@link SpawnConfig#WARP_RADIUS_UNLIMITED}).
     * Slot reserved on {@link SpawnConfig#warpRadiusLimit()};
     * consumption deferred to the WarpSystem-randomization slice.
     */
    public void warpRadiusLimit(final int tiles) {
      this.warpRadiusLimit = tiles;
    }

    /**
     * {@code team x: <X>, y: <Y>, radius: <R>} — appends one
     * {@link TeamSpawn} entry. Order matters: the n-th call is the
     * spawn for {@code freq == n} (and wraps via
     * {@code freq % teams.size()} for higher freqs).
     */
    public void team(final Map<String, ?> args) {
      teams.add(
          new TeamSpawn(
              intArg("team", args, "x"),
              intArg("team", args, "y"),
              intArg("team", args, "radius")));
    }

    SpawnConfig build() {
      return new SpawnConfig(teams, warpRadiusLimit);
    }

    private static int intArg(final String block, final Map<String, ?> args, final String key) {
      final Object v = args.get(key);
      if (v instanceof Number n) {
        return n.intValue();
      }
      throw new IllegalArgumentException(
          block + " missing numeric '" + key + "' (got " + v + ")");
    }
  }
}
