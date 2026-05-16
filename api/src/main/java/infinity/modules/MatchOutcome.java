// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import java.util.Map;

/** Match-end snapshot passed to {@code ArenaModule.onMatchEnd}. {@code winningFreq == -1} = UNDECIDED. */
public record MatchOutcome(
    int winningFreq, Map<Integer, Long> teamScores, Map<String, Object> details) {

  public MatchOutcome {
    teamScores = Map.copyOf(teamScores);
    details = Map.copyOf(details);
  }
}
