// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import infinity.sim.ArenaModule;
import java.util.Set;

/** Catalog entry: single source of truth for module metadata (Q11). */
public record ModuleDescriptor(
    Class<? extends ArenaModule> moduleClass,
    Class<? extends Record> configType,
    ModuleCategory category,
    Set<String> requires) {

  public ModuleDescriptor {
    requires = Set.copyOf(requires);
  }
}
