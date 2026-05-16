// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import infinity.config.FirstToXWinConditionConfig;
import infinity.config.KillPointsConfig;
import infinity.config.TimedRoundStructureConfig;
import infinity.modules.matchstructure.ContinuousMatchStructure;
import infinity.modules.roundstructure.TimedRoundStructure;
import infinity.modules.scoring.KillPointsScoring;
import infinity.modules.wincondition.FirstToXWinCondition;
import infinity.modules.wincondition.HighestScoreWinCondition;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Authored registry of available module types. Appended as concrete modules land. */
public final class ModuleCatalog {

  private static final Map<String, ModuleDescriptor> CATALOG =
      Map.of(
          "kill-points",
          new ModuleDescriptor(
              KillPointsScoring.class, KillPointsConfig.class, ModuleCategory.SCORING, Set.of()),
          "timed-round",
          new ModuleDescriptor(
              TimedRoundStructure.class,
              TimedRoundStructureConfig.class,
              ModuleCategory.ROUND_STRUCTURE,
              Set.of()),
          "continuous",
          new ModuleDescriptor(
              ContinuousMatchStructure.class,
              null, // zero-config
              ModuleCategory.MATCH_STRUCTURE,
              Set.of()),
          "highest-score",
          new ModuleDescriptor(
              HighestScoreWinCondition.class,
              null, // zero-config — no kwargs accepted at validate time
              ModuleCategory.WIN_CONDITION,
              Set.of()),
          "first-to-x",
          new ModuleDescriptor(
              FirstToXWinCondition.class,
              FirstToXWinConditionConfig.class,
              ModuleCategory.WIN_CONDITION,
              Set.of()));

  private ModuleCatalog() {}

  /** {@code null} when {@code moduleId} is not registered. */
  @Nullable
  public static ModuleDescriptor descriptor(final String moduleId) {
    return CATALOG.get(moduleId);
  }

  public static Set<String> allIds() {
    return CATALOG.keySet();
  }
}
