// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// Zone-tier bot-AI tactical knobs (ADR-0013 / ADR-0014). Zone-admin-editable — applies to every
// bot in the zone regardless of arena. Per-arena nudges go in arena.groovy `bots { tweak: [...] }`;
// the engine-authored synergy table lives in engine-bot-ai.groovy (do not edit that as an admin).

botAi {
    // Min wall-time between TacticalPlanner re-selects. ~150ms suits fast-combat zones (Subspace
    // TTK is 1-3s); slow game modes (Hockey, Powerball, KOTH) can raise to 500+.
    plannerCadenceMillis 150

    // A rival goal must beat the running goal's weighted score by this additive margin to preempt
    // it — kills mid-action oscillation. Lower = more reactive; higher = more committed.
    stickinessMargin 0.10

    // A behaviour is enumerated only if its derived weight is at least this fraction of the bot's
    // top behaviour weight (adaptive, self-calibrating per bot per planner tick).
    minFraction 0.25

    // Absolute floor dropping near-zero synergy bonuses when weights are derived.
    minBehaviourWeight 0.05

    // Flow-field nav cache (ADR-0011): idle TTL before a transient field is evicted, and the
    // LRU cap on transient fields per arena. Static (pinned) goal fields are exempt from both.
    navFieldTtlMs 5000
    navMaxFields  16

    // Per-team density scalar fields (ADR-0012): how often they rebuild from live ship positions,
    // and the per-ship splat radius (tile cells, linear falloff). Wider radius = smoother gradient
    // for "drift toward the action" but more cells touched per ship.
    densityCadenceMillis 330
    densityKernelRadius  6

    // Threat field (ADR-0012): weapon-range falloff radius (tile cells) each ship adds to its
    // team's threat surface, line-of-sight gated so walls give cover. Approximates per-weapon range.
    // Opportunity field: per-prize splat radius for the "bend toward pickups" gradient.
    threatRadius      20
    opportunityRadius 8

    // Chokepoints (ADR-0012) = narrow space to pass × traffic heatmap. Geometry: a cell whose
    // corridor is <= chokepointMaxWidth cells wide. Hotness ranking combines that pinch with the
    // combat (fire) heatmap + position density: pinch × (1 + combat + chokepointDensityWeight·density).
    // combatDecayPerCadence fades the fire heatmap each density cadence. chokepointTopN tiles are
    // pinned as static nav goals per arena.
    combatDecayPerCadence   0.85
    chokepointTopN          5
    chokepointMaxWidth      4
    chokepointDensityWeight 0.5

    // Blended-gradient nav (ADR-0012): the flow-field heading toward a goal is bent away from
    // incoming threat (enemy weapon-range, LoS-gated) and toward opportunity (prizes).
    // heading = navDir + navThreatWeight·threatDescent + navOpportunityWeight·opportunityAscent.
    navThreatWeight      0.6
    navOpportunityWeight 0.3

    // ADR-0016 situational-input normalization refs. range_fit peaks at engagementRangeUnits distance
    // (world units); bounty_pull saturates at bountyReference; support counts allies within
    // supportRadiusUnits (world units). engagementRangeUnits is a first-cut single value — the snipe
    // behaviour sources per-weapon max range later.
    engagementRangeUnits 20.0
    bountyReference      500.0
    supportRadiusUnits   12.0
    // isolationReference: a target this far (world units) from its nearest teammate reads as fully
    // isolated (assassinate). threatReference: blended enemy weapon-coverage value that reads as
    // fully dangerous (approach_safety / threat_density), ~2 overlapping enemy splats.
    isolationReference   30.0
    threatReference      2.0

    // Dev-only: visualise the blended bot-steering flow around each player ship as a client overlay
    // (toggle in-game with the flow-field debug key). Off by default — when on, the server samples a
    // (2·radius+1)² patch of flow vectors around every player each density cadence and syncs it.
    flowFieldDebug       true
    flowFieldDebugRadius 10

    // Reactive-steering knobs (v3 #02.B+C). lookAheadDistance is shared between the brain's
    // AvoidObstacles forward corridor and PerceptionService's wall-ray cast (world units). Wider
    // corridorHalfWidth = avoid earlier; narrower = squeeze through gaps. avoidThrust feeds both
    // AvoidObstacles + WallRepulsion when they override BT steering. oversteerThrustFloor (0..1)
    // floors the sharp-turn thrust damp so the bot doesn't stall mid-pivot.
    lookAheadDistance      5.0
    corridorHalfWidth      0.6
    avoidThrust            1.0
    oversteerThrustFloor   0.3
    wallRepulsionRadius    2
    wallObstacleRadius     0.5

    // Flow-field nav (v3 #02.D). goalSnapRadius lets a target in a non-navigable slot route to the
    // nearest passable cell within. hullFootprintCells is the Minkowski erosion applied to the
    // routing grid so the diameter-2 hull only routes where it fits. combatSplatRadius is distinct
    // from densityKernelRadius (above) so the fire-heatmap can be tuned independently.
    navGoalSnapRadius      24
    navHullFootprintCells  2
    combatSplatRadius      6

    // Behaviour-fit floors (v3 #02.E). search/hold-position/follow-traffic each score with a
    // ternary: dominant when no target is in view (idle), negligible/yielding when targeting
    // (engaged). Vestigial — these behaviours should migrate to the convex-sum situationalFit
    // in engine-bot-ai.groovy per ADR-0016. Until then, tune via these knobs.
    searchIdleFit              0.6
    searchEngagedFit           0.1
    holdPositionIdleFit        0.55
    holdPositionEngagedFit     0.15
    followTrafficIdleFit       0.5
    followTrafficEngagedFit    0.2
    // Distance falloff scale (tile cells) for FollowTraffic re-scoring of hot tiles:
    // score = hotness / (1 + dist/decay). Larger = far hot tiles stay competitive.
    followTrafficDistDecayCells 200.0

    // Deep steer-action knobs (v3 B12). steerGoalBlockCells coarsens moving-target goal cells so
    // SteerApproachTarget reuses one flow field while the target stays within the block.
    // steerArrivalRadiusCells tapers SteerToGoalTile thrust within N cells of the goal so the bot
    // settles. steerWallAvoidWeight is the wall-clearance push blended into the flow heading
    // (small — flow wins at chokepoints). seekForwardThrustFloor is the SeekDirection floor
    // keeping the bot arcing through turns instead of spinning in place. wallRepulsionMinPush
    // is the escape-of-last-resort threshold below which WallRepulsion returns no push.
    steerGoalBlockCells       16
    steerArrivalRadiusCells   6.0
    steerWallAvoidWeight      0.3
    seekForwardThrustFloor    0.4
    wallRepulsionMinPush      0.5
}
