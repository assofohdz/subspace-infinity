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
import infinity.Ship;
import infinity.config.ShipConfig;
import infinity.config.ShipStat;
import infinity.es.arena.ArenaId;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import javax.annotation.Nullable;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates an arena's Groovy ship config and installs the result into
 * {@link ConfigRegistrySystem}. The caller supplies the classpath path to
 * the script (typically read from the arena's {@code [Scripts] Ships}
 * setting so the arena can point at any preset's script).
 *
 * <p>Failure handling — any failure (no path configured, missing file, parse
 * error, eval error, IO error) logs a warning and installs a built-in
 * fallback snapshot so the arena stays playable. Callers never see an
 * exception; see {@link #FALLBACK} for the default values.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * ship(Ship.WARBIRD) {
 *     rotation initial: 210, max: 300, upgrade: 40
 *     thrust   initial: 16,  max: 19,  upgrade: 2
 *     speed    initial: 2010, max: 3250, upgrade: 250
 *     recharge initial: 400,  max: 1150, upgrade: 166
 *     energy   initial: 1000, max: 1700, upgrade: 100
 * }
 * }</pre>
 *
 * <p>The {@code Ship} enum is made available as a default import. Stats
 * omitted in a ship block default to {@code ShipStat(0, 0, 0)}; partial stat
 * blocks (missing {@code initial}/{@code max}/{@code upgrade}) fail with a
 * clear error message.
 */
public final class GroovyShipLoader {

  private static final Logger log = LoggerFactory.getLogger(GroovyShipLoader.class);

  /**
   * Built-in fallback snapshot installed when an arena's {@code ships.groovy}
   * is missing or fails to evaluate. Warbird-only, SVS-canonical tuning
   * (matches the {@code conf/base/ship-warbird} INI fragment). Extend with
   * other ships once gameplay exercises them.
   */
  public static final ConfigRegistry FALLBACK =
      ConfigRegistry.builder()
          .ship(
              Ship.WARBIRD,
              new ShipConfig(
                  Ship.WARBIRD,
                  /* rotation */ new ShipStat(210, 300, 40),
                  /* thrust */ new ShipStat(16, 19, 2),
                  /* speed */ new ShipStat(2010, 3250, 250),
                  /* recharge */ new ShipStat(400, 1150, 166),
                  /* energy */ new ShipStat(1000, 1700, 100)))
          .build();

  private final ConfigRegistrySystem configRegistry;

  public GroovyShipLoader(final ConfigRegistrySystem configRegistry) {
    this.configRegistry = configRegistry;
  }

  /**
   * Load and install the ship config for {@code arenaId} from the given
   * classpath path. {@code null} or missing/broken script → log a warning
   * and install {@link #FALLBACK}. Never throws.
   *
   * @param arenaId the arena to populate
   * @param classpathPath classpath-absolute path to the script (e.g.
   *     {@code "/conf/trench-04-2026/ships.groovy"}), or {@code null} if
   *     no script is configured for this arena
   */
  public void apply(final ArenaId arenaId, @Nullable final String classpathPath) {
    if (classpathPath == null || classpathPath.isBlank()) {
      log.warn(
          "No [Scripts] Ships configured for arena {}; installing fallback defaults",
          arenaId.getArena());
      configRegistry.replace(arenaId, FALLBACK);
      return;
    }
    try {
      final String source = readSource(classpathPath);
      if (source == null) {
        log.warn(
            "ships.groovy for arena {} not found at {}; installing fallback defaults",
            arenaId.getArena(),
            classpathPath);
        configRegistry.replace(arenaId, FALLBACK);
        return;
      }
      final ConfigRegistry snapshot = evaluate(source, classpathPath);
      configRegistry.replace(arenaId, snapshot);
      log.info(
          "Applied {} for arena {} ({} ships configured)",
          classpathPath,
          arenaId.getArena(),
          snapshot.configuredShips().size());
      for (final infinity.Ship ship : snapshot.configuredShips()) {
        log.info("  parsed config: {} -> {}", ship, snapshot.getShip(ship));
      }
    } catch (final Exception e) {
      log.warn(
          "ships.groovy for arena {} at {} failed to evaluate; installing fallback defaults",
          arenaId.getArena(),
          classpathPath,
          e);
      configRegistry.replace(arenaId, FALLBACK);
    }
  }

