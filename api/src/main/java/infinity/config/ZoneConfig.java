// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import java.util.List;

/**
 * Immutable, typed binding for zone-scope server config. Replaces the legacy
 * INI {@code zone.conf} as the source-of-truth for zone-wide settings; produced
 * by the Groovy config layer and read by zone-startup, connect-time spawn, and
 * the dev-mode Groovy-script reload poller.
 *
 * @param autoLoadArenas arena folder names under {@code zone/arenas/} the
 *     server brings up on first tick (formerly {@code [Startup] AutoLoad=})
 * @param enterSpawnArena name of the loaded arena whose {@code [Spawn] X/Z}
 *     becomes the world-space spawn for new player sessions on first connect;
 *     blank means "spawn at world origin" (formerly
 *     {@code [ZoneEnterSpawn] Arena=})
 * @param scriptPollIntervalSeconds dev-mode throttle for the per-arena
 *     {@code ships.groovy} file watcher in {@code ArenaSystem.pollScriptWatches}
 *     — stat() once per arena per this many seconds. {@code 5} keeps a
 *     save-and-tab-back loop responsive without burning syscalls at 60 Hz in
 *     production runs that happen to have on-disk script paths reachable.
 *     Read once at server startup; not hot-reloadable (zone.groovy itself
 *     isn't watched).
 * @param repelFriendlies when {@code true}, repels push every
 *     {@link infinity.es.Repellable} body in radius regardless of team
 *     (Subspace canon — repels are a movement effect, not damage). When
 *     {@code false}, same-frequency ships are skipped. Bombs / mines / other
 *     non-frequency-bearing entities are always pushed. Infinity-specific
 *     ops knob; not in REFERENCE.md. See slice S5.
 */
public record ZoneConfig(
    List<String> autoLoadArenas,
    String enterSpawnArena,
    double scriptPollIntervalSeconds,
    boolean repelFriendlies) {

  /**
   * Empty fallback installed if {@code zone.groovy} is missing or fails to
   * evaluate — matches the legacy INI behaviour of "no auto-load list, spawn at
   * world origin" so an unconfigured server still boots. The script-poll
   * default mirrors the historical {@code SCRIPT_POLL_INTERVAL_NANOS} = 5 s
   * Java constant so omitting the directive (or having no zone.groovy at all)
   * preserves the prior throttle. {@code repelFriendlies} defaults to
   * {@code true} (Subspace canon — repel is a movement effect, no FF gate).
   */
  public static final ZoneConfig EMPTY = new ZoneConfig(List.of(), "", 5.0, true);

  /** {@link #scriptPollIntervalSeconds} converted to nanoseconds for sim-tick comparisons. */
  public long scriptPollIntervalNanos() {
    return (long) (scriptPollIntervalSeconds * 1_000_000_000L);
  }
}
