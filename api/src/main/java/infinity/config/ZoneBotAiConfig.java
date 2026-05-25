// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

/**
 * Zone-wide bot-AI tactical knobs (ADR-0013 / ADR-0014), loaded from {@code zone-bot-ai.groovy}.
 * Tier above arena/archetype; every bot in the zone reads the same values.
 *
 * <ul>
 *   <li>{@code plannerCadenceMillis} — min wall-time between {@code TacticalPlanner} re-selects
 *       (~150 for fast-combat zones; slow game modes override to 500+)</li>
 *   <li>{@code stickinessMargin} — additive margin a rival goal must beat the current goal by to
 *       preempt it; {@code [0,1]} weighted-score units</li>
 *   <li>{@code minFraction} — adaptive enumeration floor: a behaviour is enumerated only if its
 *       effective weight is at least this fraction of the bot's top behaviour weight</li>
 *   <li>{@code minBehaviourWeight} — absolute floor dropping near-zero synergy bonuses at
 *       weight-derivation time (ADR-0014)</li>
 *   <li>{@code navFieldTtlMs} — idle-timeout (ms) before a transient flow field is evicted from the
 *       per-arena nav cache (ADR-0011 #03); pinned static goals are exempt</li>
 *   <li>{@code navMaxFields} — LRU cap on transient flow fields per arena</li>
 * </ul>
 */
public record ZoneBotAiConfig(
    long plannerCadenceMillis,
    double stickinessMargin,
    double minFraction,
    double minBehaviourWeight,
    long navFieldTtlMs,
    int navMaxFields) {

  public static final ZoneBotAiConfig DEFAULTS =
      new ZoneBotAiConfig(150L, 0.10, 0.25, 0.05, 5000L, 16);

  public ZoneBotAiConfig() {
    this(150L, 0.10, 0.25, 0.05, 5000L, 16);
  }
}
