// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.es.arena.MatchNumber;
import infinity.es.arena.RoundEndPending;
import infinity.es.arena.RoundNumber;
import infinity.server.chat.InfinityChatHostedService;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.sim.ArenaModule;
import infinity.sim.ChatHostedPoster;
import infinity.sim.PhysicsManager;
import infinity.sim.internal.InfinityPhysicsManager;
import infinity.systems.BaseInfinitySystem;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
  private ChatHostedPoster chat;
  private PhysicsManager physics;
  private final Map<EntityId, LoadedArena> loaded = new HashMap<>();

  /** Bundles the per-arena state {@link #handleAdded} captures + {@link #handleRemoved} unwinds. */
  public record LoadedArena(ArenaId arenaId, ArenaModuleSet set) {}

  /** Look up the loaded module set for an arena entity; {@code null} if the arena hasn't been loaded. */
  public LoadedArena loadedFor(final EntityId arenaEntity) {
    return loaded.get(arenaEntity);
  }

  /** Find the loaded set by the {@link ArenaId} value type (the arena's name); {@code null} if none. */
  public LoadedArena loadedFor(final ArenaId arenaId) {
    if (arenaId == null) {
      return null;
    }
    for (final LoadedArena entry : loaded.values()) {
      if (arenaId.equals(entry.arenaId())) {
        return entry;
      }
    }
    return null;
  }

  @Override
  protected void initialize() {
    ed = requireSystem(EntityData.class);
    configRegistry = requireSystem(ConfigRegistrySystem.class);
    chat = getSystem(InfinityChatHostedService.class); // nullable: tests register module system without chat
    physics = getSystem(InfinityPhysicsManager.class); // nullable for the same reason
    // Filter MUST include ArenaMap (stamped only on arena entities by ArenaLogic) — ArenaId
    // alone matches every ship/bot/prize stamped by ArenaMembershipSystem, which would
    // trigger module re-bootstrap on every ship respawn (observed: ~70K bounty entities/sec
    // leak on 2nd kill before this filter was tightened).
    arenas = ed.getEntities(ArenaId.class, ArenaMap.class);
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
    tickTeamSetups(time);
    tickRoundStructures(time);
    tickWinConditions();
    tickMechanics(time);
  }

  /** Per-tick dispatch for the per-arena {@code teamSetup} module (if any). */
  private void tickTeamSetups(final SimTime time) {
    for (final LoadedArena entry : loaded.values()) {
      entry.set().teamSetup().ifPresent(m -> m.tickTeamSetup(entry.arenaId(), time));
    }
  }

  /** Per-tick dispatch for opt-in {@code mechanic} modules (state-publishing phase). */
  private void tickMechanics(final SimTime time) {
    for (final LoadedArena entry : loaded.values()) {
      for (final MechanicModule m : entry.set().mechanics().values()) {
        m.tickMechanic(entry.arenaId(), time);
      }
    }
  }

  /** Per-tick dispatch for the per-arena {@code roundStructure} module (if any). */
  private void tickRoundStructures(final SimTime time) {
    for (final LoadedArena entry : loaded.values()) {
      entry.set().roundStructure().ifPresent(m -> m.tickRoundStructure(entry.arenaId(), time));
    }
  }

  /**
   * Per-tick dispatch for {@code winCondition.checkTermination}. First module
   * to return present causes {@code RoundEndPending} to be stamped on the
   * arena entity; subsequent modules in the same arena are skipped this tick.
   */
  private void tickWinConditions() {
    for (final Map.Entry<EntityId, LoadedArena> e : loaded.entrySet()) {
      final ArenaId arenaId = e.getValue().arenaId();
      for (final WinConditionModule wc : e.getValue().set().winConditions()) {
        final Optional<String> reason = wc.checkTermination(arenaId);
        if (reason.isPresent()) {
          ed.setComponent(e.getKey(), new RoundEndPending());
          break;
        }
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
      logValidationErrors(arenaId, result);
      loaded.put(entityId, new LoadedArena(arenaId, ArenaModuleSet.EMPTY));
      return;
    }

    final ModuleContext context = new ModuleContext(arenaId, entityId, ed, chat, physics);
    final ArenaModuleSet set = ModuleLoader.build(decls, context);
    loaded.put(entityId, new LoadedArena(arenaId, set));
    bootstrapLifecycle(entityId, arenaId, set);
  }

  private void logValidationErrors(final ArenaId arenaId, final ValidationResult result) {
    for (final String error : result.errors()) {
      if (log.isErrorEnabled()) {
        log.error("Arena {} module validation failed: {}", arenaId.getArena(), error);
      }
    }
  }

  /** Fires {@code onArenaLoad} + bootstraps first match + first round on every loaded module. */
  private void bootstrapLifecycle(
      final EntityId entityId, final ArenaId arenaId, final ArenaModuleSet set) {
    final List<ArenaModule> modules = set.allModules();
    for (final ArenaModule module : modules) {
      module.onArenaLoad(arenaId);
    }
    if (modules.isEmpty()) {
      return;
    }
    ed.setComponent(entityId, new MatchNumber(1));
    ed.setComponent(entityId, new RoundNumber(1));
    for (final ArenaModule module : modules) {
      module.onMatchStart(arenaId);
    }
    for (final ArenaModule module : modules) {
      module.onRoundStart(arenaId, 1);
    }
    if (log.isDebugEnabled()) {
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
