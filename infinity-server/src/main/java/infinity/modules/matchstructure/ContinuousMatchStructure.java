// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.matchstructure;

import infinity.modules.MatchStructureModule;
import infinity.modules.ModuleContext;

/**
 * Degenerate matchStructure — never declares match-end. The arena spends its
 * entire lifetime in match 1; rounds iterate independently. {@code *MatchScore}
 * components accumulate identically to {@code *TotalScore} under this shape.
 */
public final class ContinuousMatchStructure implements MatchStructureModule {

  @SuppressWarnings("PMD.UnusedFormalParameter") // ctor shape is the Module-loader ABI.
  public ContinuousMatchStructure(final ModuleContext ctx) {
    // intentionally empty — no per-arena state; defaults already encode "never end"
  }

  // shouldMatchEnd: default-no-op returns false (per MatchStructureModule)
}
