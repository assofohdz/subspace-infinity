// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import infinity.modules.ArenaModuleSystem.LoadedArena;
import infinity.sim.ArenaModule;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;

/**
 * Pure helpers for {@link ArenaModuleSystem#applyModuleSetDiff}: merge an old
 * {@link ArenaModuleSet} with new {@link ArenaModuleDeclarations} (retaining unchanged
 * instances, instantiating added specs) and locate a specific module instance by spec.
 */
final class ArenaModuleSetMerger {

  private ArenaModuleSetMerger() {}

  /** Merges {@code oldSet} forward to {@code newDecls}: retains specs that appear in both; instantiates new specs via {@link ModuleLoader#instantiate}. */
  static ArenaModuleSet mergeAndRebuild(
      final ArenaModuleSet oldSet,
      final ArenaModuleDeclarations oldDecls,
      final ArenaModuleDeclarations newDecls,
      final ModuleContext ctx) {
    return new ArenaModuleSet(
        retainOrBuildSingle(oldDecls.teamSetup(), oldSet.teamSetup(), newDecls.teamSetup(),
            TeamSetupModule.class, ctx),
        retainOrBuildSingle(oldDecls.roster(), oldSet.roster(), newDecls.roster(),
            RosterModule.class, ctx),
        retainOrBuildSingle(oldDecls.respawnPolicy(), oldSet.respawnPolicy(),
            newDecls.respawnPolicy(), RespawnPolicyModule.class, ctx),
        retainOrBuildSingle(oldDecls.roundStructure(), oldSet.roundStructure(),
            newDecls.roundStructure(), RoundStructureModule.class, ctx),
        retainOrBuildSingle(oldDecls.matchStructure(), oldSet.matchStructure(),
            newDecls.matchStructure(), MatchStructureModule.class, ctx),
        retainOrBuildSingle(oldDecls.spawnPlacement(), oldSet.spawnPlacement(),
            newDecls.spawnPlacement(), SpawnPlacementModule.class, ctx),
        retainOrBuildSingle(oldDecls.shop(), oldSet.shop(), newDecls.shop(),
            ShopModule.class, ctx),
        mergeLayered(oldDecls.scoring(), oldSet.scoring(), newDecls.scoring(),
            ScoringModule.class, ctx),
        mergeLayered(oldDecls.winConditions(), oldSet.winConditions(),
            newDecls.winConditions(), WinConditionModule.class, ctx),
        mergeMechanics(oldDecls.mechanics(), oldSet.mechanics(), newDecls.mechanics(), ctx));
  }

  static List<ArenaModule> findInstancesForSpecs(
      final LoadedArena state, final List<ModuleSpec> specs) {
    final List<ArenaModule> out = new ArrayList<>(specs.size());
    for (final ModuleSpec spec : specs) {
      final ArenaModule m = findInstanceForSpec(state, spec);
      if (m != null) {
        out.add(m);
      }
    }
    return out;
  }

  @Nullable
  static ArenaModule findInstanceForSpec(final LoadedArena state, final ModuleSpec spec) {
    final ArenaModule single = matchSingle(state.decls(), state.set(), spec);
    if (single != null) {
      return single;
    }
    final ArenaModule layered = matchLayered(state, spec);
    if (layered != null) {
      return layered;
    }
    return matchMechanics(state, spec);
  }

  /** Pair of (declaration spec, instance) for the seven single-pick categories. */
  private record SinglePickSlot(Optional<ModuleSpec> decl, Optional<? extends ArenaModule> instance) {}

  /** Walks the single-pick slots; returns the instance whose decl-spec equals {@code spec}. */
  @Nullable
  private static ArenaModule matchSingle(
      final ArenaModuleDeclarations d, final ArenaModuleSet s, final ModuleSpec spec) {
    final List<SinglePickSlot> slots = List.of(
        new SinglePickSlot(d.teamSetup(), s.teamSetup()),
        new SinglePickSlot(d.roster(), s.roster()),
        new SinglePickSlot(d.respawnPolicy(), s.respawnPolicy()),
        new SinglePickSlot(d.roundStructure(), s.roundStructure()),
        new SinglePickSlot(d.matchStructure(), s.matchStructure()),
        new SinglePickSlot(d.spawnPlacement(), s.spawnPlacement()),
        new SinglePickSlot(d.shop(), s.shop()));
    for (final SinglePickSlot slot : slots) {
      if (slot.decl().isPresent() && slot.decl().get().equals(spec)) {
        return slot.instance().orElse(null);
      }
    }
    return null;
  }

