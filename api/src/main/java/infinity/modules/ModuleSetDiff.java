// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import java.util.ArrayList;
import java.util.List;

/**
 * Symmetric diff between two {@link ArenaModuleDeclarations} snapshots. Specs match on
 * full record equality (moduleId + kwargs) — reconfiguring a module via different kwargs
 * shows up as the old spec in {@link #removedSpecs} and the new spec in {@link #addedSpecs},
 * which makes the hot-reload path treat it uniformly as teardown + rebuild until any module
 * implements {@code Reloadable}.
 */
public record ModuleSetDiff(List<ModuleSpec> addedSpecs, List<ModuleSpec> removedSpecs) {

  public ModuleSetDiff {
    addedSpecs = List.copyOf(addedSpecs);
    removedSpecs = List.copyOf(removedSpecs);
  }

  public static final ModuleSetDiff EMPTY = new ModuleSetDiff(List.of(), List.of());

  public boolean isEmpty() {
    return addedSpecs.isEmpty() && removedSpecs.isEmpty();
  }

  /** Symmetric diff over {@link ArenaModuleDeclarations#allSpecs()}. */
  public static ModuleSetDiff compute(
      final ArenaModuleDeclarations oldDecls, final ArenaModuleDeclarations newDecls) {
    final List<ModuleSpec> oldSpecs = oldDecls.allSpecs();
    final List<ModuleSpec> newSpecs = newDecls.allSpecs();
    final List<ModuleSpec> added = new ArrayList<>(newSpecs);
    added.removeAll(oldSpecs);
    final List<ModuleSpec> removed = new ArrayList<>(oldSpecs);
    removed.removeAll(newSpecs);
    return new ModuleSetDiff(added, removed);
  }
}
