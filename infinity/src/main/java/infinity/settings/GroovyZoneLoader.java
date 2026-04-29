/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import infinity.config.ZoneConfig;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates the zone-scope Groovy config and returns a typed {@link ZoneConfig}.
 * Mirrors {@link GroovyShipLoader} (filesystem-first dev-mode read, classpath
 * fallback for packaged jars, fallback installed on any failure) but at zone
 * scope, so it has no per-arena id and the script defines exactly one
 * {@code zone {…}} block.
 *
 * <p>Failure handling — any failure (missing file, parse error, eval error,
 * IO error) logs a warning and returns {@link ZoneConfig#EMPTY}, matching the
 * legacy INI behaviour of "no auto-load list, spawn at world origin" so an
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

  /**
   * Load and parse {@link #DEFAULT_PATH}. {@link ZoneConfig#EMPTY} if the file
   * is missing or fails to evaluate.
   */
  public ZoneConfig load() {
    return load(DEFAULT_PATH);
  }

  /** Same contract as {@link #load()} but with a caller-supplied classpath path. */
  public ZoneConfig load(final String classpathPath) {
    try {
      final String source = readSource(classpathPath);
      if (source == null) {
        log.warn(
            "{} not found on filesystem or classpath; using ZoneConfig.EMPTY",
            classpathPath);
        return ZoneConfig.EMPTY;
      }
      final ZoneConfig cfg = evaluate(source, classpathPath);
      log.info(
          "Applied {}: autoLoad={}, enterSpawn='{}'",
          classpathPath,
          cfg.autoLoadArenas(),
          cfg.enterSpawnArena());
      return cfg;
    } catch (final Exception e) {
      log.warn("{} failed to evaluate; using ZoneConfig.EMPTY", classpathPath, e);
      return ZoneConfig.EMPTY;
    }
  }

  /**
   * Resolve {@code classpathPath} to an on-disk file when a dev-mode source
   * exists, or {@code null} when only the classpath copy is reachable. Public
   * so a future file watcher (parallel to the per-arena ships.groovy reload
   * path in {@code ArenaSystem}) can stat / poll the same file the loader
   * actually reads from.
   */
  @Nullable
  public Path resolveOnDisk(final String classpathPath) {
    if (classpathPath == null || classpathPath.isBlank()) {
      return null;
    }
    final String relative =
        classpathPath.startsWith("/") ? classpathPath.substring(1) : classpathPath;
    final Path[] candidates = {Paths.get("zone", relative), Paths.get("infinity/zone", relative)};
    for (final Path p : candidates) {
      if (Files.isReadable(p)) {
        return p;
      }
    }
    return null;
  }

  @Nullable
  private String readSource(final String classpathPath) throws IOException {
    // Dev mode: try the filesystem source first so edits show up without a rebuild.
    // (Same dev-mode rationale as GroovyShipLoader.readSource — Gradle's :infinity:run
    // sets the JVM working directory to the infinity/ subproject and zone/ is the
    // resource root, so /x.groovy on the classpath maps to zone/x.groovy on disk.
    // The "infinity/zone" candidate covers running from the project root.)
    final Path onDisk = resolveOnDisk(classpathPath);
    if (onDisk != null) {
      log.debug("Reading {} from filesystem source: {}", classpathPath, onDisk);
      return Files.readString(onDisk, StandardCharsets.UTF_8);
    }
    try (InputStream is = getClass().getResourceAsStream(classpathPath)) {
      if (is == null) {
        return null;
      }
      log.debug("Reading {} from classpath", classpathPath);
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private ZoneConfig evaluate(final String source, final String path) {
    final ZoneConfigBuilder builder = new ZoneConfigBuilder();

    final Binding binding = new Binding();
    binding.setVariable("zone", new ZoneClosure(builder));

    final GroovyShell shell = new GroovyShell(binding);
    shell.evaluate(source, path);

    return builder.build();
  }

  /**
   * Bound to the {@code zone} variable in the script; takes a configuring
   * closure and applies it to a {@link ZoneConfigBuilder}. Mirrors the
   * {@code ShipClosure} pattern in {@link GroovyShipLoader}.
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

    ZoneConfig build() {
      return new ZoneConfig(
          List.copyOf(autoLoadArenas), enterSpawnArena, scriptPollIntervalSeconds);
    }
  }
}
