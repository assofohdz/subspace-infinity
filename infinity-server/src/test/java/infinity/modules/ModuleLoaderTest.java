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

  private static final String KILL_POINTS = "kill-points";

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

  @Test
  public void knownIdWithValidKwargs_validates() {
    final ArenaModuleDeclarations decls =
        declWithScoring(KILL_POINTS, Map.of("perKill", 100));
    final ValidationResult result = ModuleLoader.validate(decls);
    assertTrue("kill-points with perKill=100 must validate; errors=" + result.errors(),
        result.ok());
  }

  @Test
  public void knownIdWithInvalidKwargs_producesError() {
    final ArenaModuleDeclarations decls =
        declWithScoring(KILL_POINTS, Map.of("perKill", -50));
    final ValidationResult result = ModuleLoader.validate(decls);
    assertFalse(result.ok());
    assertTrue("error must surface compact-ctor message; got " + result.errors(),
        result.errors().get(0).contains(KILL_POINTS));
  }

  @Test
  public void knownIdBuilds_andProducesScoringModule() {
    final ArenaModuleDeclarations decls =
        declWithScoring(KILL_POINTS, Map.of("perKill", 50));
    final ArenaModuleSet built = ModuleLoader.build(decls, ctx());
    assertEquals(1, built.scoring().size());
    assertEquals("kill-points module class must match catalog",
        infinity.modules.scoring.KillPointsScoring.class,
        built.scoring().get(0).getClass());
  }

  private static ArenaModuleDeclarations declWithScoring(final String id) {
    return declWithScoring(id, Map.of());
  }

  private static ArenaModuleDeclarations declWithScoring(
      final String id, final Map<String, Object> kwargs) {
    return new ArenaModuleDeclarations(
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        List.of(new ModuleSpec(id, kwargs)),
        List.of(),
        Map.of());
  }

  private static ModuleContext ctx() {
    return new ModuleContext(null, null, null);
  }
}
