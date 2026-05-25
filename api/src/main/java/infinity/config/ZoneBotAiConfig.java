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
 *   <li>{@code densityCadenceMillis} — min wall-time between rebuilds of the per-team density
 *       scalar fields (ADR-0012); ~330ms (every ~10 ticks at 30Hz)</li>
 *   <li>{@code densityKernelRadius} — splat radius (tile cells) each ship contributes to its team's
 *       density field with linear falloff; wider = smoother gradient, more cells touched</li>
 *   <li>{@code threatRadius} — weapon-range falloff radius (tile cells) each ship adds to its team's
 *       threat field; line-of-sight gated (walls block). Approximates per-weapon Subspace range</li>
 *   <li>{@code opportunityRadius} — splat radius (tile cells) each prize adds to the arena
 *       opportunity field with linear falloff</li>
 *   <li>{@code combatDecayPerCadence} — multiplicative fade ({@code (0,1)}) applied to the combat
 *       (fire) heatmap each density cadence; lower = heat fades faster</li>
 *   <li>{@code chokepointTopN} — number of geometric chokepoint tiles pinned as static nav goals
 *       per arena (ADR-0012)</li>
 *   <li>{@code chokepointMaxWidth} — max corridor width (tile cells) for a cell to count as a
 *       chokepoint pinch</li>
 *   <li>{@code chokepointDensityWeight} — weight {@code k} on position-density in the chokepoint
 *       hotness combo {@code pinch × (1 + combat + k·totalDensity)}</li>
 * </ul>
 */
public record ZoneBotAiConfig(
    long plannerCadenceMillis,
    double stickinessMargin,
    double minFraction,
    double minBehaviourWeight,
    long navFieldTtlMs,
    int navMaxFields,
    long densityCadenceMillis,
    int densityKernelRadius,
    int threatRadius,
    int opportunityRadius,
    double combatDecayPerCadence,
    int chokepointTopN,
    int chokepointMaxWidth,
    double chokepointDensityWeight) {

  public static final ZoneBotAiConfig DEFAULTS =
      new ZoneBotAiConfig(150L, 0.10, 0.25, 0.05, 5000L, 16, 330L, 6, 20, 8, 0.85, 5, 4, 0.5);

  public ZoneBotAiConfig() {
    this(150L, 0.10, 0.25, 0.05, 5000L, 16, 330L, 6, 20, 8, 0.85, 5, 4, 0.5);
  }
}
