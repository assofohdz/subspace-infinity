// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

/** Pins {@link ModuleSetDiff#compute} symmetric-diff semantics. */
public final class ModuleSetDiffTest {

  private static final String KILL_POINTS = "kill-points";
  private static final String BONUS_POINTS = "bonus-points";
  private static final String PER_KILL = "perKill";

  @Test
  public void identicalDeclarations_diffEmpty() {
    final ArenaModuleDeclarations decls = withScoring(KILL_POINTS, Map.of(PER_KILL, 100));
    assertTrue(ModuleSetDiff.compute(decls, decls).isEmpty());
  }

  @Test
  public void addedScoring_appearsInAdded() {
    final ArenaModuleDeclarations oldDecls = withScoring(KILL_POINTS, Map.of(PER_KILL, 100));
    final ArenaModuleDeclarations newDecls = withScorings(
        new ModuleSpec(KILL_POINTS, Map.of(PER_KILL, 100)),
        new ModuleSpec(BONUS_POINTS, Map.of()));
    final ModuleSetDiff diff = ModuleSetDiff.compute(oldDecls, newDecls);
    assertEquals(1, diff.addedSpecs().size());
    assertEquals(BONUS_POINTS, diff.addedSpecs().get(0).moduleId());
    assertEquals(List.of(), diff.removedSpecs());
  }

  @Test
  public void removedScoring_appearsInRemoved() {
    final ArenaModuleDeclarations oldDecls = withScorings(
        new ModuleSpec(KILL_POINTS, Map.of(PER_KILL, 100)),
        new ModuleSpec(BONUS_POINTS, Map.of()));
    final ArenaModuleDeclarations newDecls = withScoring(KILL_POINTS, Map.of(PER_KILL, 100));
    final ModuleSetDiff diff = ModuleSetDiff.compute(oldDecls, newDecls);
    assertEquals(List.of(), diff.addedSpecs());
    assertEquals(1, diff.removedSpecs().size());
    assertEquals(BONUS_POINTS, diff.removedSpecs().get(0).moduleId());
  }

  @Test
  public void reconfiguredKwargs_treatAsRemoveThenAdd() {
    final ArenaModuleDeclarations oldDecls = withScoring(KILL_POINTS, Map.of(PER_KILL, 100));
    final ArenaModuleDeclarations newDecls = withScoring(KILL_POINTS, Map.of(PER_KILL, 250));
    final ModuleSetDiff diff = ModuleSetDiff.compute(oldDecls, newDecls);
    assertEquals(1, diff.addedSpecs().size());
    assertEquals(250, diff.addedSpecs().get(0).kwargs().get(PER_KILL));
    assertEquals(1, diff.removedSpecs().size());
    assertEquals(100, diff.removedSpecs().get(0).kwargs().get(PER_KILL));
  }

  @Test
  public void singlePickReplacement_oneInOneOut() {
    final ArenaModuleDeclarations oldDecls = withRespawn("instant-respawn", Map.of());
    final ArenaModuleDeclarations newDecls = withRespawn("cooldown-respawn", Map.of("seconds", 5));
    final ModuleSetDiff diff = ModuleSetDiff.compute(oldDecls, newDecls);
    assertEquals(1, diff.addedSpecs().size());
    assertEquals("cooldown-respawn", diff.addedSpecs().get(0).moduleId());
    assertEquals(1, diff.removedSpecs().size());
    assertEquals("instant-respawn", diff.removedSpecs().get(0).moduleId());
  }

  private static ArenaModuleDeclarations withScoring(final String id, final Map<String, Object> kwargs) {
    return withScorings(new ModuleSpec(id, kwargs));
  }

  private static ArenaModuleDeclarations withScorings(final ModuleSpec... specs) {
    return new ArenaModuleDeclarations(
        Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
        List.of(specs), List.of(), Map.of());
  }

  private static ArenaModuleDeclarations withRespawn(final String id, final Map<String, Object> kwargs) {
    return new ArenaModuleDeclarations(
        Optional.empty(), Optional.empty(), Optional.of(new ModuleSpec(id, kwargs)),
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
        List.of(), List.of(), Map.of());
  }
}
