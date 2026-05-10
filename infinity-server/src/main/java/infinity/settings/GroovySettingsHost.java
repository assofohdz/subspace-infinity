// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

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
import org.codehaus.groovy.control.customizers.ImportCustomizer;
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

  /**
   * Load a Groovy settings file at {@code classpathPath}. Distinguishes the
   * two failure modes callers may need to handle differently:
   *
   * <ul>
   *   <li>{@code null} — the file does not exist on filesystem or classpath.
   *       Callers can treat this as "no config supplied" (e.g. unmigrated
   *       arena, optional fragment).
   *   <li>{@code adapter.empty()} — the file exists but failed to read,
   *       compile, or evaluate. The error is logged; the sentinel signals
   *       "broken config, use defaults" without propagating an exception.
   * </ul>
   *
   * <p>Callers that don't need the distinction should coalesce: {@code cfg ==
   * null ? adapter.empty() : cfg}.
   *
   * <p>Never throws.
   */
  @Nullable
  public <T, A> T load(final GroovySettingsAdapter<T, A> adapter, final String classpathPath) {
    final String source;
    try {
      source = readSource(classpathPath);
    } catch (final IOException e) {
      log.warn("{} failed to read; using empty", classpathPath, e);
      return adapter.empty();
    }
    if (source == null) {
      log.debug("{} not found on filesystem or classpath; returning null", classpathPath);
      return null;
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
   * Evaluate a Groovy source string and propagate any exception. Same as
   * {@link #evaluate} but without the catch-all — used by adapters whose
   * caller logic needs to distinguish exception classes (e.g.
   * {@link GroovyFragmentLoader} re-throws cycle / depth
   * {@link IllegalStateException}s but soft-skips ordinary parse / eval
   * errors).
   *
   * <p>Package-private; production callers should prefer {@link #load} or
   * {@link #evaluate}.
   */
  <T, A> T evaluateOrThrow(
      final GroovySettingsAdapter<T, A> adapter, final String source, final String sourceName) {
    return evaluateInternal(adapter, source, sourceName);
  }


  /**
   * Resolve {@code classpathPath} to an on-disk file when a working-dir-relative
   * source exists, or {@code null} when only the classpath copy is reachable.
   * Public so file watchers (e.g. arena ships.groovy reload) can stat / poll
   * the same file the loader actually reads from.
   *
   * <p>Working-dir convention (post arch-review-megasplit): {@code zone/} lives at
   * the project root in dev (run/test set {@code workingDir = rootProject.projectDir})
   * and at the dist root in production (the gradle application dist places
   * {@code zone/} alongside {@code bin/} and {@code lib/}). Both resolve via
   * {@code Paths.get("zone", relative)}.
   */
  @Nullable
  public Path resolveOnDisk(final String classpathPath) {
    if (classpathPath == null || classpathPath.isBlank()) {
      return null;
    }
    final String relative =
        classpathPath.startsWith("/") ? classpathPath.substring(1) : classpathPath;
    final Path candidate = Paths.get("zone", relative);
    if (Files.isReadable(candidate)) {
      return candidate;
    }
    return null;
  }

  /**
   * Read a Groovy source file at {@code classpathPath}. Filesystem-first
   * dev-mode lookup, classpath fallback for packaged jars. Returns
   * {@code null} when neither candidate is reachable; throws on actual I/O
   * errors so callers can distinguish "file does not exist" (recoverable)
   * from "file exists but cannot be read" (probably a real problem).
   *
   * <p>Package-private; production callers go through {@link #load}.
   * Exposed for {@link GroovyFragmentLoader}'s recursive {@code include}
   * pipeline, which needs to read each included source separately while
   * sharing a single accumulator across the whole tree.
   */
  @Nullable
  String readSource(final String classpathPath) throws IOException {
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

    final List<String> allowed =
        adapter.allowedImports() == null ? Collections.emptyList() : adapter.allowedImports();

    final CompilerConfiguration cc = new CompilerConfiguration();
    if (!allowed.isEmpty()) {
      // Default imports: scripts can use these names without an explicit
      // `import` statement (e.g. `Ship.WARBIRD` works without `import infinity.Ship`).
      final ImportCustomizer imports = new ImportCustomizer();
      imports.addImports(allowed.toArray(new String[0]));
      cc.addCompilationCustomizers(imports);
    }
    cc.addCompilationCustomizers(secureCustomizer(allowed));

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
    sec.setImportsWhitelist(allowed);
    sec.setStaticImportsWhitelist(allowed);
    sec.setStarImportsWhitelist(allowed);
    sec.setStaticStarImportsWhitelist(allowed);
    return sec;
  }
}
