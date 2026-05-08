// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import infinity.Ship;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import org.ini4j.Ini;
import org.ini4j.Profile;
import org.ini4j.Profile.Section;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Evaluates a Groovy preset fragment script and returns its content as an
 * {@link Ini} so it can merge into the per-arena settings store via
 * {@link SettingsSystem#loadFragments} alongside the still-INI fragments.
 * Thin facade over {@link GroovySettingsHost} for the I/O + eval pipeline;
 * the recursive {@code include} directive is owned here because the
 * accumulator (Ini) and cycle-detection stack must persist across each
 * include re-entry.
 *
 * <p>Failure modes:
 *
 * <ul>
 *   <li><b>Missing file or eval error</b> — log + return {@code null}. Callers
 *       (currently {@code SettingsSystem.loadFragments}) treat {@code null}
 *       as "skip this fragment, keep going."
 *   <li><b>Include cycle / depth violation</b> — propagated as
 *       {@link IllegalStateException}. Author bug; do not soft-skip.
 * </ul>
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
 *
 * include '/conf/svs/cost.groovy'
 * }</pre>
 *
 * <p>The {@code Ship} enum is added as a default import (and whitelisted) by
 * the host. Inside a {@code shipSection} / {@code shipSections} block the
 * name is validated against {@link Ship} so typos fail loudly.
 */
public final class GroovyFragmentLoader {

  /**
   * Maximum recursive {@code include} depth before bailing — matches the
   * value the retired {@code IniLoader} {@code #include} preprocessor used
   * so behaviour stays familiar to operators who hit the bound previously.
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
      final Profile ini, final String classpathPath, final Deque<String> stack) {
    assertNoCycleOrDepth(classpathPath, stack);
    final String source = readFragmentSource(classpathPath);
    if (source == null) {
      return false;
    }
    return evaluateInStack(ini, classpathPath, stack, source);
  }

  /** Throw {@link IllegalStateException} on either an include cycle or a depth-exceeded violation. */
  private static void assertNoCycleOrDepth(
      final String classpathPath, final Deque<String> stack) {
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
  }

  /**
   * Read the fragment's Groovy source from disk/classpath. Returns the source
   * string on success, or {@code null} if the file is missing or unreadable —
   * either case is logged as a warning and the caller treats as a soft skip.
   */
  private String readFragmentSource(final String classpathPath) {
    try {
      final String source = GroovySettingsHost.INSTANCE.readSource(classpathPath);
      if (source == null) {
        log.warn("Fragment {} not found on filesystem or classpath", classpathPath);
      }
      return source;
    } catch (final IOException e) {
      log.warn("Fragment {} failed to read", classpathPath, e);
      return null;
    }
  }

