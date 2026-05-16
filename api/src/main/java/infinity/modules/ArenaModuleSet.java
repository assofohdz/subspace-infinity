// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import infinity.sim.ArenaModule;
import java.util.ArrayList;
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

  /** Every loaded module, in registration order — single-pick, layered, then mechanics. */
  public List<ArenaModule> allModules() {
    final List<ArenaModule> all = new ArrayList<>();
    teamSetup.ifPresent(all::add);
    roster.ifPresent(all::add);
    respawnPolicy.ifPresent(all::add);
    roundStructure.ifPresent(all::add);
    matchStructure.ifPresent(all::add);
    spawnPlacement.ifPresent(all::add);
    shop.ifPresent(all::add);
    all.addAll(scoring);
    all.addAll(winConditions);
    all.addAll(mechanics.values());
    return List.copyOf(all);
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