  private ConfigRegistry evaluate(final String source, final String path) {
    final ConfigRegistry.Builder registryBuilder = ConfigRegistry.builder();

    final CompilerConfiguration cc = new CompilerConfiguration();
    final ImportCustomizer imports = new ImportCustomizer();
    imports.addImports(Ship.class.getName());
    cc.addCompilationCustomizers(imports);

    final Binding binding = new Binding();
    binding.setVariable("ship", new ShipClosure(registryBuilder));

    final GroovyShell shell = new GroovyShell(binding, cc);
    shell.evaluate(source, path);

    return registryBuilder.build();
  }

  @Nullable
  private String readSource(final String classpathPath) throws IOException {
    // Dev mode: try the filesystem source first so edits show up without a rebuild.
    // Gradle's :infinity:run sets the JVM working directory to the infinity/ subproject,
    // and zone/ is the resource root — so /conf/x.groovy on the classpath is zone/conf/x.groovy
    // on disk. The "infinity/zone" candidate covers the case where the JVM is launched
    // from the project root instead.
    final String relative =
        classpathPath.startsWith("/") ? classpathPath.substring(1) : classpathPath;
    final Path[] candidates = {Paths.get("zone", relative), Paths.get("infinity/zone", relative)};
    for (final Path p : candidates) {
      if (Files.isReadable(p)) {
        log.debug("Reading {} from filesystem source: {}", classpathPath, p);
        return Files.readString(p, StandardCharsets.UTF_8);
      }
    }
    // Production / packaged jar: fall back to classpath.
    try (InputStream is = getClass().getResourceAsStream(classpathPath)) {
      if (is == null) {
        return null;
      }
      log.debug("Reading {} from classpath", classpathPath);
      return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  /**
   * Bound to the {@code ship} variable in the script. Takes a {@link Ship}
   * type and a configuring closure; builds a {@link ShipConfig} and adds it
   * to the registry snapshot under construction.
   */
  private static final class ShipClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final ConfigRegistry.Builder registryBuilder;

    ShipClosure(final ConfigRegistry.Builder registryBuilder) {
      super(null);
      this.registryBuilder = registryBuilder;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final Ship type, final Closure<?> body) {
      final ShipConfigBuilder shipBuilder = new ShipConfigBuilder(type);
      body.setDelegate(shipBuilder);
      body.setResolveStrategy(DELEGATE_FIRST);
      body.call();
      registryBuilder.ship(type, shipBuilder.build());
      return null;
    }
  }

  /**
   * Delegate for a {@code ship(Ship.X) { ... }} block. Each stat method
   * accepts a Groovy named-argument map and stores a {@link ShipStat}.
   * Stats not called stay at {@code (0, 0, 0)}.
   */
  public static final class ShipConfigBuilder {

    private final Ship type;
    private ShipStat rotation = new ShipStat(0, 0, 0);
    private ShipStat thrust = new ShipStat(0, 0, 0);
    private ShipStat speed = new ShipStat(0, 0, 0);
    private ShipStat recharge = new ShipStat(0, 0, 0);
    private ShipStat energy = new ShipStat(0, 0, 0);

    private ShipConfigBuilder(final Ship type) {
      this.type = type;
    }

    public void rotation(final Map<String, ?> args) {
      this.rotation = toStat("rotation", args);
    }

    public void thrust(final Map<String, ?> args) {
      this.thrust = toStat("thrust", args);
    }

    public void speed(final Map<String, ?> args) {
      this.speed = toStat("speed", args);
    }

    public void recharge(final Map<String, ?> args) {
      this.recharge = toStat("recharge", args);
    }

    public void energy(final Map<String, ?> args) {
      this.energy = toStat("energy", args);
    }

    private static ShipStat toStat(final String statName, final Map<String, ?> args) {
      return new ShipStat(
          intArg(statName, args, "initial"),
          intArg(statName, args, "max"),
          intArg(statName, args, "upgrade"));
    }

    private static int intArg(
        final String statName, final Map<String, ?> args, final String key) {
      final Object v = args.get(key);
      if (v instanceof Number n) {
        return n.intValue();
      }
      throw new IllegalArgumentException(
          "Ship stat '" + statName + "' is missing numeric '" + key + "' (got " + v + ")");
    }

    ShipConfig build() {
      return new ShipConfig(type, rotation, thrust, speed, recharge, energy);
    }
  }
}
