// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Per-tick debug snapshot of a bot's brain state, written by {@code BotBrainSystem} for
 * client-side HUD consumption. Wire-crossing — see {@code components.md}. Non-record
 * because jME3 {@code FieldSerializer} can't reflectively set record components.
 *
 * <p>v1 fields: {@link #targetId()} is {@code -1} when the bot has no pursue target, and
 * {@link #clockHour()} is {@code 0} in the same case; otherwise {@code clockHour} is
 * {@code 1..12} where {@code 12} = target straight ahead, {@code 3} = 90° right,
 * {@code 6} = behind, {@code 9} = 90° left.
 *
 * <p>v2 fields (slice #08) surface the tactical layer: {@link #currentGoalLabel()} (planner
 * goal), {@link #topScores()} + {@link #weightBreakdown()} (capability-derived behaviour
 * weights), {@link #navMode()} (reactive vs flow-field). {@link #objectiveName()} +
 * {@link #roleName()} are populated once arena objectives/roles land (#06); empty until then.
 */
public final class BotDebug implements EntityComponent {

  private final String branch;
  private final long targetId;
  private final double intentTurn;
  private final double intentThrust;
  private final int clockHour;
  private final String objectiveName;
  private final String roleName;
  private final String currentGoalLabel;
  private final String topScores;
  private final String weightBreakdown;
  private final String navMode;

  public BotDebug() {
    this("", -1L, 0.0, 0.0, 0, "", "", "", "", "", "");
  }

  // Wire-crossing data snapshot: the param list mirrors the serialized field set, so a
  // builder/param-object would just shadow FieldSerializer's reflective field access.
  @SuppressWarnings("PMD.ExcessiveParameterList")
  public BotDebug(
      final String branch,
      final long targetId,
      final double intentTurn,
      final double intentThrust,
      final int clockHour,
      final String objectiveName,
      final String roleName,
      final String currentGoalLabel,
      final String topScores,
      final String weightBreakdown,
      final String navMode) {
    this.branch = branch;
    this.targetId = targetId;
    this.intentTurn = intentTurn;
    this.intentThrust = intentThrust;
    this.clockHour = clockHour;
    this.objectiveName = objectiveName;
    this.roleName = roleName;
    this.currentGoalLabel = currentGoalLabel;
    this.topScores = topScores;
    this.weightBreakdown = weightBreakdown;
    this.navMode = navMode;
  }

  public String branch() {
    return this.branch;
  }

  public long targetId() {
    return this.targetId;
  }

  public double intentTurn() {
    return this.intentTurn;
  }

  public double intentThrust() {
    return this.intentThrust;
  }

  public int clockHour() {
    return this.clockHour;
  }

  /** Arena objective name (e.g. {@code koth}); empty until objectives land (#06). */
  public String objectiveName() {
    return this.objectiveName;
  }

  /** Per-bot role name (e.g. {@code koth-holder}); empty until roles land (#06). */
  public String roleName() {
    return this.roleName;
  }

  /** Planner's current goal as a compact label (e.g. {@code Engage(42)}, {@code Search}). */
  public String currentGoalLabel() {
    return this.currentGoalLabel;
  }

  /** Top-N {@code behaviour=effectiveWeight} pairs from the derived archetype, descending. */
  public String topScores() {
    return this.topScores;
  }

  /** Factor breakdown for the top behaviour (capability today; × objective × role with #06). */
  public String weightBreakdown() {
    return this.weightBreakdown;
  }

  /** Steering substrate: {@code reactive} (BT/steering) or {@code flow} (flow-field nav). */
  public String navMode() {
    return this.navMode;
  }
}
