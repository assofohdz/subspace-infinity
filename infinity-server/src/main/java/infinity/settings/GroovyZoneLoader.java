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

/** Evaluates {@code zone.groovy} → {@link ZoneConfig}; returns {@link ZoneConfig#EMPTY} on any failure (logged). */
public final class GroovyZoneLoader {

  public static final String DEFAULT_PATH = "/zone.groovy";

  private static final Logger log = LoggerFactory.getLogger(GroovyZoneLoader.class);
  private static final ZoneAdapter ADAPTER = new ZoneAdapter();

  public ZoneConfig load() {
    return load(DEFAULT_PATH);
  }

  public ZoneConfig load(final String classpathPath) {
    // Zone treats file-not-found and broken the same — both fall through to EMPTY.
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

  private static final class ZoneAdapter
      implements GroovySettingsAdapter<ZoneConfig, ZoneConfigBuilder> {

    @Override
    public List<String> allowedImports() {
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

  private static final class ZoneClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final transient ZoneConfigBuilder builder;

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

  /** Delegate for {@code zone{…}}; fields default to {@link ZoneConfig#EMPTY}. */
  public static final class ZoneConfigBuilder {

    private final List<String> autoLoadArenas = new ArrayList<>();
    private String enterSpawnArena = "";
    private double scriptPollIntervalSeconds = ZoneConfig.EMPTY.scriptPollIntervalSeconds();
    private boolean repelFriendlies = ZoneConfig.EMPTY.repelFriendlies();

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
      // Reject non-positive: 0 disables the watcher, negative would spin stat().
      if (v <= 0) {
        return;
      }
      this.scriptPollIntervalSeconds = v;
    }

    /** When {@code true} (default), repels push every {@link infinity.es.Repellable} regardless of team. Infinity-specific; not in REFERENCE.md. */
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
