// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
//
// Engine-tier bot-AI synergy table (ADR-0014 / ADR-0016). ENGINEER-AUTHORED — zone admins
// do not edit this; per-arena tweaks go in arena.groovy `bots { tweak: [...] }`.
//
// One entry per catalogued behaviour. `requires` = capability eligibility gate (situational
// gates like line-of-sight / energy-threshold live in the behaviour's situationalFit, not here).
// `bonus` = capability-derived weight over the CapabilityProfile (method-call form: records).
// Coefficients are first-cut per docs/bot-ai/capability-derivation.md.

synergy {
    // --- Combat ---
    behaviour 'engage', {
        requires { profile -> profile.burstDamage() > 0 || profile.sustainedDamage() > 0 }
        bonus    { profile -> 0.3 * profile.sustainedDamage() + 0.2 * profile.mobility() }
    }
    behaviour 'snipe', {
        requires { profile -> profile.rangeProfile() > 0 }
        bonus    { profile -> 0.3 * profile.rangeProfile() + 0.25 * profile.burstDamage() }
    }
    behaviour 'assassinate', {
        requires { profile -> profile.burstDamage() > 0 }
        bonus    { profile -> 0.3 * profile.burstDamage() + 0.2 * profile.mobility()
                              + ((profile.cloak() || profile.stealth()) ? 0.2 : 0.0) }
    }
    behaviour 'ambush', {
        requires { profile -> profile.cloak() || profile.stealth() }
        bonus    { profile -> (profile.cloak() && profile.stealth()) ? 0.4 : 0.2 }
    }
    behaviour 'harass', {
        requires { profile -> profile.burstDamage() > 0 || profile.sustainedDamage() > 0 }
        bonus    { profile -> 0.3 * profile.mobility() + 0.25 * profile.rechargeEconomy() }
    }
    behaviour 'disengage', {
        // No capability gate (energy-threshold gate is situational). Mobility + escape tools.
        bonus    { profile -> 0.3 * profile.mobility()
                              + ((profile.maxRepels() > 0 || profile.maxPortals() > 0) ? 0.2 : 0.0) }
    }
    behaviour 'search', {
        bonus    { profile -> 0.3 * profile.mobility() + (profile.xRadar() ? 0.2 : 0.0) }
    }

    // --- Spatial / map control ---
    behaviour 'area-denial', {
        requires { profile -> profile.maxMines() > 0 || profile.areaDamage() > 0 }
        bonus    { profile -> 0.3 * profile.areaDamage() + (profile.maxMines() > 0 ? 0.2 : 0.0) }
    }
    behaviour 'hold-position', {
        // Flat base so EVERY hull carries some hold-position weight — any ship can sit on a flag.
        // Tankiness/antiwarp make durable hulls hold better, but the base keeps glass cannons above
        // the min-weight floor so the (multiplicative) turf objective bias can amplify them too:
        // in a flag arena all hulls bias toward the flag, not just tanky ones (ADR-0015). Inert in
        // arenas with no objective goal tiles — hold-position enumerates nothing there.
        bonus    { profile -> 0.3 + 0.3 * profile.tankiness() + (profile.antiwarp() ? 0.2 : 0.0) }
    }
    behaviour 'push', {
        bonus    { profile -> 0.3 * profile.mobility() + 0.2 * profile.tankiness() }
    }
    behaviour 'flank', {
        bonus    { profile -> 0.4 * profile.mobility() }
    }
    behaviour 'follow-traffic', {
        // Drift toward where the action is (combat/density × chokepoints, ADR-0012). No hard gate —
        // any ship can follow traffic; mobile hulls do it best, xRadar reads the action from afar.
        bonus    { profile -> 0.3 * profile.mobility() + (profile.xRadar() ? 0.15 : 0.0) }
    }
    behaviour 'choke', {
        requires { profile -> profile.antiwarp() || profile.maxPortals() > 0 }
        bonus    { profile -> (profile.antiwarp() ? 0.4 : 0.0) + (profile.maxPortals() > 0 ? 0.3 : 0.0) }
    }

    // --- Team / support ---
    behaviour 'escort', {
        bonus    { profile -> 0.3 * profile.mobility() + (profile.maxRepels() > 0 ? 0.2 : 0.0) }
    }
    behaviour 'regroup', {
        bonus    { profile -> 0.3 * profile.mobility() }
    }
    behaviour 'sweep', {
        bonus    { profile -> 0.4 * profile.tankiness() }
    }
    behaviour 'spawn-camp', {
        bonus    { profile -> 0.3 * profile.mobility() + 0.2 * profile.sustainedDamage() }
    }
    behaviour 'anchor', {
        requires { profile -> profile.attachReceive() } // placeholder false in v1 → not yet eligible
        bonus    { profile -> 0.4 * profile.tankiness() + 0.3 * profile.areaDamage() }
    }
    behaviour 'attach-to-anchor', {
        requires { profile -> profile.attachReceive() } // placeholder false in v1
        bonus    { profile -> 0.4 * (1.0 - profile.mobility()) } // slow hulls want delivery
    }
    behaviour 'repel-portal-support', {
        requires { profile -> profile.maxRepels() > 0 || profile.maxPortals() > 0 }
        bonus    { profile -> (profile.maxRepels() > 0 ? 0.3 : 0.0) + (profile.maxPortals() > 0 ? 0.3 : 0.0) }
    }
    behaviour 'spot-scout', {
        requires { profile -> profile.xRadar() }
        bonus    { profile -> 0.4 * profile.mobility() }
    }
    behaviour 'anti-stealth-hunt', {
        requires { profile -> profile.xRadar() || profile.antiwarp() }
        bonus    { profile -> (profile.xRadar() ? 0.4 : 0.0) + (profile.antiwarp() ? 0.3 : 0.0) }
    }

    // --- Objective (eligibility comes from ArenaObjective, ADR-0015; here = capability affinity only) ---
    behaviour 'capture-objective', {
        bonus    { profile -> 0.2 * profile.mobility() + 0.2 * profile.tankiness() }
    }
    behaviour 'defend-objective', {
        bonus    { profile -> 0.3 * profile.tankiness() + (profile.antiwarp() ? 0.2 : 0.0) }
    }
    behaviour 'deny-objective', {
        bonus    { profile -> 0.3 * profile.burstDamage() + 0.3 * profile.areaDamage() }
    }
    behaviour 'reposition', {
        bonus    { profile -> 0.2 * profile.mobility() }
    }

    // --- Self-preservation / meta (baseline floors; eligibility is situational/team, not capability) ---
    behaviour 'bait', {
        bonus    { profile -> 0.2 * profile.mobility() }
    }
    behaviour 'recharge', {
        bonus    { profile -> 0.1 } // universal floor; situational energy-threshold gate drives it
    }
    behaviour 'resupply', {
        bonus    { profile -> 0.1 } // situational (items depleted + shop)
    }
    behaviour 'communicate', {
        bonus    { profile -> profile.xRadar() ? 0.3 : 0.1 }
    }
    behaviour 'switch-ships', {
        // Tier-4 meta (event-driven, not planner-enumerated); baseline entry for completeness.
        bonus    { profile -> 0.1 }
    }
}
