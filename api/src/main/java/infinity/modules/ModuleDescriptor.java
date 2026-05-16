// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import infinity.sim.ArenaModule;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * Catalog entry: single source of truth for module metadata (Q11).
 *
 * <p>{@code configType == null} signals a zero-config module — the loader uses
 * the {@code (ModuleContext)}-only constructor and rejects any kwargs at validate
 * time.
 */
public record ModuleDescriptor(
    Class<? extends ArenaModule> moduleClass,
    @Nullable Class<? extends Record> configType,
    ModuleCategory category,
    Set<String> requires) {

  public ModuleDescriptor {
    requires = Set.copyOf(requires);
  }
}
