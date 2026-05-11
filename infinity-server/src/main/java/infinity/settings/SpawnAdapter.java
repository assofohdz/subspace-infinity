// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

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
 *     spawnRadius 0                              // tiles; 0 = exact-point spawn (legacy fallback only)
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
 *
 * <p>{@code spawnRadius} diverges from Subspace canon
 * {@code [Misc] WarpRadiusLimit} — see
 * {@link SpawnConfig#spawnRadius()} for the divergence note.
 */
public final class SpawnAdapter
    extends SingleClosureAdapter<SpawnConfig, SpawnAdapter.SpawnBuilder> {

  /** Stateless; safe to share across calls. */
  public static final SpawnAdapter INSTANCE = new SpawnAdapter();

  private SpawnAdapter() {
    super("spawn", SpawnConfig.DEFAULTS);
  }

  @Override
  protected SpawnBuilder newBuilder() {
    return new SpawnBuilder();
  }

  @Override
  public SpawnConfig extract(final SpawnBuilder accumulator) {
    return accumulator.build();
  }

  /** Delegate for the {@code spawn { ... }} block. */
  public static final class SpawnBuilder {

    private final List<TeamSpawn> teams = new ArrayList<>();
    private int spawnRadius;

    SpawnBuilder() {}

    /**
     * Tile-radius of the disc around the legacy single-spawn coord
     * ({@code arena.groovy spawn x, z}). {@code 0} = exact-point spawn
     * (no randomization); positive values sample uniformly inside the
     * disc. Applies <em>only</em> on the legacy-fallback path — when
     * {@link SpawnBuilder#team(Map) team(…)} entries are authored, their
     * per-team {@code radius:} owns the disc instead.
     *
     * <p>Diverges from Subspace canon {@code [Misc] WarpRadiusLimit}
     * (arena-center anchor): Infinity anchors on the
     * arena.groovy-declared spawn coord. The Subspace "1024 = anywhere"
     * sentinel does not apply.
     */
    public void spawnRadius(final int tiles) {
      this.spawnRadius = tiles;
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
      return new SpawnConfig(teams, spawnRadius);
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
