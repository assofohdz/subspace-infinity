// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import infinity.sim.ArenaModule;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Two-phase loader per ADR-0008 / PRD: {@code validate} is pure; {@code build}
 * runs only after a clean validate. Each module class declares a single public
 * {@code (ModuleContext, *Config)} constructor; the loader binds kwargs via
 * Jackson and reflects to instantiate.
 */
public final class ModuleLoader {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ModuleLoader() {}

  /** Pure phase-1 checks. No side effects. No instantiation. */
  public static ValidationResult validate(final ArenaModuleDeclarations decls) {
    final List<ModuleSpec> allSpecs = decls.allSpecs();
    final List<String> errors = new ArrayList<>();

    checkUnknownIds(allSpecs, errors);
    if (!errors.isEmpty()) {
      // Subsequent checks need resolved descriptors; bail if any id failed.
      return new ValidationResult(errors);
    }
    checkRequiresSatisfied(allSpecs, decls.mechanics().keySet(), errors);
    // Check: mechanic `requires:` graph is acyclic — skeleton; lands with first
    // mechanic that has requires.
    checkKwargsBind(allSpecs, errors);

    return new ValidationResult(errors);
  }

  /** Check 1: every moduleId is registered in the catalog. */
  private static void checkUnknownIds(
      final List<ModuleSpec> allSpecs, final List<String> errors) {
    for (final ModuleSpec spec : allSpecs) {
      if (ModuleCatalog.descriptor(spec.moduleId()) == null) {
        errors.add("Unknown module id '" + spec.moduleId() + "'");
      }
    }
  }

  /** Check 2: every {@code requires:} mechanic dep is satisfied by a loaded mechanic. */
  private static void checkRequiresSatisfied(
      final List<ModuleSpec> allSpecs,
      final Set<String> loadedMechanics,
      final List<String> errors) {
    for (final ModuleSpec spec : allSpecs) {
      final ModuleDescriptor desc = ModuleCatalog.descriptor(spec.moduleId());
      for (final String required : desc.requires()) {
        if (!loadedMechanics.contains(required)) {
          errors.add(
              "Module '"
                  + spec.moduleId()
                  + "' requires mechanic '"
                  + required
                  + "' which is not loaded");
        }
      }
    }
  }

  /**
   * Check: kwargs bind cleanly via Jackson + record compact-constructor validation.
   * Zero-config modules ({@code configType == null}) reject any kwargs loudly.
   */
  private static void checkKwargsBind(
      final List<ModuleSpec> allSpecs, final List<String> errors) {
    for (final ModuleSpec spec : allSpecs) {
      final ModuleDescriptor desc = ModuleCatalog.descriptor(spec.moduleId());
      if (desc.configType() == null) {
        if (!spec.kwargs().isEmpty()) {
          errors.add(
              "Module '" + spec.moduleId() + "' takes no config; unexpected kwargs "
                  + spec.kwargs().keySet());
        }
        continue;
      }
      try {
        MAPPER.convertValue(spec.kwargs(), desc.configType());
      } catch (final IllegalArgumentException e) {
        errors.add("Invalid config for '" + spec.moduleId() + "': " + e.getMessage());
      }
    }
  }

  /**
   * Phase-2 build. Caller must have called {@link #validate} and received an OK
   * result; this method assumes validity and instantiates each module via its
   * single {@code (ModuleContext, *Config)} constructor.
   */
  public static ArenaModuleSet build(
      final ArenaModuleDeclarations decls, final ModuleContext context) {
    if (decls.equals(ArenaModuleDeclarations.EMPTY)) {
      return ArenaModuleSet.EMPTY;
    }
    final List<ScoringModule> scoring = new ArrayList<>();
    final List<WinConditionModule> winConditions = new ArrayList<>();
    final Map<String, MechanicModule> mechanics = new LinkedHashMap<>();
    for (final ModuleSpec spec : decls.scoring()) {
      scoring.add((ScoringModule) instantiate(spec, context));
    }
    for (final ModuleSpec spec : decls.winConditions()) {
      winConditions.add((WinConditionModule) instantiate(spec, context));
    }
    for (final Map.Entry<String, ModuleSpec> entry : decls.mechanics().entrySet()) {
      mechanics.put(entry.getKey(), (MechanicModule) instantiate(entry.getValue(), context));
    }
    return new ArenaModuleSet(
        decls.teamSetup().map(s -> (TeamSetupModule) instantiate(s, context)),
        decls.roster().map(s -> (RosterModule) instantiate(s, context)),
        decls.respawnPolicy().map(s -> (RespawnPolicyModule) instantiate(s, context)),
        decls.roundStructure().map(s -> (RoundStructureModule) instantiate(s, context)),
        decls.matchStructure().map(s -> (MatchStructureModule) instantiate(s, context)),
        decls.spawnPlacement().map(s -> (SpawnPlacementModule) instantiate(s, context)),
        decls.shop().map(s -> (ShopModule) instantiate(s, context)),
        scoring,
        winConditions,
        mechanics);
  }

  private static ArenaModule instantiate(final ModuleSpec spec, final ModuleContext context) {
    final ModuleDescriptor desc = ModuleCatalog.descriptor(spec.moduleId());
    if (desc.configType() == null) {
      return instantiateZeroConfig(desc, spec, context);
    }
    return instantiateWithConfig(desc, spec, context);
  }

  private static ArenaModule instantiateZeroConfig(
      final ModuleDescriptor desc, final ModuleSpec spec, final ModuleContext context) {
    try {
      final Constructor<? extends ArenaModule> ctor =
          desc.moduleClass().getDeclaredConstructor(ModuleContext.class);
      return ctor.newInstance(context);
    } catch (final NoSuchMethodException e) {
      throw new IllegalStateException(
          "Zero-config module class "
              + desc.moduleClass().getName()
              + " must declare a public (ModuleContext) constructor",
          e);
    } catch (final InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(
          "Failed to instantiate module '" + spec.moduleId() + "'", e);
    }
  }

  private static ArenaModule instantiateWithConfig(
      final ModuleDescriptor desc, final ModuleSpec spec, final ModuleContext context) {
    final Object config = MAPPER.convertValue(spec.kwargs(), desc.configType());
    try {
      final Constructor<? extends ArenaModule> ctor =
          desc.moduleClass().getDeclaredConstructor(ModuleContext.class, desc.configType());
      return ctor.newInstance(context, config);
    } catch (final NoSuchMethodException e) {
      throw new IllegalStateException(
          "Module class "
              + desc.moduleClass().getName()
              + " must declare a public (ModuleContext, "
              + desc.configType().getSimpleName()
              + ") constructor",
          e);
    } catch (final InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new IllegalStateException(
          "Failed to instantiate module '" + spec.moduleId() + "'", e);
    }
  }
}
