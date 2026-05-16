// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Two-phase loader per ADR-0008 / PRD: {@code validate} is pure; {@code build}
 * runs only after a clean validate. F1 ships with an empty {@code ModuleCatalog},
 * so {@code build} always returns {@link ArenaModuleSet#EMPTY}; concrete module
 * instantiation lands with the first catalog entry in F2+.
 */
public final class ModuleLoader {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ModuleLoader() {}

  /** Pure phase-1 checks. No side effects. No instantiation. */
  public static ValidationResult validate(final ArenaModuleDeclarations decls) {
    final List<ModuleSpec> allSpecs = decls.allSpecs();
    final List<String> errors = new ArrayList<>();

    // Check 1: every moduleId is registered in the catalog.
    for (final ModuleSpec spec : allSpecs) {
      if (ModuleCatalog.descriptor(spec.moduleId()) == null) {
        errors.add("Unknown module id '" + spec.moduleId() + "'");
      }
    }
    // Subsequent checks need resolved descriptors; bail if any id failed.
    if (!errors.isEmpty()) {
      return new ValidationResult(errors);
    }

    // Check 2: every `requires:` mechanic dep is satisfied by a loaded mechanic.
    final Set<String> loadedMechanics = decls.mechanics().keySet();
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

    // Check 3: mechanic `requires:` graph is acyclic.
    // Skeleton — F2+ when first mechanic with requires lands.

    // Check 4: kwargs bind cleanly (Jackson convertValue invokes the record's
    // canonical constructor; compact-constructor validation throws IAE).
    for (final ModuleSpec spec : allSpecs) {
      final ModuleDescriptor desc = ModuleCatalog.descriptor(spec.moduleId());
      try {
        MAPPER.convertValue(spec.kwargs(), desc.configType());
      } catch (final IllegalArgumentException e) {
        errors.add("Invalid config for '" + spec.moduleId() + "': " + e.getMessage());
      }
    }

    return new ValidationResult(errors);
  }

  /**
   * Phase-2 build. Caller must have called {@link #validate} and received an OK
   * result. F1: catalog is empty, so the only valid input has no declarations;
   * returns {@link ArenaModuleSet#EMPTY}. F2+ replaces this body with real
   * instantiation per the descriptor's moduleClass + configType.
   */
  public static ArenaModuleSet build(
      final ArenaModuleDeclarations decls, final ModuleContext context) {
    if (decls.equals(ArenaModuleDeclarations.EMPTY)) {
      return ArenaModuleSet.EMPTY;
    }
    throw new IllegalStateException(
        "ModuleLoader.build with non-empty declarations is F2+ work; F1 catalog is empty");
  }
}
