// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.es.arena.ArenaId;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.sim.ArenaModule;
import infinity.systems.BaseInfinitySystem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observes arena lifecycle via {@code EntitySet<ArenaId>} (Q1); on add, validates
 * the arena's module declarations via {@link ModuleLoader} and installs the resulting
 * {@link ArenaModuleSet}. On remove, fires {@code onArenaUnload} on each loaded module
 * in registration-reverse order and uninstalls.
 *
 * <p>F1 catalog is empty; legacy arenas (no module statements) produce
 * {@link ArenaModuleSet#EMPTY} and dispatch is a no-op. Arenas declaring unknown
 * modules log the validation errors and run without a module set (failure → degrade,
 * not arena-load-fail, until ArenaSystem.fail integration is decided).
 */
public final class ArenaModuleSystem extends BaseInfinitySystem {

  private static final Logger log = LoggerFactory.getLogger(ArenaModuleSystem.class);

  private EntityData ed;
  private EntitySet arenas;
  private ConfigRegistrySystem configRegistry;
  private final Map<EntityId, LoadedArena> loaded = new HashMap<>();

  /** Bundles the per-arena state {@link #handleAdded} captures + {@link #handleRemoved} unwinds. */
  private record LoadedArena(ArenaId arenaId, ArenaModuleSet set) {}

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    configRegistry = requireSystem(ConfigRegistrySystem.class);
    arenas = ed.getEntities(ArenaId.class);
  }

  @Override
  protected void terminate() {
    arenas.release();
    arenas = null;
    loaded.clear();
  }

  @Override
  public void update(final SimTime time) {
    if (arenas.applyChanges()) {
      for (final Entity arenaEntity : arenas.getAddedEntities()) {
        handleAdded(arenaEntity);
      }
      for (final Entity arenaEntity : arenas.getRemovedEntities()) {
        handleRemoved(arenaEntity.getId());
      }
    }
  }

  private void handleAdded(final Entity arenaEntity) {
    final EntityId entityId = arenaEntity.getId();
    final ArenaId arenaId = arenaEntity.get(ArenaId.class);
    final ConfigRegistry registry = configRegistry.forArena(arenaId);
    final ArenaModuleDeclarations decls = registry.get(ArenaModuleDeclarations.class);

    final ValidationResult result = ModuleLoader.validate(decls);
    if (!result.ok()) {
      if (log.isErrorEnabled()) {
        for (final String error : result.errors()) {
          log.error("Arena {} module validation failed: {}", arenaId.getArena(), error);
        }
      }
      loaded.put(entityId, new LoadedArena(arenaId, ArenaModuleSet.EMPTY));
      return;
    }

    final ModuleContext context = new ModuleContext(arenaId, ed);
    final ArenaModuleSet set = ModuleLoader.build(decls, context);
    loaded.put(entityId, new LoadedArena(arenaId, set));

    for (final ArenaModule module : set.allModules()) {
      module.onArenaLoad(arenaId);
    }
    if (log.isDebugEnabled() && !set.equals(ArenaModuleSet.EMPTY)) {
      log.debug("Arena {} loaded with module set: {}", arenaId.getArena(), set);
    }
  }

  private void handleRemoved(final EntityId entityId) {
    final LoadedArena entry = loaded.remove(entityId);
    if (entry == null) {
      return;
    }
    final List<ArenaModule> all = entry.set().allModules();
    for (int i = all.size() - 1; i >= 0; i--) {
      all.get(i).onArenaUnload(entry.arenaId());
    }
  }

  @Override
  public void start() {
    // intentionally empty
  }

  @Override
  public void stop() {
    // intentionally empty
  }
}
