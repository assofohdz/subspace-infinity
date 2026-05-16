// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

/** Covers F1 fail-fast paths (empty catalog) + EMPTY round-trip. */
public final class ModuleLoaderTest {

  @Test
  public void emptyDeclarationsValidateOk() {
    final ValidationResult result = ModuleLoader.validate(ArenaModuleDeclarations.EMPTY);
    assertTrue("EMPTY declarations must validate", result.ok());
  }

  @Test
  public void emptyDeclarationsBuildToEmptySet() {
    final ArenaModuleSet built = ModuleLoader.build(ArenaModuleDeclarations.EMPTY, ctx());
    assertSame("EMPTY input must produce EMPTY set", ArenaModuleSet.EMPTY, built);
  }

  @Test
  public void unknownModuleIdProducesError() {
    final ValidationResult result = ModuleLoader.validate(declWithScoring("ghost-points"));
    assertFalse(result.ok());
    assertEquals(1, result.errors().size());
    assertTrue(
        "error must name the unknown id",
        result.errors().get(0).contains("ghost-points"));
  }

  private static ArenaModuleDeclarations declWithScoring(final String id) {
    return new ArenaModuleDeclarations(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        List.of(new ModuleSpec(id, Map.of())),
        List.of(),
        Map.of());
  }

  private static ModuleContext ctx() {
    return new ModuleContext(null, null);
  }
}
