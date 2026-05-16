// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

/** Categorises arena modules per ADR-0008 composition shapes (single-pick / layered / mechanic). */
public enum ModuleCategory {
  TEAM_SETUP,
  ROSTER,
  RESPAWN_POLICY,
  ROUND_STRUCTURE,
  MATCH_STRUCTURE,
  SPAWN_PLACEMENT,
  SHOP,
  SCORING,
  WIN_CONDITION,
  MECHANIC
}
