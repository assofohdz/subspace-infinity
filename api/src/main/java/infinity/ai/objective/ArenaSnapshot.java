// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.objective;

import com.simsilica.es.EntityId;

/**
 * Read-only view of arena state passed to {@link ArenaObjective#assignRole} (ADR-0015), keeping the
 * objective decoupled from {@code EntityData}. Deliberately minimal for v2.0 — just the bot's team.
 * Grows as gametypes need it (flag carriers, control-point holders, scores for CTF / Powerball / KOTH
 * role assignment); add accessors here rather than passing {@code EntityData} into objectives.
 */
public interface ArenaSnapshot {

  /** The frequency (team) the bot belongs to, or {@code -1} if unknown. */
  int teamFreq(EntityId bot);
}
