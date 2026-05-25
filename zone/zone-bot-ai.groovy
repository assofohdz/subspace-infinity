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
}
