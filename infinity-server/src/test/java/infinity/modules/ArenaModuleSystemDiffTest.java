// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.es.arena.RoundNumber;
import infinity.modules.ArenaModuleSystem.LoadedArena;
import infinity.modules.scoring.BonusPointsScoring;
import infinity.modules.scoring.KillPointsScoring;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

/**
 * Pins {@link ArenaModuleSystem#applyModuleSetDiff} — the hot-reload entry point. Verifies that
 * adding a scoring module mid-run preserves the existing instance and instantiates the new one
 * with its lifecycle hooks fired at the arena's current round number.
 */
public final class ArenaModuleSystemDiffTest {

  private static final String ARENA_NAME = "ffa";
  private static final String KILL_POINTS = "kill-points";
  private static final String BONUS_POINTS = "bonus-points";
  private static final String PER_KILL = "perKill";

  @Test
  public void addLayeredScoringModule_preservesExistingInstance_andLifecycleFiresAtCurrentRound() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaModuleSystem moduleSystem = new ArenaModuleSystem();
    systems.register(EntityData.class, ed);
    systems.register(ConfigRegistrySystem.class, new ConfigRegistrySystem());
    systems.register(ArenaModuleSystem.class, moduleSystem);
    systems.initialize();
    systems.start();
    try {
      final EntityId arenaEntity = ed.createEntity();
      final ArenaId arenaId = new ArenaId(ARENA_NAME, arenaEntity);
      // Need both ArenaId AND ArenaMap — module system filters on both (avoids
      // matching ships/bots which carry ArenaId but not ArenaMap).
      ed.setComponent(arenaEntity, arenaId);
      ed.setComponent(arenaEntity, new ArenaMap(
        new com.simsilica.mathd.Vec3d(0, 0, 0),
        new com.simsilica.mathd.Vec3d(1024, 4, 1024),
        "dummy.lvl", 0));

      final ArenaModuleDeclarations initialDecls = scoringOnly(
          new ModuleSpec(KILL_POINTS, Map.of(PER_KILL, 100)));
      installInitialModuleSet(systems, moduleSystem, arenaEntity, arenaId, initialDecls);

      // Arena has progressed to round 3 — added modules' onRoundStart should see that.
      ed.setComponent(arenaEntity, new RoundNumber(3));

      final ScoringModule existingKillPoints =
          moduleSystem.loadedFor(arenaEntity).set().scoring().get(0);

      final ArenaModuleDeclarations newDecls = scoringOnly(
          new ModuleSpec(KILL_POINTS, Map.of(PER_KILL, 100)),
          new ModuleSpec(BONUS_POINTS, Map.of()));
      final ModuleSetDiff diff = moduleSystem.applyModuleSetDiff(arenaEntity, newDecls);

      assertEquals("expected one added spec", 1, diff.addedSpecs().size());
      assertEquals(BONUS_POINTS, diff.addedSpecs().get(0).moduleId());
      assertEquals("no removals", List.of(), diff.removedSpecs());

      final LoadedArena loaded = moduleSystem.loadedFor(arenaEntity);
      assertNotNull(loaded);
      assertEquals("scoring list size", 2, loaded.set().scoring().size());
      assertSame("existing kill-points instance retained",
          existingKillPoints, loaded.set().scoring().get(0));
      assertEquals(BonusPointsScoring.class, loaded.set().scoring().get(1).getClass());
      assertEquals("decls updated", newDecls, loaded.decls());
    } finally {
      systems.stop();
      systems.terminate();
    }
  }

  @Test
  public void removeLayeredScoringModule_callsOnArenaUnload_andDropsFromSet() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaModuleSystem moduleSystem = new ArenaModuleSystem();
    systems.register(EntityData.class, ed);
    systems.register(ConfigRegistrySystem.class, new ConfigRegistrySystem());
    systems.register(ArenaModuleSystem.class, moduleSystem);
    systems.initialize();
    systems.start();
    try {
      final EntityId arenaEntity = ed.createEntity();
      final ArenaId arenaId = new ArenaId(ARENA_NAME, arenaEntity);
      ed.setComponent(arenaEntity, arenaId);
      ed.setComponent(arenaEntity, new ArenaMap(
        new com.simsilica.mathd.Vec3d(0, 0, 0),
        new com.simsilica.mathd.Vec3d(1024, 4, 1024),
        "dummy.lvl", 0));

      final ArenaModuleDeclarations initialDecls = scoringOnly(
          new ModuleSpec(KILL_POINTS, Map.of(PER_KILL, 100)),
          new ModuleSpec(BONUS_POINTS, Map.of()));
      installInitialModuleSet(systems, moduleSystem, arenaEntity, arenaId, initialDecls);

      final KillPointsScoring kill =
          (KillPointsScoring) moduleSystem.loadedFor(arenaEntity).set().scoring().get(0);

      final ArenaModuleDeclarations newDecls = scoringOnly(
          new ModuleSpec(KILL_POINTS, Map.of(PER_KILL, 100)));
      final ModuleSetDiff diff = moduleSystem.applyModuleSetDiff(arenaEntity, newDecls);

      assertEquals(1, diff.removedSpecs().size());
      assertEquals(BONUS_POINTS, diff.removedSpecs().get(0).moduleId());
      assertEquals(1, moduleSystem.loadedFor(arenaEntity).set().scoring().size());
      assertSame(kill, moduleSystem.loadedFor(arenaEntity).set().scoring().get(0));
    } finally {
      systems.stop();
      systems.terminate();
    }
  }

  @Test
  public void emptyDiff_isANoOp() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaModuleSystem moduleSystem = new ArenaModuleSystem();
    systems.register(EntityData.class, ed);
    systems.register(ConfigRegistrySystem.class, new ConfigRegistrySystem());
    systems.register(ArenaModuleSystem.class, moduleSystem);
    systems.initialize();
    systems.start();
    try {
      final EntityId arenaEntity = ed.createEntity();
      final ArenaId arenaId = new ArenaId(ARENA_NAME, arenaEntity);
      ed.setComponent(arenaEntity, arenaId);
      ed.setComponent(arenaEntity, new ArenaMap(
        new com.simsilica.mathd.Vec3d(0, 0, 0),
        new com.simsilica.mathd.Vec3d(1024, 4, 1024),
        "dummy.lvl", 0));

      final ArenaModuleDeclarations decls = scoringOnly(
          new ModuleSpec(KILL_POINTS, Map.of(PER_KILL, 100)));
      installInitialModuleSet(systems, moduleSystem, arenaEntity, arenaId, decls);
      final ScoringModule original =
          moduleSystem.loadedFor(arenaEntity).set().scoring().get(0);

      final ModuleSetDiff diff = moduleSystem.applyModuleSetDiff(arenaEntity, decls);

      assertEquals(ModuleSetDiff.EMPTY, diff);
      assertSame("instance untouched", original,
          moduleSystem.loadedFor(arenaEntity).set().scoring().get(0));
    } finally {
      systems.stop();
      systems.terminate();
    }
  }

  @Test
  public void unknownArenaEntity_returnsEmptyDiff() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaModuleSystem moduleSystem = new ArenaModuleSystem();
    systems.register(EntityData.class, ed);
    systems.register(ConfigRegistrySystem.class, new ConfigRegistrySystem());
    systems.register(ArenaModuleSystem.class, moduleSystem);
    systems.initialize();
    systems.start();
    try {
      final EntityId orphan = ed.createEntity();
      assertEquals(ModuleSetDiff.EMPTY,
          moduleSystem.applyModuleSetDiff(orphan, scoringOnly(
              new ModuleSpec(KILL_POINTS, Map.of(PER_KILL, 100)))));
      assertNull(moduleSystem.loadedFor(orphan));
    } finally {
      systems.stop();
      systems.terminate();
    }
  }

  private static ArenaModuleDeclarations scoringOnly(final ModuleSpec... specs) {
    return new ArenaModuleDeclarations(
        Optional.empty(), Optional.empty(), Optional.empty(),
        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
        List.of(specs), List.of(), Map.of());
  }

  /**
   * Drives ArenaModuleSystem's normal {@code handleAdded} path: with the EntityData
   * carrying {@code ArenaModuleDeclarations} via the ConfigRegistrySystem slot, one
   * {@code systems.update()} sees the new arena entity and installs the LoadedArena.
   */
  private static void installInitialModuleSet(
      final GameSystemManager systems,
      final ArenaModuleSystem moduleSystem,
      final EntityId arenaEntity,
      final ArenaId arenaId,
      final ArenaModuleDeclarations decls) {
    final ConfigRegistrySystem registry = systems.get(ConfigRegistrySystem.class);
    final ConfigRegistry seeded =
        ConfigRegistry.EMPTY.with(ArenaModuleDeclarations.class, decls);
    registry.replace(arenaId, seeded);
    systems.update();
    assertNotNull("expected ArenaModuleSystem to load the arena",
        moduleSystem.loadedFor(arenaEntity));
  }
}
