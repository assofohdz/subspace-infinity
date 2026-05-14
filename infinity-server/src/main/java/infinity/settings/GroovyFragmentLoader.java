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

/** Evaluates a Groovy preset fragment script into an {@link Ini} for merging into the per-arena settings store; supports recursive {@code include}. */
public final class GroovyFragmentLoader {

  static final int MAX_INCLUDE_DEPTH = 16;

  private static final Logger log = LoggerFactory.getLogger(GroovyFragmentLoader.class);

  /** Returns the parsed fragment or {@code null} on missing/eval error (logged); cycles propagate. */
  @Nullable
  public Profile load(final String classpathPath) {
    final Profile ini = new Ini();
    final Deque<String> stack = new ArrayDeque<>();
    if (!loadInto(ini, classpathPath, stack)) {
      return null;
    }
    return ini;
  }

  /** Recursive entry point shared by {@link #load} and the {@code include} keyword. */
  private boolean loadInto(
      final Profile ini, final String classpathPath, final Deque<String> stack) {
    assertNoCycleOrDepth(classpathPath, stack);
    final String source = readFragmentSource(classpathPath);
    if (source == null) {
      return false;
    }
    return evaluateInStack(ini, classpathPath, stack, source);
  }

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

  /** Test seam — evaluate a literal source string with no on-disk file; stack pre-seeded so self-include is a 1-step cycle. */
  Profile evaluate(final String source, final String path) {
    final Profile ini = new Ini();
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

  /** Per-evaluation adapter; carries the shared accumulator + cycle-detection stack. */
  private static final class FragmentAdapter implements GroovySettingsAdapter<Profile, Profile> {

    // Sentinel only used by GroovySettingsHost#load; fragment eval bypasses it.
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

  /** Backing closure for the {@code section} / {@code shipSection} DSL keywords. */
  private static final class SectionClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final transient Profile ini;
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

  /** Backing closure for {@code shipSections('A','B',…){…}} — applies the body once per ship name. */
  private static final class ShipSectionsClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final transient Profile ini;

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

  /** Backing closure for {@code include 'path'} — last-writer-wins on key conflict. */
  private static final class IncludeClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final transient GroovyFragmentLoader loader;
    private final transient Profile ini;
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

  /** Delegate for {@code section('Foo'){…}}; each {@code KeyName value} line lands in {@link #invokeMethod}. */
  public static final class SectionDelegate extends GroovyObjectSupport {

    private final Section section;

    // Package-private so unit tests can put without standing up a GroovyShell.
    SectionDelegate(final Section section) {
      this.section = section;
    }

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
