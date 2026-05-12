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
 * Stateless pipeline: resolve source (filesystem-first dev mode, classpath
 * fallback via {@link #resolveOnDisk}), build a hardened {@link GroovyShell},
 * run the adapter's DSL bindings, evaluate, extract. Any failure logs and
 * returns the adapter's {@link GroovySettingsAdapter#empty} sentinel —
 * nothing propagates. Live-reload polling cadence is the caller's
 * responsibility; this host owns I/O resolution but not polling.
 */
public final class GroovySettingsHost {

  private static final Logger log = LoggerFactory.getLogger(GroovySettingsHost.class);

  public static final GroovySettingsHost INSTANCE = new GroovySettingsHost();

  /** {@code null} = file missing; {@code adapter.empty()} = file present but broken (logged). */
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

  /** Test-only; production callers use {@link #load}. */
  <T, A> T evaluate(
      final GroovySettingsAdapter<T, A> adapter, final String source, final String sourceName) {
    try {
      return evaluateInternal(adapter, source, sourceName);
    } catch (final Exception e) {
      log.warn("{} failed to evaluate; using empty", sourceName, e);
      return adapter.empty();
    }
  }

  /** {@link #evaluate} without the catch-all; for adapters that classify exceptions themselves (e.g. cycle/depth). */
  <T, A> T evaluateOrThrow(
      final GroovySettingsAdapter<T, A> adapter, final String source, final String sourceName) {
    return evaluateInternal(adapter, source, sourceName);
  }


  /** Resolves {@code classpathPath} → {@code zone/<relative>} on disk; {@code null} if not reachable. */
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

  /** {@code null} = neither fs nor classpath candidate reachable; throws on real I/O errors. */
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
      // Default imports — scripts use `Ship.WARBIRD` without an explicit import line.
      final ImportCustomizer imports = new ImportCustomizer();
      imports.addImports(allowed.toArray(new String[0]));
      cc.addCompilationCustomizers(imports);
    }
    cc.addCompilationCustomizers(secureCustomizer(allowed));

    final GroovyShell shell = new GroovyShell(binding, cc);
    shell.evaluate(source, path);

    return adapter.extract(accumulator);
  }

  /** Blocks every explicit import not in {@code allowed}; auto-imports ({@code java.lang.*}, etc.) are unaffected. */
  private static SecureASTCustomizer secureCustomizer(final List<String> allowed) {
    final SecureASTCustomizer sec = new SecureASTCustomizer();
    sec.setImportsWhitelist(allowed);
    sec.setStaticImportsWhitelist(allowed);
    sec.setStarImportsWhitelist(allowed);
    sec.setStaticStarImportsWhitelist(allowed);
    return sec;
  }
}
