// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.ZoneConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates the zone-scope Groovy config and returns a typed {@link ZoneConfig}.
 * Thin facade over {@link GroovySettingsHost} — the host owns I/O, hardening
 * and error handling; this class supplies the {@code zone { … }} DSL.
 *
 * <p>Failure handling — any failure (missing file, parse error, eval error)
 * logs a warning and returns {@link ZoneConfig#EMPTY}, matching the legacy
 * INI behaviour of "no auto-load list, spawn at world origin" so an
 * unconfigured server still boots. Callers never see an exception.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * zone {
 *     autoLoad 'trench', 'deva'
 *     enterSpawn 'trench'
 *     scriptPollInterval 5.0   // seconds; dev-mode ships.groovy watcher throttle
 * }
 * }</pre>
 *
 * <p>All directives are optional; an entirely empty script is equivalent to
 * {@link ZoneConfig#EMPTY} (which carries the documented defaults — no
 * auto-load, blank enter-spawn, 5-second script poll).
 */
public final class GroovyZoneLoader {

  /**
   * Default classpath path for the zone config — the production location
   * relative to the {@code zone/} resource root. Tests override this.
   */
  public static final String DEFAULT_PATH = "/zone.groovy";

  private static final Logger log = LoggerFactory.getLogger(GroovyZoneLoader.class);
  private static final ZoneAdapter ADAPTER = new ZoneAdapter();

  /**
   * Load and parse {@link #DEFAULT_PATH}. {@link ZoneConfig#EMPTY} if the file
   * is missing or fails to evaluate.
   */
  public ZoneConfig load() {
    return load(DEFAULT_PATH);
  }

  /** Same contract as {@link #load()} but with a caller-supplied classpath path. */
  public ZoneConfig load(final String classpathPath) {
    // Host returns null on file-not-found (a distinction the arena loader needs);
    // zone treats missing the same as broken — both fall through to EMPTY.
    final ZoneConfig raw = GroovySettingsHost.INSTANCE.load(ADAPTER, classpathPath);
    final ZoneConfig cfg = raw == null ? ZoneConfig.EMPTY : raw;
    if (cfg != ZoneConfig.EMPTY && log.isInfoEnabled()) {
      log.info(
          "Applied {}: autoLoad={}, enterSpawn='{}'",
          classpathPath,
          cfg.autoLoadArenas(),
          cfg.enterSpawnArena());
    }
    return cfg;
  }

  /** Adapter holding the {@code zone { … }} DSL semantics. */
  private static final class ZoneAdapter
      implements GroovySettingsAdapter<ZoneConfig, ZoneConfigBuilder> {

    @Override
    public List<String> allowedImports() {
      // zone.groovy uses no explicit imports; auto-imports cover String/List.
      return Collections.emptyList();
    }

    @Override
    public ZoneConfigBuilder bind(final Binding binding) {
      final ZoneConfigBuilder builder = new ZoneConfigBuilder();
      binding.setVariable("zone", new ZoneClosure(builder));
      return builder;
    }

    @Override
    public ZoneConfig extract(final ZoneConfigBuilder accumulator) {
      return accumulator.build();
    }

    @Override
    public ZoneConfig empty() {
      return ZoneConfig.EMPTY;
    }
  }

  /**
   * Bound to the {@code zone} variable in the script; takes a configuring
   * closure and applies it to a {@link ZoneConfigBuilder}.
   */
  private static final class ZoneClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final ZoneConfigBuilder builder;

    ZoneClosure(final ZoneConfigBuilder builder) {
      super(null);
      this.builder = builder;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final Closure<?> body) {
      body.setDelegate(builder);
      body.setResolveStrategy(DELEGATE_FIRST);
      body.call();
      return null;
    }
  }

  /**
   * Delegate for the {@code zone { ... }} block. Each directive method appends
   * to the field it owns; missing directives leave the field at its empty
   * default so partial scripts are valid.
   */
  public static final class ZoneConfigBuilder {

    private final List<String> autoLoadArenas = new ArrayList<>();
    private String enterSpawnArena = "";
    // Defaults to ZoneConfig.EMPTY's value so an omitted directive matches the
    // documented fallback (5-second poll, the historical SCRIPT_POLL_INTERVAL_NANOS).
    private double scriptPollIntervalSeconds = ZoneConfig.EMPTY.scriptPollIntervalSeconds();
    // Slice S5 — Subspace canon "repels push everyone" maps to true.
    private boolean repelFriendlies = ZoneConfig.EMPTY.repelFriendlies();

    // Package-private so unit tests can build configs without standing up the
    // full GroovyShell pipeline (mirrors GroovyShipLoader.ShipConfigBuilder).
    ZoneConfigBuilder() {}

    public void autoLoad(final String... arenaNames) {
      if (arenaNames == null) {
        return;
      }
      for (final String name : arenaNames) {
        if (name != null && !name.isBlank()) {
          autoLoadArenas.add(name.trim());
        }
      }
    }

    public void enterSpawn(final String arenaName) {
      this.enterSpawnArena = arenaName == null ? "" : arenaName.trim();
    }

    public void scriptPollInterval(final Number seconds) {
      if (seconds == null) {
        return;
      }
      final double v = seconds.doubleValue();
      if (v <= 0) {
        // Non-positive intervals would either disable the watcher (zero) or
        // throw the sim into a tight stat() loop (negative). Reject both;
        // the documented default of 5 seconds applies.
        return;
      }
      this.scriptPollIntervalSeconds = v;
    }

    /**
     * Slice S5 — when {@code true} (default), repels push every
     * {@link infinity.es.Repellable} body in radius regardless of team. When
     * {@code false}, same-frequency ships are skipped (non-frequency-bearing
     * entities like bombs / mines are always pushed). Infinity-specific ops
     * knob; not in REFERENCE.md.
     */
    public void repelFriendlies(final boolean enabled) {
      this.repelFriendlies = enabled;
    }

    ZoneConfig build() {
      return new ZoneConfig(
          List.copyOf(autoLoadArenas),
          enterSpawnArena,
          scriptPollIntervalSeconds,
          repelFriendlies);
    }
  }
}
