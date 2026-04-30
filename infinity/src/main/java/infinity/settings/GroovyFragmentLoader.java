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
import groovy.lang.GroovyObjectSupport;
import groovy.lang.GroovyShell;
import infinity.Ship;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import javax.annotation.Nullable;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.ini4j.Ini;
import org.ini4j.Profile.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates a Groovy preset fragment script and returns its content as an
 * {@link Ini} so it can merge into the per-arena settings store via
 * {@link SettingsSystem#loadFragments} alongside the still-INI fragments.
 *
 * <p>Mirrors {@link GroovyShipLoader} / {@link GroovyZoneLoader} in style:
 * filesystem-first dev-mode read, classpath fallback for packaged jars, and a
 * "log + return null" failure mode so a broken fragment never throws past the
 * caller. The caller (currently {@code SettingsSystem.loadFragments}) treats a
 * {@code null} return as "skip this fragment, log the warning, keep going" —
 * matching how the legacy {@code IniLoader} dispatch already handles missing
 * fragments.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * section('Bomb') {
 *     BombDamageLevel 750
 *     BombAliveTime   6000
 * }
 *
 * shipSection('Warbird') {
 *     SuperTime         6000
 *     BulletFireEnergy  20
 * }
 *
 * shipSections('Warbird', 'Javelin', 'Spider') {
 *     InitialBurst 1
 *     InitialDecoy 2
 * }
 * }</pre>
 *
 * <p>The {@code Ship} enum is on the classpath of the script (via the imports
 * customizer) for parity with {@code ships.groovy}, even though the DSL takes
 * ship names as strings (so unmigrated keys can stay verbatim from the INI
 * surface). Inside a {@code shipSection} / {@code shipSections} block the name
 * is validated against {@link Ship} so typos fail loudly.
 *
 * <p>Recursive {@code include '/conf/.../x.groovy'} support is intentionally
 * deferred — it lands when the first preset that needs it migrates
 * ({@code svs-league/}, which composes {@code svs/} with overrides).
 */
public final class GroovyFragmentLoader {

  /**
   * Maximum recursive {@code include} depth before bailing — matches the
   * value baked into the INI {@code IniLoader} preprocessor so the two
   * include systems behave identically.
   */
  static final int MAX_INCLUDE_DEPTH = 16;

  private static final Logger log = LoggerFactory.getLogger(GroovyFragmentLoader.class);

  /**
   * Load and evaluate the Groovy fragment at {@code classpathPath}. Returns
   * {@code null} when the file is missing or the script fails to evaluate
   * (logs a warning either way) — callers treat {@code null} as "skip this
   * fragment", matching how the legacy INI dispatch already behaves.
   *
   * @param classpathPath classpath-absolute path (e.g. {@code "/conf/svs/cost.groovy"})
   * @return the parsed fragment as an {@link Ini}, or {@code null} on miss/failure
   */
  @Nullable
  public Ini load(final String classpathPath) {
    final Ini ini = new Ini();
    final Deque<String> stack = new ArrayDeque<>();
    if (!loadInto(ini, classpathPath, stack)) {
      return null;
    }
    return ini;
  }

  /**
   * Recursive entry point used by both the public {@link #load} and the
   * {@code include} script keyword. Reads the file at {@code classpathPath}
   * and applies its contents to {@code ini}; {@code stack} tracks
   * in-progress includes so cycles surface as {@link IllegalStateException}
   * rather than infinite recursion / stack overflow.
   *
   * @return {@code true} on a successful evaluation, {@code false} if the
   *     file was missing or evaluated but failed (warning logged either
   *     way). Cycles + depth violations propagate as exceptions so the
   *     parent script eval surfaces them too — those are author bugs, not
   *     soft "skip and continue" conditions.
   */
  private boolean loadInto(
      final Ini ini, final String classpathPath, final Deque<String> stack) {
    if (stack.contains(classpathPath)) {
      throw new IllegalStateException(
          "include cycle detected: " + describeChain(stack, classpathPath));
    }
    if (stack.size() >= MAX_INCLUDE_DEPTH) {
      throw new IllegalStateException(
          "include depth exceeded "
              + MAX_INCLUDE_DEPTH
              + " at "
              + classpathPath
              + " (chain: "
              + describeChain(stack, classpathPath)
              + ")");
    }
    final String source;
    try {
      source = readSource(classpathPath);
    } catch (final IOException e) {
      log.warn("Fragment {} failed to read", classpathPath, e);
      return false;
    }
    if (source == null) {
      log.warn("Fragment {} not found on filesystem or classpath", classpathPath);
      return false;
    }
    stack.push(classpathPath);
    try {
      evaluateInto(source, classpathPath, ini, stack);
    } catch (final IllegalStateException cycleOrDepth) {
      // Author bug — propagate so the outermost load() reports it instead of swallowing.
      throw cycleOrDepth;
    } catch (final Exception e) {
      log.warn("Fragment {} failed to evaluate", classpathPath, e);
      return false;
    } finally {
      stack.pop();
    }
    return true;
  }

