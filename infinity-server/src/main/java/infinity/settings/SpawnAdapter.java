// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.SpawnConfig;
import infinity.config.TeamSpawn;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Typed adapter for {@code spawn {…}} → {@link SpawnConfig}. Coords are arena-local tiles (REFERENCE.md §Spawn). */
public final class SpawnAdapter
    extends SingleClosureAdapter<SpawnConfig, SpawnAdapter.SpawnBuilder> {

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
     * Tile-radius around the legacy {@code arena.groovy spawn x, z} coord;
     * {@code 0} = exact-point spawn. Applies only on the legacy-fallback
     * path — when {@code team(…)} entries are authored, their per-team
     * radius wins. Diverges from Subspace {@code [Misc] WarpRadiusLimit}
     * (arena-center anchor; "1024 = anywhere" sentinel does not apply
     * here). See {@link SpawnConfig#spawnRadius()}.
     */
    public void spawnRadius(final int tiles) {
      this.spawnRadius = tiles;
    }

    /** {@code team x:…, y:…, radius:…} — n-th call is freq {@code n}; higher freqs wrap via mod. */
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
