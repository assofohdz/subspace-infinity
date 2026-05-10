// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import groovy.lang.Binding;
import groovy.lang.Closure;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Pipeline-level tests for {@link GroovySettingsHost}: I/O resolution,
 * the error-to-empty contract, and {@code SecureASTCustomizer}-based
 * hardening are owned by the host, so they're tested here rather than in any
 * one adapter's test class. Adapters get their own DSL-semantics tests.
 *
 * <p>The hardening / throw paths use the package-private
 * {@link GroovySettingsHost#evaluate} entry point so the tests don't need
 * classpath fixtures.
 */
public class GroovySettingsHostTest {

  /** Trivial accumulator: holds whatever the script's {@code put} closure stores. */
  private static final class StringBox {
    String value;
  }

  /** Adapter used by every test. Allowed-imports configurable per case. */
  private static final class TestAdapter implements GroovySettingsAdapter<String, StringBox> {

    static final String SENTINEL_EMPTY = "EMPTY";

    private final List<String> imports;

    TestAdapter() {
      this(Collections.emptyList());
    }

    TestAdapter(final List<String> imports) {
      this.imports = imports;
    }

    @Override
    public List<String> allowedImports() {
      return imports;
    }

    @Override
    public StringBox bind(final Binding binding) {
      final StringBox box = new StringBox();
      binding.setVariable(
          "put",
          new Closure<Void>(null) {
            private static final long serialVersionUID = 1L;

            @SuppressWarnings("unused") // invoked via Groovy dispatch
            public Void doCall(final Object v) {
              box.value = String.valueOf(v);
              return null;
            }
          });
      return box;
    }

    @Override
    public String extract(final StringBox accumulator) {
      return accumulator.value;
    }

    @Override
    public String empty() {
      return SENTINEL_EMPTY;
    }
  }

  @Test
  public void evaluate_validScript_returnsExtractedResult() {
    final String result =
        GroovySettingsHost.INSTANCE.evaluate(new TestAdapter(), "put 'hello'", "test:valid");

    assertEquals("hello", result);
  }

  @Test
  public void evaluate_throwingScript_returnsAdapterEmpty() {
    final String result =
        GroovySettingsHost.INSTANCE.evaluate(
            new TestAdapter(), "throw new RuntimeException('boom')", "test:throwing");

    assertSame("eval-throws path must return adapter.empty()", TestAdapter.SENTINEL_EMPTY, result);
  }

  @Test
  public void evaluate_disallowedImport_isRejectedAtCompileAndReturnsEmpty() {
    // Empty allowed-imports = block every explicit import.
    // SecureASTCustomizer raises a compilation error; host catches and
    // returns adapter.empty() rather than propagating.
    final String src = "import java.util.Date\nput new Date()";
    final String result =
        GroovySettingsHost.INSTANCE.evaluate(new TestAdapter(), src, "test:forbiddenImport");

    assertSame(
        "forbidden import must be rejected at compile time → empty",
        TestAdapter.SENTINEL_EMPTY,
        result);
  }

  @Test
  public void evaluate_allowedImport_passesAndScriptRuns() {
    final TestAdapter adapter = new TestAdapter(List.of("java.util.Date"));
    final String src = "import java.util.Date\nput 'imported-ok'";

    final String result =
        GroovySettingsHost.INSTANCE.evaluate(adapter, src, "test:allowedImport");

    assertEquals("whitelisted import must compile and the script must run", "imported-ok", result);
  }

  @Test
  public void evaluate_allowedImport_isAlsoAvailableAsDefaultImport() {
    // Whitelisted classes are also added as default imports so scripts can
    // use the short name without an explicit `import` statement (e.g.
    // GroovyShipLoader's `Ship.WARBIRD` works without `import infinity.Ship`).
    final TestAdapter adapter = new TestAdapter(List.of("java.util.Date"));
    final String src = "put new Date(0L)"; // no `import` line — relies on default import

    final String result =
        GroovySettingsHost.INSTANCE.evaluate(adapter, src, "test:defaultImport");

    // java.util.Date.toString() at epoch 0 is timezone-dependent; just assert
    // the script ran and produced a non-null result. The point is that
    // `Date` resolved without an explicit import.
    assertEquals(
        "default import lets the script use Date without an explicit import",
        true,
        result != null && !TestAdapter.SENTINEL_EMPTY.equals(result));
  }

  @Test
  public void load_missingFile_returnsNull() {
    // null on file-not-found is the primitive callers like GroovyArenaLoader
    // need to distinguish "no Groovy file for this arena" (fail-fast) from
    // "Groovy file exists but is broken" (use defaults). Callers that don't
    // care coalesce: `cfg == null ? adapter.empty() : cfg`.
    final String result =
        GroovySettingsHost.INSTANCE.load(new TestAdapter(), "/nope-not-on-classpath.groovy");

    assertNull("missing file must return null, not adapter.empty()", result);
  }

  @Test
  public void resolveOnDisk_blankPath_returnsNull() {
    assertNull(GroovySettingsHost.INSTANCE.resolveOnDisk(null));
    assertNull(GroovySettingsHost.INSTANCE.resolveOnDisk(""));
    assertNull(GroovySettingsHost.INSTANCE.resolveOnDisk("   "));
  }

  @Test
  public void resolveOnDisk_unknownPath_returnsNull() {
    assertNull(
        "no zone/ or infinity/zone/ candidate exists for this path",
        GroovySettingsHost.INSTANCE.resolveOnDisk("/this-file-is-fictional.groovy"));
  }
}
