// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

/**
 * Kwargs for the {@code cooldown-respawn} {@code RespawnPolicyModule}. Configures the wallclock
 * delay (in seconds) between a player ship's death and the respawn-spawn of a fresh ship.
 * {@code 0} is degenerate (same observable behaviour as {@code instant-respawn}).
 */
public record CooldownRespawnConfig(int seconds) {

  public CooldownRespawnConfig {
    if (seconds < 0) {
      throw new IllegalArgumentException(
          "CooldownRespawnConfig.seconds must be >= 0; got " + seconds);
    }
  }

  public CooldownRespawnConfig() {
    this(0);
  }
}