  private static String describeChain(final Deque<String> stack, final String tail) {
    // stack is LIFO; convert to a left-to-right "outer -> inner -> tail" chain.
    final StringBuilder sb = new StringBuilder();
    final java.util.List<String> reversed = new java.util.ArrayList<>(stack);
    java.util.Collections.reverse(reversed);
    for (final String s : reversed) {
      sb.append(s).append(" -> ");
    }
    sb.append(tail);
    return sb.toString();
  }

  /**
   * Resolve {@code classpathPath} to an on-disk file when a dev-mode source
   * exists, or {@code null} when only the classpath copy is reachable. Public
   * so callers wiring a file watcher (parallel to the per-arena
   * {@code ships.groovy} reload path in {@code ArenaSystem}) can stat / poll
   * the same file the loader actually reads from.
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
    // Dev mode: filesystem-first so edits show up without a rebuild. Same
    // rationale as GroovyShipLoader.readSource — Gradle's :infinity:run sets
    // the JVM working directory to the infinity/ subproject and zone/ is the
    // resource root, so /conf/x.groovy on the classpath maps to zone/conf/x.groovy
    // on disk. The "infinity/zone" candidate covers running from the project root.
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
   * Package-private — exposed so unit tests can drive the evaluation pipeline
   * with a literal source string and no on-disk file. Production callers go
   * through {@link #load} (and recursive {@code include} calls re-enter via
   * {@link #loadInto}).
   *
   * <p>Kept on the class for test-source compatibility; internally delegates to
   * {@link #evaluateInto} with a fresh {@link Ini} and a stack pre-seeded with
   * {@code path} so an {@code include} of the same path is treated as a cycle.
   */
  Ini evaluate(final String source, final String path) {
    final Ini ini = new Ini();
    final Deque<String> stack = new ArrayDeque<>();
    stack.push(path);
    try {
      evaluateInto(source, path, ini, stack);
    } finally {
      stack.pop();
    }
    return ini;
  }

  private void evaluateInto(
      final String source, final String path, final Ini ini, final Deque<String> stack) {
    final CompilerConfiguration cc = new CompilerConfiguration();
    final ImportCustomizer imports = new ImportCustomizer();
    imports.addImports(Ship.class.getName());
    cc.addCompilationCustomizers(imports);

    final Binding binding = new Binding();
    binding.setVariable("section", new SectionClosure(ini, /* validateAsShip */ false));
    binding.setVariable("shipSection", new SectionClosure(ini, /* validateAsShip */ true));
    binding.setVariable("shipSections", new ShipSectionsClosure(ini));
    binding.setVariable("include", new IncludeClosure(this, ini, stack));

    final GroovyShell shell = new GroovyShell(binding, cc);
    shell.evaluate(source, path);
  }

  /**
   * Bound to the {@code section} / {@code shipSection} variables in the script.
   * Takes a section name and a configuring closure; populates the Ini section
   * via the {@link SectionDelegate}'s {@code methodMissing} (so each
   * {@code KeyName value} line in the body becomes a put).
   */
  private static final class SectionClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final Ini ini;
    private final boolean validateAsShip;

