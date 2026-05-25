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
}
