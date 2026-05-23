// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;

/**
 * Per-tick debug snapshot of a bot's brain state, written by {@code BotBrainSystem} for
 * client-side HUD consumption. Wire-crossing — see {@code components.md}. Non-record
 * because jME3 {@code FieldSerializer} can't reflectively set record components.
 * The {@link #targetId()} value is {@code -1} when the bot has no pursue target, and
 * {@link #clockHour()} is {@code 0} in the same case; otherwise {@code clockHour} is
 * {@code 1..12} where {@code 12} = target straight ahead, {@code 3} = 90° right,
 * {@code 6} = behind, {@code 9} = 90° left.
 */
public final class BotDebug implements EntityComponent {

  private final String branch;
  private final long targetId;
  private final double intentTurn;
  private final double intentThrust;
  private final int clockHour;

  public BotDebug() {
    this("", -1L, 0.0, 0.0, 0);
  }

  public BotDebug(
      final String branch,
      final long targetId,
      final double intentTurn,
      final double intentThrust,
      final int clockHour) {
    this.branch = branch;
    this.targetId = targetId;
    this.intentTurn = intentTurn;
    this.intentThrust = intentThrust;
    this.clockHour = clockHour;
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
}
