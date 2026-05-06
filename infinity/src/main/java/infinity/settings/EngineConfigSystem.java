// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import com.simsilica.sim.AbstractGameSystem;
import infinity.config.EngineConfig;

/**
 * Engine-tier config holder — the fourth scope above preset / arena / zone.
 * Loads {@code engine.groovy} once at server initialize and exposes the
 * resulting {@link EngineConfig} for runtime consumers.
 *
 * <p>Runtime consumers (today: {@code WeaponsSystem.getAttackInfo} for the
 * projectile-speed scale + cap) call {@link #get()} to read the current
 * snapshot. Storing engine-tier values once at startup means there's no
 * per-tick or per-fire reload hit; future live-reload work would extend
 * this system without changing consumer code.
 *
 * <p>Loading sequence:
 * <ol>
 *   <li>{@code GroovyEngineLoader.load()} reads {@code /engine.groovy}
 *       from the classpath.
 *   <li>Failure (missing file, parse error) falls back to
 *       {@link EngineConfig#DEFAULTS} with a warning log.
 *   <li>The system holds the result for the lifetime of the server.
 * </ol>
 *
 * <p>Registered in {@code GameServer} alongside the other persistent
 * config systems (e.g. {@code ConfigRegistrySystem}).
 */
public class EngineConfigSystem extends AbstractGameSystem {

  private final GroovyEngineLoader loader;
  private volatile EngineConfig config = EngineConfig.DEFAULTS;

  /** Production constructor — uses the default classpath path. */
  public EngineConfigSystem() {
    this(new GroovyEngineLoader());
  }

  /** Test seam — caller-supplied loader. */
  public EngineConfigSystem(final GroovyEngineLoader loader) {
    this.loader = loader;
  }

  @Override
  protected void initialize() {
    config = loader.load();
  }

  @Override
  protected void terminate() {
    // No-op; nothing to release.
  }

  /**
   * Current engine-tier config snapshot. Never returns {@code null} —
   * pre-{@code initialize()} returns {@link EngineConfig#DEFAULTS}, and
   * post-init returns the loaded config (or DEFAULTS if loading failed).
   */
  public EngineConfig get() {
    return config;
  }
}
