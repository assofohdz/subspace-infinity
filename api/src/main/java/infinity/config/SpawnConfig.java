// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/** Per-arena spawn-point configuration ({@code [Spawn] Team<N>-X/Y/Radius}); see REFERENCE.md {@code ## Spawn}. {@code spawnRadius} diverges from {@code WarpRadiusLimit}: anchors on arena.groovy-declared coord, not arena center; {@code 0} = exact-point. */
public record SpawnConfig(List<TeamSpawn> teams, int spawnRadius) {

  /** Empty {@code teams} signals fallback to legacy {@code ArenaConfig.spawnX/spawnZ}. */
  public static final SpawnConfig DEFAULTS = new SpawnConfig(List.of(), 0);

  public SpawnConfig {
    Objects.requireNonNull(teams, "teams");
    teams = List.copyOf(teams);
  }

  /** {@code null} when {@link #teams} is empty (consumer falls back to legacy spawn coord). */
  @Nullable
  public TeamSpawn forFreq(final int freq) {
    if (teams.isEmpty()) {
      return null;
    }
    return teams.get(Math.floorMod(freq, teams.size()));
  }
}
