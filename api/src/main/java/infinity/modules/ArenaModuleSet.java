// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Per-arena module instances produced by {@code ModuleLoader.build}. {@code EMPTY} for legacy arenas. */
public record ArenaModuleSet(
    Optional<TeamSetupModule> teamSetup,
    Optional<RosterModule> roster,
    Optional<RespawnPolicyModule> respawnPolicy,
    Optional<RoundStructureModule> roundStructure,
    Optional<MatchStructureModule> matchStructure,
    Optional<SpawnPlacementModule> spawnPlacement,
    Optional<ShopModule> shop,
    List<ScoringModule> scoring,
    List<WinConditionModule> winConditions,
    Map<String, MechanicModule> mechanics) {

  public ArenaModuleSet {
    scoring = List.copyOf(scoring);
    winConditions = List.copyOf(winConditions);
    mechanics = Map.copyOf(mechanics);
  }

  public static final ArenaModuleSet EMPTY =
      new ArenaModuleSet(
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          Optional.empty(),
          List.of(),
          List.of(),
          Map.of());
}
