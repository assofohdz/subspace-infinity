// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.sim.SimTime;
import infinity.config.ArenaConfig;
import infinity.es.arena.ArenaId;
import infinity.es.arena.ArenaMap;
import infinity.settings.ConfigRegistry;
import infinity.settings.ConfigRegistrySystem;
import infinity.settings.GroovyArenaLoader;
import infinity.settings.GroovyFileWatcher;
import infinity.settings.GroovySettingsHost;
import infinity.systems.BaseInfinitySystem;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polls each loaded arena's {@code arena.groovy} for mtime changes and delegates the
 * module-set diff to {@link ArenaModuleSystem#applyModuleSetDiff}. F3 scope is limited to
 * module-set changes; alterations to non-module {@link ArenaConfig} fields
 * ({@code map}, {@code shipsScript}, fragment includes) emit a warn-and-skip notice
 * because applying them requires a full arena restart. Live reload is silently disabled
 * for any arena whose {@code arena.groovy} isn't reachable on disk (classpath-only deploys).
 */
public final class ArenaFileWatcherSystem extends BaseInfinitySystem {

  private static final Logger log = LoggerFactory.getLogger(ArenaFileWatcherSystem.class);

  /** 5 s — matches {@code EngineConfigSystem}'s cadence; engine-tier sets the rule. */
  static final long POLL_INTERVAL_NANOS = 5_000_000_000L;

  private EntitySet arenas;
  private ConfigRegistrySystem configRegistry;
  private ArenaModuleSystem moduleSystem;
  private GroovyArenaLoader loader = new GroovyArenaLoader();
  private final Map<EntityId, GroovyFileWatcher<ArenaConfig>> watchers = new HashMap<>();
  private long nextPollNanos;

  /** Test seam — inject a loader stub so the integration test doesn't need real groovy files. */
  public void setLoaderForTest(final GroovyArenaLoader loaderForTest) {
    this.loader = loaderForTest;
  }

  @Override
  protected void initialize() {
    final EntityData ed = requireSystem(EntityData.class);
    configRegistry = requireSystem(ConfigRegistrySystem.class);
    moduleSystem = requireSystem(ArenaModuleSystem.class);
    arenas = ed.getEntities(ArenaId.class, ArenaMap.class);
  }

  @Override
  protected void terminate() {
    arenas.release();
    arenas = null;
    watchers.clear();
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
    final long now = time.getTime();
    if (now < nextPollNanos) {
      return;
    }
    nextPollNanos = now + POLL_INTERVAL_NANOS;
    pollAll();
  }

  /** Forces a poll cycle without waiting for the interval; used by the integration test. */
  void pollNow() {
    pollAll();
  }

  private void pollAll() {
    for (final GroovyFileWatcher<ArenaConfig> w : watchers.values()) {
      w.poll();
    }
  }

  private void handleAdded(final Entity arenaEntity) {
    final EntityId entityId = arenaEntity.getId();
    final ArenaId arenaId = arenaEntity.get(ArenaId.class);
    final String arenaName = arenaId.getArena();
    final String classpathPath = String.format(GroovyArenaLoader.ARENA_GROOVY_TEMPLATE, arenaName);
    final Path onDisk = GroovySettingsHost.INSTANCE.resolveOnDisk(classpathPath);
    if (onDisk == null) {
      if (log.isDebugEnabled()) {
        log.debug("arena.groovy for {} not on disk; module-set live reload disabled", arenaName);
      }
      return;
    }
    final GroovyFileWatcher<ArenaConfig> watcher = new GroovyFileWatcher<>(
        onDisk,
        () -> loader.load(arenaName, classpathPath),
        reloaded -> applyReload(arenaId, entityId, reloaded));
    if (watcher.arm()) {
      watchers.put(entityId, watcher);
    }
  }

  private void handleRemoved(final EntityId entityId) {
    watchers.remove(entityId);
  }

  /**
   * Applies module-set diffs only. Non-module {@link ArenaConfig} changes (map, ships
   * script, fragment includes) are silently ignored in F3 — they'd require a full arena
   * restart and the operator gets that signal by re-issuing {@code ~loadArena}.
   *
   * <p>Package-private for the broken-parse-sentinel regression test.
   */
  void applyReload(
      final ArenaId arenaId, final EntityId arenaEntity, final ArenaConfig newConfig) {
    if (newConfig == null) {
      if (log.isWarnEnabled()) {
        log.warn("Arena {} reload returned null config; ignoring", arenaId.getArena());
      }
      return;
    }
    // ArenaConfig.EMPTY is GroovyArenaLoader's broken-parse sentinel — common during
    // mid-edit saves with unmatched braces. Diffing against it would mass-unload every
    // module; instead skip the reload so the operator can keep typing.
    if (newConfig == ArenaConfig.EMPTY) {
      if (log.isWarnEnabled()) {
        log.warn("Arena {} reload returned broken-parse sentinel; keeping current module set",
            arenaId.getArena());
      }
      return;
    }
    final ConfigRegistry current = configRegistry.forArena(arenaId);
    final ArenaModuleDeclarations oldDecls = current.get(ArenaModuleDeclarations.class);
    final ArenaModuleDeclarations newDecls = newConfig.modules();
    if (oldDecls.equals(newDecls)) {
      if (log.isInfoEnabled()) {
        log.info("Arena {} reload: no module-set changes detected", arenaId.getArena());
      }
      return;
    }
    final ModuleSetDiff diff = moduleSystem.applyModuleSetDiff(arenaEntity, newDecls);
    if (!diff.isEmpty()) {
      configRegistry.replace(arenaId, current.with(ArenaModuleDeclarations.class, newDecls));
    }
  }
}