  /**
   * Push the path onto {@code stack}, evaluate {@code source} with a fresh
   * {@link FragmentAdapter}, then pop. Cycle/depth violations propagate so
   * the outermost {@code load()} surfaces them; arbitrary script exceptions
   * are logged + soft-skipped.
   */
  private boolean evaluateInStack(
      final Profile ini,
      final String classpathPath,
      final Deque<String> stack,
      final String source) {
    stack.push(classpathPath);
    try {
      final FragmentAdapter adapter = new FragmentAdapter(this, ini, stack);
      GroovySettingsHost.INSTANCE.evaluateOrThrow(adapter, source, classpathPath);
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
   * Package-private — exposed so unit tests can drive the evaluation pipeline
   * with a literal source string and no on-disk file. Production callers go
   * through {@link #load} (and recursive {@code include} calls re-enter via
   * {@link #loadInto}).
   *
   * <p>The stack is pre-seeded with {@code path} so an {@code include} of
   * the same path is treated as a 1-step cycle, matching production
   * semantics.
   */
  Ini evaluate(final String source, final String path) {
    final Ini ini = new Ini();
    final Deque<String> stack = new ArrayDeque<>();
    stack.push(path);
    try {
      final FragmentAdapter adapter = new FragmentAdapter(this, ini, stack);
      GroovySettingsHost.INSTANCE.evaluateOrThrow(adapter, source, path);
    } finally {
      stack.pop();
    }
    return ini;
  }

  /**
   * Adapter holding the fragment DSL semantics. Stateful: each instance is
   * scoped to one evaluation (one outer {@code load} or one {@code include}
   * re-entry), carrying the shared accumulator {@link Ini} and the
   * cycle-detection {@code stack}.
   */
  private static final class FragmentAdapter implements GroovySettingsAdapter<Profile, Profile> {

    /**
     * Sentinel returned by {@link #empty} when an outer caller hits the
     * normal {@link GroovySettingsHost#load} path. Never observed in
     * fragment usage today — {@link GroovyFragmentLoader} drives evaluation
     * via {@link GroovySettingsHost#evaluateOrThrow} so it can do its own
     * exception classification.
     */
    private static final Profile SENTINEL_BROKEN = new Ini();

    private final GroovyFragmentLoader loader;
    private final Profile ini;
    private final Deque<String> stack;

    FragmentAdapter(final GroovyFragmentLoader loader, final Profile ini, final Deque<String> stack) {
      this.loader = loader;
      this.ini = ini;
      this.stack = stack;
    }

    @Override
    public List<String> allowedImports() {
      // Ship enum is the only class fragment scripts reference directly.
      return List.of(Ship.class.getName());
    }

    @Override
    public Profile bind(final Binding binding) {
      binding.setVariable("section", new SectionClosure(ini, /* validateAsShip */ false));
      binding.setVariable("shipSection", new SectionClosure(ini, /* validateAsShip */ true));
      binding.setVariable("shipSections", new ShipSectionsClosure(ini));
      binding.setVariable("include", new IncludeClosure(loader, ini, stack));
      return ini;
    }

    @Override
    public Profile extract(final Profile accumulator) {
      return accumulator;
    }

    @Override
    public Profile empty() {
      return SENTINEL_BROKEN;
    }
  }

  /**
   * Bound to the {@code section} / {@code shipSection} variables in the script.
   * Takes a section name and a configuring closure; populates the Ini section
   * via the {@link SectionDelegate}'s {@code methodMissing} (so each
   * {@code KeyName value} line in the body becomes a put).
   */
  private static final class SectionClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final Profile ini;
    private final boolean validateAsShip;

    SectionClosure(final Profile ini, final boolean validateAsShip) {
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

    private final Profile ini;

    ShipSectionsClosure(final Profile ini) {
      super(null);
      this.ini = ini;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final Object... args) {
      final Closure<?> body = validateShipSectionsArgs(args);
      // Validate every ship name first so a typo in arg 5 of 8 still fails before
      // any sections are mutated — partial application would be confusing.
      for (int i = 0; i < args.length - 1; i++) {
        validateShipName((String) args[i]);
      }
      for (int i = 0; i < args.length - 1; i++) {
        applySection(ini, (String) args[i], body);
      }
      return null;
    }

    /**
     * Validate that {@code args} is the {@code (name, name, ..., closure)} shape
     * documented for {@code shipSections}. Throws {@link IllegalArgumentException}
     * on any structural mismatch; on success returns the trailing configuring
     * closure for the caller to drive per-section.
     */
    private static Closure<?> validateShipSectionsArgs(final Object... args) {
      if (args == null || args.length < 2) {
        throw new IllegalArgumentException(
            "shipSections requires at least one ship name and a configuring closure");
      }
      final Object last = args[args.length - 1];
      if (!(last instanceof Closure<?> body)) {
        throw new IllegalArgumentException(
            "shipSections last argument must be a configuring closure (got " + last + ")");
      }
      for (int i = 0; i < args.length - 1; i++) {
        if (!(args[i] instanceof String)) {
          throw new IllegalArgumentException(
              "shipSections ship-name args must be strings (got " + args[i] + " at index " + i + ")");
        }
      }
      return body;
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
    private final Profile ini;
    private final Deque<String> stack;

    IncludeClosure(
        final GroovyFragmentLoader loader, final Profile ini, final Deque<String> stack) {
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

  private static void applySection(final Profile ini, final String name, final Closure<?> body) {
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
