// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.GameSystemManager;
import infinity.config.ArenaConfig;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

/**
 * Regression: when the operator saves arena.groovy mid-edit with broken syntax,
 * {@code GroovyArenaLoader.load} returns {@link ArenaConfig#EMPTY}. The watcher must NOT
 * diff against it (which would mass-unload every module); it must skip the reload and
 * keep the live module set intact.
 */
public final class ArenaFileWatcherSystemTest {

  private static final String ARENA_NAME = "ffa";

  @Test
  public void brokenParseSentinel_isSkipped_modulesUntouched() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaModuleSystem moduleSystem = new ArenaModuleSystem();
    final ArenaFileWatcherSystem watcher = new ArenaFileWatcherSystem();
    systems.register(EntityData.class, ed);
    systems.register(ConfigRegistrySystem.class, new ConfigRegistrySystem());
    systems.register(ArenaModuleSystem.class, moduleSystem);
    systems.register(ArenaFileWatcherSystem.class, watcher);
    systems.initialize();
    systems.start();
    try {
      final EntityId arenaEntity = ed.createEntity();
      final ArenaId arenaId = new ArenaId(ARENA_NAME, arenaEntity);
      ed.setComponent(arenaEntity, arenaId);
      ed.setComponent(arenaEntity, new ArenaMap(
          new Vec3d(0, 0, 0), new Vec3d(1024, 4, 1024), "dummy.lvl", 0));

      final ArenaModuleDeclarations decls = new ArenaModuleDeclarations(
          Optional.empty(), Optional.empty(), Optional.empty(),
          Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
          List.of(new ModuleSpec("kill-points", Map.of("perKill", 100))),
          List.of(), Map.of());
      final ConfigRegistry seeded =
          ConfigRegistry.EMPTY.with(ArenaModuleDeclarations.class, decls);
      systems.get(ConfigRegistrySystem.class).replace(arenaId, seeded);
      systems.update();

      assertNotNull(moduleSystem.loadedFor(arenaEntity));
      final ScoringModule original =
          moduleSystem.loadedFor(arenaEntity).set().scoring().get(0);

      // Simulate the mid-edit save: watcher hands the broken-parse sentinel to applyReload.
      watcher.applyReload(arenaId, arenaEntity, ArenaConfig.EMPTY);

      assertEquals("module set untouched after broken-parse",
          1, moduleSystem.loadedFor(arenaEntity).set().scoring().size());
      assertSame("instance retained",
          original, moduleSystem.loadedFor(arenaEntity).set().scoring().get(0));
    } finally {
      systems.stop();
      systems.terminate();
    }
  }
}
