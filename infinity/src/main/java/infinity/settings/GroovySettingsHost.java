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
import groovy.lang.GroovyShell;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.SecureASTCustomizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stateless pipeline that evaluates a Groovy settings file end to end:
 * resolve the source (filesystem-first dev mode, classpath fallback), build
 * a hardened {@link GroovyShell}, run the adapter's DSL bindings, evaluate,
 * and extract a typed result. Any failure (file missing, parse error, eval
 * error) is logged and the adapter's {@link GroovySettingsAdapter#empty}
 * sentinel is returned; nothing propagates.
 *
 * <p>One host serves all adapters — see {@code CONTEXT.md} ("Settings host")
 * for the role this plays in the settings layer.
 *
 * <p><b>Live reload</b>: the host owns I/O resolution but not polling.
 * Callers (e.g. {@code ArenaSystem.pollScriptWatches}) drive their own
 * cadence and use {@link #resolveOnDisk} to stat the same file the loader
 * reads from.
 *
 * <p><b>Future extension</b>: when the modules loader lands (see
 * {@code .scratch/groovy-module-loader/PRD.md}) it will likely need
 * multi-source composition (one accumulator across N scripts in a directory).
 * The natural shape is a {@code loadAll(adapter, List&lt;String&gt;)} method
 * on this host. Don't pre-build it — add when there's a real consumer.
 */
public final class GroovySettingsHost {

  private static final Logger log = LoggerFactory.getLogger(GroovySettingsHost.class);

  /** Stateless; safe to share across threads. Convenience for callers that don't keep a field. */
  public static final GroovySettingsHost INSTANCE = new GroovySettingsHost();

  /** Load using the adapter's {@link GroovySettingsAdapter#defaultPath default path}. */
  public <T, A> T load(final GroovySettingsAdapter<T, A> adapter) {
    return load(adapter, adapter.defaultPath());
  }

  /**
   * Load a Groovy settings file at {@code classpathPath}. Returns
   * {@code adapter.empty()} on any failure. Never throws.
   */
  public <T, A> T load(final GroovySettingsAdapter<T, A> adapter, final String classpathPath) {
    final String source;
    try {
      source = readSource(classpathPath);
    } catch (final IOException e) {
      log.warn("{} failed to read; using empty", classpathPath, e);
      return adapter.empty();
    }
    if (source == null) {
      log.warn("{} not found on filesystem or classpath; using empty", classpathPath);
      return adapter.empty();
    }
    return evaluate(adapter, source, classpathPath);
  }

  /**
   * Evaluate a Groovy source string against {@code adapter}'s DSL bindings
   * and security whitelist. Same try/catch contract as {@link #load} —
   * compilation or evaluation failure logs and returns {@code adapter.empty()}.
   *
   * <p>Package-private; intended for tests that want to exercise the
   * evaluation + hardening pipeline without standing up classpath fixtures.
   * Production callers use {@link #load}.
   */
  <T, A> T evaluate(
      final GroovySettingsAdapter<T, A> adapter, final String source, final String sourceName) {
    try {
      return evaluateInternal(adapter, source, sourceName);
    } catch (final Exception e) {
      log.warn("{} failed to evaluate; using empty", sourceName, e);
      return adapter.empty();
    }
  }

  /**
   * Resolve {@code classpathPath} to an on-disk file when a dev-mode source
   * exists, or {@code null} when only the classpath copy is reachable.
   * Public so file watchers (e.g. arena ships.groovy reload) can stat / poll
   * the same file the loader actually reads from.
   *
   * <p>Dev-mode rationale: Gradle's {@code :infinity:run} sets the JVM
   * working directory to the {@code infinity/} subproject and {@code zone/}
   * is the resource root, so {@code /x.groovy} on the classpath maps to
   * {@code zone/x.groovy} on disk. The {@code infinity/zone} candidate
   * covers running from the project root.
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

  private <T, A> T evaluateInternal(
      final GroovySettingsAdapter<T, A> adapter, final String source, final String path) {
    final Binding binding = new Binding();
    final A accumulator = adapter.bind(binding);

    final CompilerConfiguration cc = new CompilerConfiguration();
    cc.addCompilationCustomizers(secureCustomizer(adapter.allowedImports()));

    final GroovyShell shell = new GroovyShell(binding, cc);
    shell.evaluate(source, path);

    return adapter.extract(accumulator);
  }

  /**
   * Build a {@link SecureASTCustomizer} that blocks every explicit import
   * not in {@code allowed}. Auto-imports ({@code java.lang.*},
   * {@code java.util.*}, etc.) are unaffected — they're a Groovy compiler
   * feature, not an {@code import} statement, so scripts can still reach
   * {@code String}, {@code List}, etc. without ceremony.
   */
  private static SecureASTCustomizer secureCustomizer(final List<String> allowed) {
    final SecureASTCustomizer sec = new SecureASTCustomizer();
    final List<String> safe = allowed == null ? Collections.emptyList() : allowed;
    sec.setImportsWhitelist(safe);
    sec.setStaticImportsWhitelist(safe);
    sec.setStarImportsWhitelist(safe);
    sec.setStaticStarImportsWhitelist(safe);
    return sec;
  }
}
