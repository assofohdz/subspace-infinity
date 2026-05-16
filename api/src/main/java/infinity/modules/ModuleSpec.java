// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import java.util.Map;

/** A single module declaration parsed from arena.groovy ({@code <category> 'id', kw: v, ...}). */
public record ModuleSpec(String moduleId, Map<String, Object> kwargs) {

  public ModuleSpec {
    if (moduleId == null || moduleId.isBlank()) {
      throw new IllegalArgumentException("moduleId must not be blank");
    }
    kwargs = Map.copyOf(kwargs);
  }
}
