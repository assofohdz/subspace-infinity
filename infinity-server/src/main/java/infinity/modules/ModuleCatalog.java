// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import infinity.config.CooldownRespawnConfig;
import infinity.config.CrownKillBonusConfig;
import infinity.config.CrownResetRoundStructureConfig;
import infinity.config.FillUpXTeamsConfig;
import infinity.config.FirstToXWinConditionConfig;
import infinity.config.FlagHoldTimeConfig;
import infinity.config.KillPointsConfig;
import infinity.config.RandomRadiusConfig;
import infinity.config.TimedRoundStructureConfig;
import infinity.modules.matchstructure.ContinuousMatchStructure;
import infinity.modules.mechanic.Crowns;
import infinity.modules.mechanic.FillUpXTeams;
import infinity.modules.respawn.CooldownRespawn;
import infinity.modules.respawn.InstantRespawn;
import infinity.modules.respawn.LockoutNoCrownRespawn;
import infinity.modules.roster.AllShipsRoster;
import infinity.modules.roundstructure.CrownResetRoundStructure;
import infinity.modules.roundstructure.TimedRoundStructure;
import infinity.modules.scoring.BonusPointsScoring;
import infinity.modules.scoring.CrownKillBonus;
import infinity.modules.scoring.FlagHoldTimeScoring;
import infinity.modules.scoring.KillPointsScoring;
import infinity.modules.shop.FlatShop;
import infinity.modules.spawnplacement.RandomRadiusSpawnPlacement;
import infinity.modules.teamsetup.FfaPrivateFreqsTeamSetup;
import infinity.modules.teamsetup.TwoFixedTeamsTeamSetup;
import infinity.modules.wincondition.FirstToXWinCondition;
import infinity.modules.wincondition.HighestScoreWinCondition;
import infinity.modules.wincondition.LastCrownStandingWinCondition;
import infinity.modules.wincondition.MostCrownsWinCondition;
import infinity.modules.wincondition.MostFlagOccupancyWinCondition;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Authored registry of available module types. Appended as concrete modules land. */
public final class ModuleCatalog {

  private static final Map<String, ModuleDescriptor> CATALOG =
      Map.ofEntries(
          Map.entry("kill-points",
              new ModuleDescriptor(
                  KillPointsScoring.class, KillPointsConfig.class, ModuleCategory.SCORING,
                  Set.of())),
          Map.entry("bonus-points",
              new ModuleDescriptor(
                  BonusPointsScoring.class, null, ModuleCategory.SCORING, Set.of())),
          Map.entry("flag-hold-time",
              new ModuleDescriptor(
                  FlagHoldTimeScoring.class, FlagHoldTimeConfig.class,
                  ModuleCategory.SCORING, Set.of())),
          Map.entry("crown-kill-bonus",
              new ModuleDescriptor(
                  CrownKillBonus.class, CrownKillBonusConfig.class,
                  ModuleCategory.SCORING, Set.of())),
          Map.entry("timed-round",
              new ModuleDescriptor(
                  TimedRoundStructure.class, TimedRoundStructureConfig.class,
                  ModuleCategory.ROUND_STRUCTURE, Set.of())),
          Map.entry("crown-reset",
              new ModuleDescriptor(
                  CrownResetRoundStructure.class, CrownResetRoundStructureConfig.class,
                  ModuleCategory.ROUND_STRUCTURE, Set.of())),
          Map.entry("continuous",
              new ModuleDescriptor(
                  ContinuousMatchStructure.class, null, ModuleCategory.MATCH_STRUCTURE, Set.of())),
          Map.entry("fill-up-x-teams",
              new ModuleDescriptor(
                  FillUpXTeams.class, FillUpXTeamsConfig.class, ModuleCategory.MECHANIC, Set.of())),
          Map.entry("crowns",
              new ModuleDescriptor(
                  Crowns.class, null, ModuleCategory.MECHANIC, Set.of())),
          Map.entry("highest-score",
              new ModuleDescriptor(
                  HighestScoreWinCondition.class, null, ModuleCategory.WIN_CONDITION, Set.of())),
          Map.entry("most-flag-occupancy",
              new ModuleDescriptor(
                  MostFlagOccupancyWinCondition.class, null,
                  ModuleCategory.WIN_CONDITION, Set.of())),
          Map.entry("last-crown-standing",
              new ModuleDescriptor(
                  LastCrownStandingWinCondition.class, null,
                  ModuleCategory.WIN_CONDITION, Set.of())),
          Map.entry("most-crowns",
              new ModuleDescriptor(
                  MostCrownsWinCondition.class, null,
                  ModuleCategory.WIN_CONDITION, Set.of())),
          Map.entry("first-to-x",
              new ModuleDescriptor(
                  FirstToXWinCondition.class, FirstToXWinConditionConfig.class,
                  ModuleCategory.WIN_CONDITION, Set.of())),
          Map.entry("all-ships",
              new ModuleDescriptor(
                  AllShipsRoster.class, null, ModuleCategory.ROSTER, Set.of())),
          Map.entry("random-radius",
              new ModuleDescriptor(
                  RandomRadiusSpawnPlacement.class, RandomRadiusConfig.class,
                  ModuleCategory.SPAWN_PLACEMENT, Set.of())),
          Map.entry("ffa-private-freqs",
              new ModuleDescriptor(
                  FfaPrivateFreqsTeamSetup.class, null, ModuleCategory.TEAM_SETUP, Set.of())),
          Map.entry("two-fixed-teams",
              new ModuleDescriptor(
                  TwoFixedTeamsTeamSetup.class, null, ModuleCategory.TEAM_SETUP, Set.of())),
          Map.entry("instant-respawn",
              new ModuleDescriptor(
                  InstantRespawn.class, null, ModuleCategory.RESPAWN_POLICY, Set.of())),
          Map.entry("cooldown-respawn",
              new ModuleDescriptor(
                  CooldownRespawn.class, CooldownRespawnConfig.class,
                  ModuleCategory.RESPAWN_POLICY, Set.of())),
          Map.entry("lockout-no-crown-respawn",
              new ModuleDescriptor(
                  LockoutNoCrownRespawn.class, null,
                  ModuleCategory.RESPAWN_POLICY, Set.of())),
          Map.entry("flat-shop",
              new ModuleDescriptor(
                  FlatShop.class, null, ModuleCategory.SHOP, Set.of())));

  private ModuleCatalog() {}

  /** {@code null} when {@code moduleId} is not registered. */
  @Nullable
  public static ModuleDescriptor descriptor(final String moduleId) {
    return CATALOG.get(moduleId);
  }

  public static Set<String> allIds() {
    return CATALOG.keySet();
  }

  /** Every {@code (id, descriptor)} entry — used by the cleanup-contract test to walk the catalog. */
  public static Set<Map.Entry<String, ModuleDescriptor>> allDescriptors() {
    return CATALOG.entrySet();
  }
}
