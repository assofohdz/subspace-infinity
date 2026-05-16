// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** What {@link infinity.config.ArenaConfig#modules()} carries; produced by GroovyArenaLoader. */
public record ArenaModuleDeclarations(
    Optional<ModuleSpec> teamSetup,
    Optional<ModuleSpec> roster,
    Optional<ModuleSpec> respawnPolicy,
    Optional<ModuleSpec> roundStructure,
    Optional<ModuleSpec> matchStructure,
    Optional<ModuleSpec> spawnPlacement,
    Optional<ModuleSpec> shop,
    List<ModuleSpec> scoring,
    List<ModuleSpec> winConditions,
    Map<String, ModuleSpec> mechanics) {

  public ArenaModuleDeclarations {
    scoring = List.copyOf(scoring);
    winConditions = List.copyOf(winConditions);
    mechanics = Map.copyOf(mechanics);
  }

  /** Every declared spec, flattened in registration order — single-pick, layered, then mechanics. */
  public List<ModuleSpec> allSpecs() {
    final List<ModuleSpec> all = new java.util.ArrayList<>();
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

  public static final ArenaModuleDeclarations EMPTY =
      new ArenaModuleDeclarations(
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

  public static final ArenaModuleDeclarations DEFAULTS = EMPTY;
}
