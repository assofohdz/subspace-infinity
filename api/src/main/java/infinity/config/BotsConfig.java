// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

import infinity.Ship;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-arena {@code bots { }} block (ADR-0010 as amended by ADR-0014): which ship hulls spawn as bots
 * and their optional per-ship behaviour-weight {@code tweak} overlays. Absent / empty ⇒ the spawner
 * falls back to its default hull and every bot's behaviour is pure capability-derived (slice #04).
 */
public record BotsConfig(List<BotShipConfig> ships) {

  public static final BotsConfig DEFAULTS = new BotsConfig(List.of());

  public BotsConfig {
    ships = List.copyOf(ships);
  }

  public BotsConfig() {
    this(List.of());
  }

  public boolean isEmpty() {
    return ships.isEmpty();
  }

  /** Flatten to the per-freq spawn order: each entry repeated {@code count} times, block order. */
  public List<Ship> expandedRoster() {
    final List<Ship> roster = new ArrayList<>();
    for (final BotShipConfig entry : ships) {
      for (int i = 0; i < entry.count(); i++) {
        roster.add(entry.ship());
      }
    }
    return roster;
  }

  /** Tweak overlay for {@code ship} (first matching entry); empty when the ship isn't listed. */
  public List<BehaviourTweak> tweakFor(final Ship ship) {
    for (final BotShipConfig entry : ships) {
      if (entry.ship() == ship) {
        return entry.tweak();
      }
    }
    return List.of();
  }
}
