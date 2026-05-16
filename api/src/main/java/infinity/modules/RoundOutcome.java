// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import java.util.Map;

/** Round-end snapshot passed to {@code ArenaModule.onRoundEnd}. {@code winningFreq == -1} = UNDECIDED. */
public record RoundOutcome(
    int winningFreq,
    String triggeringId,
    Map<Integer, Long> teamScores,
    Map<String, Object> details) {

  public RoundOutcome {
    teamScores = Map.copyOf(teamScores);
    details = Map.copyOf(details);
  }
}
