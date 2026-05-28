// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.ai.capability.BotSynergyTable;
import infinity.ai.objective.BotRoleRegistry;
import infinity.config.BotDerivationConfig;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Engine-tier holder for the engineer-authored {@link BotSynergyTable} + {@link BotRoleRegistry}
 * ({@code engine-bot-ai.groovy}, ADR-0014 / ADR-0015 / ADR-0016). Loads once at init, hot-reloads via
 * {@link GroovyFileWatcher} (both blocks reload together); held snapshots are {@code volatile} so
 * watcher writes publish safely to the planner. Mirrors {@link EngineConfigSystem}; parse failures
 * fall back to empties.
 */
public class EngineBotAiSystem extends AbstractGameSystem {

  private static final Logger log = LoggerFactory.getLogger(EngineBotAiSystem.class);

  // 5 s poll cadence — matches the other Groovy-tier holders.
  static final long POLL_INTERVAL_NANOS = 5_000_000_000L;

  private final GroovyBotSynergyLoader loader;
  private final GroovyBotRolesLoader rolesLoader = new GroovyBotRolesLoader();
  private final GroovyBotDerivationLoader derivationLoader = new GroovyBotDerivationLoader();
  private final String classpathPath;
  private final Path watchedPathOverride;
  private volatile BotSynergyTable table = new BotSynergyTable(java.util.Map.of());
  private volatile BotRoleRegistry roles = new BotRoleRegistry(List.of());
  private volatile BotDerivationConfig derivation = BotDerivationConfig.DEFAULTS;

  private GroovyFileWatcher<BotSynergyTable> watcher;
  private long nextPollNanos;

  public EngineBotAiSystem() {
    this(new GroovyBotSynergyLoader(), GroovyBotSynergyLoader.DEFAULT_PATH, null);
  }

  /** Test seam — explicit on-disk watch path bypasses {@link GroovySettingsHost#resolveOnDisk}. */
  public EngineBotAiSystem(
      final GroovyBotSynergyLoader loader,
      final String classpathPath,
      final Path watchedPathOverride) {
    this.loader = loader;
    this.classpathPath = classpathPath;
    this.watchedPathOverride = watchedPathOverride;
  }

  @Override
  protected void initialize() {
    table = loader.load(classpathPath);
    roles = rolesLoader.load(classpathPath);
    derivation = derivationLoader.load(classpathPath);
    registerWatch();
  }

  @Override
  public void update(final SimTime time) {
    if (watcher == null) {
      return;
    }
    final long now = time.getTime();
    if (now < nextPollNanos) {
      return;
    }
    nextPollNanos = now + POLL_INTERVAL_NANOS;
    watcher.poll();
  }

  @Override
  protected void terminate() {
    watcher = null;
  }

  /** Current synergy table; never null (empty if load failed). */
  public BotSynergyTable get() {
    return table;
  }

  /** Current bot-role registry (ADR-0015); never null (default role only if load failed). */
  public BotRoleRegistry roles() {
    return roles;
  }

  /** Current capability-derivation coefficients (ADR-0014); never null (defaults if load failed). */
  public BotDerivationConfig derivation() {
    return derivation;
  }

  private void registerWatch() {
    final Path onDisk =
        watchedPathOverride != null
            ? watchedPathOverride
            : GroovySettingsHost.INSTANCE.resolveOnDisk(classpathPath);
    if (onDisk == null) {
      if (log.isDebugEnabled()) {
        log.debug("{} not on disk; engine-bot-ai live reload disabled for this run", classpathPath);
      }
      return;
    }
    final GroovyFileWatcher<BotSynergyTable> w =
        new GroovyFileWatcher<>(
            onDisk,
            () -> loader.load(classpathPath),
            reloaded -> {
              table = reloaded;
              roles = rolesLoader.load(classpathPath); // same file holds the roles { } block too
              derivation = derivationLoader.load(classpathPath); // and the derivation { } block
              if (log.isInfoEnabled()) {
                log.info(
                    "Engine bot-AI synergy table + roles + derivation reloaded from {}", onDisk);
              }
            });
    if (w.arm()) {
      watcher = w;
    }
  }
}
