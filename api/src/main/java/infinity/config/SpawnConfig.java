// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;

/**
 * Per-arena spawn-point configuration. Read at spawn-time by
 * {@code ArenaSystem.getArenaSpawn(arenaName, freq)} via the arena's
 * {@link infinity.settings.ConfigRegistry#spawn()} slot, looked up by
 * {@code freq % teams.size()}.
 *
 * <p>Subspace fragment keys (REFERENCE.md {@code ## Spawn}):
 * <ul>
 *   <li>{@code [Spawn] Team<N>-X / Team<N>-Y / Team<N>-Radius} → one
 *       {@link TeamSpawn} entry in {@link #teams}, indexed by author
 *       order. Subspace canon authors 4 teams; Infinity's typed shape
 *       is a list of any length, so operators can author 1, 2, 3, 4,
 *       N team entries.
 *   <li>{@link #spawnRadius} → radius of the disc around the
 *       arena's <em>legacy single-spawn coord</em>
 *       ({@code ArenaConfig.spawnX/spawnZ}) when no typed
 *       {@link TeamSpawn} entries are authored. Diverges from Subspace
 *       canon {@code [Misc] WarpRadiusLimit} (REFERENCE.md
 *       "Random spawn distance limit from arena center"): canon anchors
 *       on arena <em>center</em>, Infinity anchors on the
 *       arena.groovy-declared spawn coord. The Subspace "1024 =
 *       anywhere" sentinel does not apply — Infinity uses {@code 0} =
 *       exact-point spawn (no randomization), and any positive value is
 *       the tile-radius of the spawn disc.
 * </ul>
 *
 * <p>Frequency wraparound: Subspace canon says "Freq 4 → Team0,
 * Freq 5 → Team1, …" — implemented as {@code Math.floorMod(freq,
 * teams.size())} in {@link #forFreq(int)}, which generalizes the
 * 4-team wraparound to N teams for free.
 *
 * <p>Pattern 4 ({@code .claude/rules/config-pattern.md}): this is the
 * <em>template</em> tier. The consumer
 * ({@code ArenaSystem.getArenaSpawn}) reads it directly to compute a
 * world coord — there's no per-entity "current spawn" component
 * because spawning is a one-shot action, not persistent state.
 *
 * @param teams ordered list of per-team spawn definitions; empty list
 *     means "no typed spawn data — fall back to legacy
 *     {@code ArenaConfig.spawnX/spawnZ}" at the consumer
 * @param spawnRadius tile-radius of the spawn disc around the legacy
 *     single-spawn coord. {@code 0} = exact-point spawn (no
 *     randomization). Positive values sample uniformly inside the
 *     disc. Applies <em>only</em> on the legacy-fallback path —
 *     authored per-team {@link TeamSpawn#radiusTiles()} owns the
 *     disc when {@link #teams} is non-empty. Diverges from Subspace
 *     {@code [Misc] WarpRadiusLimit} (arena-center anchor) by
 *     anchoring on the arena.groovy-declared coord instead
 */
public record SpawnConfig(List<TeamSpawn> teams, int spawnRadius) {

  /**
   * Empty-spawn baseline — empty {@code teams} list signals "no typed
   * spawn data", so the consumer falls back to legacy
   * {@code ArenaConfig.spawnX/spawnZ}. {@link #spawnRadius} defaults
   * to {@code 0} (exact-point spawn — no randomization).
   */
  public static final SpawnConfig DEFAULTS = new SpawnConfig(List.of(), 0);

  public SpawnConfig {
    Objects.requireNonNull(teams, "teams");
    teams = List.copyOf(teams);
  }

  /**
   * Look up the spawn for a given player frequency. Wraps around with
   * {@code Math.floorMod} so negative freqs (defensive) and freqs
   * past the configured team count both resolve to a valid entry.
   *
   * @return the matching {@link TeamSpawn}, or {@code null} when
   *     {@link #teams} is empty (consumer falls back to legacy
   *     {@code ArenaConfig.spawnX/spawnZ})
   */
  @Nullable
  public TeamSpawn forFreq(final int freq) {
    if (teams.isEmpty()) {
      return null;
    }
    return teams.get(Math.floorMod(freq, teams.size()));
  }
}
