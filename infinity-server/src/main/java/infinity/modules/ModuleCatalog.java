// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/** Authored registry of available module types. Empty in F1; appended as concrete modules land. */
public final class ModuleCatalog {

  private static final Map<String, ModuleDescriptor> CATALOG = Map.of();

  private ModuleCatalog() {}

  /** {@code null} when {@code moduleId} is not registered. */
  @Nullable
  public static ModuleDescriptor descriptor(final String moduleId) {
    return CATALOG.get(moduleId);
  }

  public static Set<String> allIds() {
    return CATALOG.keySet();
  }
}
