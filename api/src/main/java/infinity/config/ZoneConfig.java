// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import java.util.List;

/** Zone-scope server config; read by zone-startup, connect-time spawn, and the Groovy-script reload poller. */
public record ZoneConfig(
    List<String> autoLoadArenas,
    String enterSpawnArena,
    double scriptPollIntervalSeconds,
    boolean repelFriendlies) {

  /** Empty fallback used when {@code zone.groovy} is missing or fails to evaluate. */
  public static final ZoneConfig EMPTY = new ZoneConfig(List.of(), "", 5.0, true);

  /** {@link #scriptPollIntervalSeconds} converted to nanoseconds for sim-tick comparisons. */
  public long scriptPollIntervalNanos() {
    return (long) (scriptPollIntervalSeconds * 1_000_000_000L);
  }
}