    SectionClosure(final Ini ini, final boolean validateAsShip) {
      super(null);
      this.ini = ini;
      this.validateAsShip = validateAsShip;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final String name, final Closure<?> body) {
      if (validateAsShip) {
        validateShipName(name);
      }
      applySection(ini, name, body);
      return null;
    }
  }

  /**
   * Bound to the {@code shipSections} variable. Takes one or more ship-name
   * strings followed by a configuring closure, validates each name against
   * {@link Ship}, and applies the closure body once per name so the same key
   * block lands under multiple section headers (the Groovy form of the INI
   * idiom {@code [Warbird] #include shared; [Javelin] #include shared; …}).
   */
  private static final class ShipSectionsClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final Ini ini;

    ShipSectionsClosure(final Ini ini) {
      super(null);
      this.ini = ini;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final Object... args) {
      if (args == null || args.length < 2) {
        throw new IllegalArgumentException(
            "shipSections requires at least one ship name and a configuring closure");
      }
      final Object last = args[args.length - 1];
      if (!(last instanceof Closure<?> body)) {
        throw new IllegalArgumentException(
            "shipSections last argument must be a configuring closure (got " + last + ")");
      }
      // Validate every ship name first so a typo in arg 5 of 8 still fails before
      // any sections are mutated — partial application would be confusing.
      for (int i = 0; i < args.length - 1; i++) {
        if (!(args[i] instanceof String name)) {
          throw new IllegalArgumentException(
              "shipSections ship-name args must be strings (got " + args[i] + " at index " + i + ")");
        }
        validateShipName(name);
      }
      for (int i = 0; i < args.length - 1; i++) {
        applySection(ini, (String) args[i], body);
      }
      return null;
    }
  }

  /**
   * Bound to the {@code include} variable. Takes a single classpath-absolute
   * path and recursively loads that fragment into the same {@link Ini} the
   * including script writes to (so includes contribute to the same merged
   * result, last-writer-wins on key conflict — matches INI {@code #include}
   * semantics).
   */
  private static final class IncludeClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final GroovyFragmentLoader loader;
    private final Ini ini;
    private final Deque<String> stack;

    IncludeClosure(
        final GroovyFragmentLoader loader, final Ini ini, final Deque<String> stack) {
      super(null);
      this.loader = loader;
      this.ini = ini;
      this.stack = stack;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final String path) {
      if (path == null || path.isBlank()) {
        throw new IllegalArgumentException("include requires a non-blank classpath path");
      }
      loader.loadInto(ini, path, stack);
      return null;
    }
  }

  private static void applySection(final Ini ini, final String name, final Closure<?> body) {
    Section sec = ini.get(name);
    if (sec == null) {
      sec = ini.add(name);
    }
    final SectionDelegate delegate = new SectionDelegate(sec);
    body.setDelegate(delegate);
    body.setResolveStrategy(Closure.DELEGATE_FIRST);
    body.call();
  }

  private static void validateShipName(final String name) {
    if (name == null) {
      throw new IllegalArgumentException("Ship name must not be null");
    }
    try {
      Ship.valueOf(name.toUpperCase(Locale.ROOT));
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Unknown Ship '" + name + "'; expected one of " + java.util.Arrays.toString(Ship.values()),
          e);
    }
  }

  /**
   * Delegate for a {@code section('Foo') { ... }} block. Each line in the
   * body looks like {@code KeyName 1234} or {@code KeyName 'string'} —
   * Groovy dispatches that as a method call with one positional argument
   * which lands in {@link #invokeMethod}; we translate it into a single
   * put on the underlying ini4j {@link Section}.
   *
   * <p>Extends {@link GroovyObjectSupport} so {@code invokeMethod} is the
   * MetaClass's method-dispatch hook — for plain Java classes the Groovy
   * runtime won't surface a {@code methodMissing} method on its own.
   */
  public static final class SectionDelegate extends GroovyObjectSupport {

    private final Section section;

    // Package-private so unit tests can put without standing up a GroovyShell.
    SectionDelegate(final Section section) {
      this.section = section;
    }

    /**
     * Catch-all dispatch from Groovy: every {@code KeyName value} line in a
     * section block lands here. Stores the value as a string (matching
     * ini4j's typing) so downstream {@code SettingsSystem.getInt} /
     * {@code getBool} parse Groovy-sourced values the same way they parse
     * INI-sourced ones.
     */
    @Override
    public Object invokeMethod(final String name, final Object args) {
      final Object[] arr = args instanceof Object[] objs ? objs : new Object[] {args};
      if (arr.length != 1) {
        throw new IllegalArgumentException(
            "section key '" + name + "' takes exactly one value (got " + arr.length + " args)");
      }
      final Object value = arr[0];
      if (value == null) {
        throw new IllegalArgumentException(
            "section key '" + name + "' value must not be null");
      }
      // ini4j Section.put returns the previous value; ignore — within one fragment
      // we're effectively the first writer, and last-wins is the documented contract
      // when the same key is set twice.
      section.put(name, String.valueOf(value));
      return null;
    }
  }
}
