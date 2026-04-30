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
import infinity.config.ArenaConfig;
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
 * Evaluates a per-arena Groovy config and returns a typed {@link ArenaConfig}.
 * Mirrors {@link GroovyZoneLoader} (which mirrors {@link GroovyShipLoader}) —
 * filesystem-first dev-mode read, classpath fallback, Closure DSL.
 *
 * <p>Intentionally returns {@code null} when the script is missing — callers
 * use that to decide whether to fall back to the legacy INI loader. Failure
 * to evaluate (parse error, eval error, IO error) returns
 * {@link ArenaConfig#EMPTY} and logs a warning, so a syntactically broken
 * Groovy file doesn't silently fall back to the INI as if no migration had
 * been attempted.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * arena {
 *     map '04-2026-trench/pub2025.lvl'
 *     shipsScript '/conf/trench-04-2026/ships.groovy'
 *     spawn 1000, 20
 *     wallFriction 0.0   // tangential friction on ship-vs-wall hits (0 = slidey)
 *     includeFragment '/conf/trench-04-2026/trench.conf'
 *     // includeFragment '/conf/another.conf' — repeat as needed
 * }
 * }</pre>
 *
 * <p>All directives are optional; an entirely empty script yields
 * {@link ArenaConfig#EMPTY}. Multiple {@code includeFragment} calls accumulate
 * in declaration order.
 */
public final class GroovyArenaLoader {

  /**
   * Classpath path under {@code zone/} where each arena's Groovy config lives.
   * The arena name is interpolated into the {@code <name>} slot.
   */
  public static final String ARENA_GROOVY_TEMPLATE = "/arenas/%s/arena.groovy";

  private static final Logger log = LoggerFactory.getLogger(GroovyArenaLoader.class);

  /**
   * Try to load and parse {@code /arenas/<arenaName>/arena.groovy}.
   *
   * @return the parsed {@link ArenaConfig}, or {@code null} if no Groovy file
   *     exists for this arena (caller should fall back to INI). Returns
   *     {@link ArenaConfig#EMPTY} on parse / eval failure (logged) so callers
   *     don't silently revert to INI on a broken Groovy file.
   */
  @Nullable
  public ArenaConfig load(final String arenaName) {
    final String classpathPath = String.format(ARENA_GROOVY_TEMPLATE, arenaName);
    return load(arenaName, classpathPath);
  }

  /** Same as {@link #load(String)} but with an explicit classpath path. */
  @Nullable
  public ArenaConfig load(final String arenaName, final String classpathPath) {
    final String source;
    try {
      source = readSource(classpathPath);
    } catch (final IOException e) {
      log.warn(
          "{} for arena {} failed to read; using ArenaConfig.EMPTY",
          classpathPath, arenaName, e);
      return ArenaConfig.EMPTY;
    }
    if (source == null) {
      // No Groovy file — caller falls back to INI. This is a normal
      // unmigrated-arena case, not a warning condition.
      log.debug("{} not found for arena {}; INI fallback expected", classpathPath, arenaName);
      return null;
    }
    try {
      final ArenaConfig cfg = evaluate(source, classpathPath);
      log.info(
          "Applied {} for arena {}: map='{}', ships='{}', spawn=({},{}), wallFriction={},"
              + " fragments={}",
          classpathPath, arenaName,
          cfg.mapFile(), cfg.shipsScript(), cfg.spawnX(), cfg.spawnZ(),
          cfg.wallFriction(),
          cfg.fragmentIncludes());
      return cfg;
    } catch (final Exception e) {
      log.warn(
          "{} for arena {} failed to evaluate; using ArenaConfig.EMPTY",
          classpathPath, arenaName, e);
      return ArenaConfig.EMPTY;
    }
  }

  /**
   * Resolve {@code classpathPath} to the on-disk source path when a dev-mode
   * candidate exists, or {@code null} otherwise. Public so a future file
   * watcher (parallel to the per-arena ships.groovy reload path in
   * {@code ArenaSystem}) can stat / poll the same file the loader actually
   * reads from.
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

  /**
   * Test-only entry point: parse {@code source} as if it had come from a file
   * at {@code virtualPath}. Returns {@link ArenaConfig#EMPTY} on parse / eval
   * failure, mirroring {@link #load}'s broken-script branch — lets tests
   * exercise that branch without writing to disk.
   */
  ArenaConfig evaluateSourceForTest(final String source, final String virtualPath) {
    try {
      return evaluate(source, virtualPath);
    } catch (final Exception e) {
      log.warn("test source {} failed to evaluate; using ArenaConfig.EMPTY", virtualPath, e);
      return ArenaConfig.EMPTY;
    }
  }

  private ArenaConfig evaluate(final String source, final String path) {
    final ArenaConfigBuilder builder = new ArenaConfigBuilder();

    final Binding binding = new Binding();
    binding.setVariable("arena", new ArenaClosure(builder));

    final GroovyShell shell = new GroovyShell(binding);
    shell.evaluate(source, path);

    return builder.build();
  }

  /** Bound to the {@code arena} variable; mirrors {@code ZoneClosure} from #1. */
  private static final class ArenaClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final ArenaConfigBuilder builder;

    ArenaClosure(final ArenaConfigBuilder builder) {
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
   * Delegate for the {@code arena { ... }} block. Fields default to
   * {@link ArenaConfig#EMPTY}'s values so partial scripts are valid; missing
   * directives mean "use the empty default."
   */
  public static final class ArenaConfigBuilder {

    private String mapFile = "";
    private String shipsScript = "";
    // Default to the arena's centre tile so an arena.groovy that omits the
    // `spawn` directive puts players in the middle of the map instead of the
    // NW corner. Mirrors ArenaConfig.EMPTY's spawn fallback.
    private int spawnX = ArenaConfig.EMPTY.spawnX();
    private int spawnZ = ArenaConfig.EMPTY.spawnZ();
    private final List<String> fragmentIncludes = new ArrayList<>();
    private double wallFriction = ArenaConfig.EMPTY.wallFriction();

    // Package-private so tests can build configs without the full GroovyShell.
    ArenaConfigBuilder() {}

    public void map(final String mapFile) {
      this.mapFile = mapFile == null ? "" : mapFile.trim();
    }

    public void shipsScript(final String shipsScript) {
      this.shipsScript = shipsScript == null ? "" : shipsScript.trim();
    }

    public void spawn(final int x, final int z) {
      this.spawnX = x;
      this.spawnZ = z;
    }

    public void includeFragment(final String classpathPath) {
      if (classpathPath != null && !classpathPath.isBlank()) {
        fragmentIncludes.add(classpathPath.trim());
      }
    }

    /**
     * Per-contact fraction of <i>tangential</i> velocity drained on
     * ship-vs-wall hits (sliding-deceleration). Not the resolver's standard
     * Coulomb friction — that would torque the body's heading at off-center
     * contact points, which is wrong for arcade ship physics. See
     * {@link ArenaConfig#wallFriction()} for the full semantic and tuning
     * guidance. {@code 0.0} (default) keeps walls frictionless. Values
     * outside {@code [0, 1]} are rejected.
     */
    public void wallFriction(final Number value) {
      if (value == null) {
        throw new IllegalArgumentException("wallFriction requires a number");
      }
      final double v = value.doubleValue();
      if (Double.isNaN(v) || Double.isInfinite(v) || v < 0.0 || v > 1.0) {
        throw new IllegalArgumentException(
            "wallFriction must be a finite value in [0, 1]; got " + value);
      }
      this.wallFriction = v;
    }

    ArenaConfig build() {
      return new ArenaConfig(
          mapFile, shipsScript, spawnX, spawnZ, List.copyOf(fragmentIncludes), wallFriction);
    }
  }
}