  @Nullable
  private static ArenaModule matchLayered(final LoadedArena state, final ModuleSpec spec) {
    final ArenaModule scoring = matchByIndex(
        state.decls().scoring(), state.set().scoring(), spec);
    if (scoring != null) {
      return scoring;
    }
    return matchByIndex(state.decls().winConditions(), state.set().winConditions(), spec);
  }

  @Nullable
  private static ArenaModule matchByIndex(
      final List<ModuleSpec> specs, final List<? extends ArenaModule> instances, final ModuleSpec spec) {
    for (int i = 0; i < specs.size(); i++) {
      if (specs.get(i).equals(spec) && i < instances.size()) {
        return instances.get(i);
      }
    }
    return null;
  }

  @Nullable
  private static ArenaModule matchMechanics(final LoadedArena state, final ModuleSpec spec) {
    for (final Map.Entry<String, ModuleSpec> e : state.decls().mechanics().entrySet()) {
      if (e.getValue().equals(spec)) {
        return state.set().mechanics().get(e.getKey());
      }
    }
    return null;
  }

  private static <M extends ArenaModule> Optional<M> retainOrBuildSingle(
      final Optional<ModuleSpec> oldSpec,
      final Optional<M> oldInstance,
      final Optional<ModuleSpec> newSpec,
      final Class<M> moduleIface,
      final ModuleContext ctx) {
    if (newSpec.isEmpty()) {
      return Optional.empty();
    }
    if (oldSpec.isPresent() && oldSpec.get().equals(newSpec.get()) && oldInstance.isPresent()) {
      return oldInstance;
    }
    return Optional.of(moduleIface.cast(ModuleLoader.instantiate(newSpec.get(), ctx)));
  }

  /** Walks {@code newSpecs}, consuming matching old instances in order; builds new for unmatched. */
  private static <M extends ArenaModule> List<M> mergeLayered(
      final List<ModuleSpec> oldSpecs,
      final List<M> oldInstances,
      final List<ModuleSpec> newSpecs,
      final Class<M> moduleIface,
      final ModuleContext ctx) {
    final List<Integer> available = new ArrayList<>();
    for (int i = 0; i < oldSpecs.size(); i++) {
      available.add(i);
    }
    final List<M> result = new ArrayList<>(newSpecs.size());
    for (final ModuleSpec spec : newSpecs) {
      final int slot = findSlot(oldSpecs, available, spec);
      if (slot >= 0) {
        result.add(oldInstances.get(available.remove(slot)));
      } else {
        result.add(moduleIface.cast(ModuleLoader.instantiate(spec, ctx)));
      }
    }
    return result;
  }

  private static int findSlot(
      final List<ModuleSpec> oldSpecs, final List<Integer> available, final ModuleSpec spec) {
    for (int i = 0; i < available.size(); i++) {
      if (oldSpecs.get(available.get(i)).equals(spec)) {
        return i;
      }
    }
    return -1;
  }

  private static Map<String, MechanicModule> mergeMechanics(
      final Map<String, ModuleSpec> oldSpecs,
      final Map<String, MechanicModule> oldInstances,
      final Map<String, ModuleSpec> newSpecs,
      final ModuleContext ctx) {
    final Map<String, MechanicModule> result = new LinkedHashMap<>(newSpecs.size());
    for (final Map.Entry<String, ModuleSpec> entry : newSpecs.entrySet()) {
      final String key = entry.getKey();
      final ModuleSpec spec = entry.getValue();
      final MechanicModule retained = oldInstances.get(key);
      if (retained != null && spec.equals(oldSpecs.get(key))) {
        result.put(key, retained);
      } else {
        result.put(key, (MechanicModule) ModuleLoader.instantiate(spec, ctx));
      }
    }
    return result;
  }
}
