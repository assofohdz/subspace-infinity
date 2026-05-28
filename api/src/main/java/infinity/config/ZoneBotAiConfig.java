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
 *   <li>{@code navThreatWeight} — weight on the threat-descent (avoid) direction blended into the
 *       flow-field nav heading: {@code navDir + navThreatWeight·threatDescent}</li>
 *   <li>{@code navOpportunityWeight} — weight on the opportunity-ascent (seek) direction blended
 *       into the nav heading</li>
 *   <li>{@code flowFieldDebug} — dev-only: when {@code true}, the server samples the blended
 *       bot-steering flow around each player ship into a {@code FlowFieldDebug} component for the
 *       client overlay (off in production — costs a per-cadence sample + wire bandwidth)</li>
 *   <li>{@code flowFieldDebugRadius} — half-extent (tile cells) of the sampled flow patch around a
 *       player; the patch is {@code (2r+1)×(2r+1)} cells</li>
 *   <li>{@code engagementRangeUnits} — ADR-0016 {@code range_opt}: nominal optimal engage distance
 *       (world units) the {@code range_fit} input normalizes against. First-cut single value;
 *       per-weapon max range is sourced later by the snipe behaviour</li>
 *   <li>{@code bountyReference} — ADR-0016 {@code B_ref}: target bounty that saturates the
 *       {@code bounty_pull} input to {@code 1.0}</li>
 *   <li>{@code supportRadiusUnits} — ADR-0016 {@code R} for {@code support}: radius (world units)
 *       within which allies count toward the {@code clamp(allies/2)} support input</li>
 *   <li>{@code isolationReference} — ADR-0016 {@code iso_ref}: target-to-nearest-enemy distance
 *       (world units) that saturates the {@code isolation} input to {@code 1.0}</li>
 *   <li>{@code threatReference} — ADR-0016 {@code threat_ref}: blended enemy-threat field value that
 *       saturates {@code threat_density} (and bottoms out {@code approach_safety}) — i.e. roughly how
 *       many overlapping enemy weapon-coverage splats count as "fully dangerous"</li>
 *   <li>{@code lookAheadDistance} — reactive-steering forward look-ahead (world units) — used by
 *       both the brain's {@code AvoidObstacles} corridor probe and {@code PerceptionService}'s
 *       wall-ray cast. Consolidates the old duplicate {@code LOOK_AHEAD_DISTANCE}/{@code WALL_LOOK_AHEAD}
 *       Java constants</li>
 *   <li>{@code corridorHalfWidth} — half-width of the {@code AvoidObstacles} look-ahead corridor
 *       (world units). Wider = avoid earlier; narrower = squeeze through gaps</li>
 *   <li>{@code avoidThrust} — thrust magnitude when reactive avoid / wall-repel overrides BT steering</li>
 *   <li>{@code oversteerThrustFloor} — min thrust multiplier at max turn rate (empirical damp:
 *       prevents stall during sharp turns while reducing momentum overshoot)</li>
 *   <li>{@code wallRepulsionRadius} — omnidirectional grid wall-repulsion reach in tile cells
 *       (1 = adjacent only; 2 = also-touch diagonal). Tighter = fires only on real wall-grind,
 *       avoids hijacking flow on every adjacent block</li>
 *   <li>{@code wallObstacleRadius} — perception wall-ray hit "obstacle radius" (world units);
 *       feeds avoidance scoring</li>
 *   <li>{@code navGoalSnapRadius} — radius (tile cells) the async flow-field builder snaps a target
 *       goal cell into the nearest passable cell within</li>
 *   <li>{@code navHullFootprintCells} — Minkowski erosion footprint (tile cells) applied to the
 *       routing grid so the flow field treats wall-adjacent cells the bot's hull can't fit through
 *       as impassable. Increase for larger hulls</li>
 *   <li>{@code combatSplatRadius} — splat radius (tile cells) each fire event contributes to the
 *       combat heatmap; separate from {@code densityKernelRadius} so combat-pull can be tuned
 *       independently of allied/enemy positional density</li>
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
    double chokepointDensityWeight,
    double navThreatWeight,
    double navOpportunityWeight,
    boolean flowFieldDebug,
    int flowFieldDebugRadius,
    double engagementRangeUnits,
    double bountyReference,
    double supportRadiusUnits,
    double isolationReference,
    double threatReference,
    double lookAheadDistance,
    double corridorHalfWidth,
    double avoidThrust,
    double oversteerThrustFloor,
    int wallRepulsionRadius,
    double wallObstacleRadius,
    int navGoalSnapRadius,
    int navHullFootprintCells,
    int combatSplatRadius) {

  public static final ZoneBotAiConfig DEFAULTS =
      new ZoneBotAiConfig(
          150L, 0.10, 0.25, 0.05, 5000L, 16, 330L, 6, 20, 8, 0.85, 5, 4, 0.5, 0.6, 0.3, false, 10,
          20.0, 500.0, 12.0, 30.0, 2.0,
          5.0, 0.6, 1.0, 0.3, 2, 0.5, 24, 2, 6);

  public ZoneBotAiConfig() {
    this(
        150L, 0.10, 0.25, 0.05, 5000L, 16, 330L, 6, 20, 8, 0.85, 5, 4, 0.5, 0.6, 0.3, false, 10,
        20.0, 500.0, 12.0, 30.0, 2.0,
        5.0, 0.6, 1.0, 0.3, 2, 0.5, 24, 2, 6);
  }
}
