// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import groovy.lang.GroovyObjectSupport;
import org.junit.Test;

/**
 * Pins the {@link SingleClosureAdapter} contract — the generic helper every
 * single-block adapter (Bomb, Brick, Bullet, Burst, Decoy, Mine, Portal, Prize,
 * Repel, Rocket, Spawn, PrizeWeights) extends. See REFERENCE.md (per-section).
 */
public class SingleClosureAdapterTest {

  /** Trivial accumulator — captures one int written by {@code put N}. */
  private static final class IntBox {
    int value;
  }

  /** Default-delegate adapter — DSL is {@code testBlock { put 42 }}. */
  private static final class TestAdapter
      extends SingleClosureAdapter<Integer, IntBox> {

    static final Integer SENTINEL_EMPTY = -1;

    TestAdapter() {
      super("testBlock", SENTINEL_EMPTY);
    }

    @Override
    protected IntBox newBuilder() {
      return new IntBox();
    }

    @Override
    public Integer extract(final IntBox accumulator) {
      // value == 0 stays 0; never collapse to null (host treats null as "no
      // result" but the contract is "return extract output" — the empty()
      // sentinel only fires on eval failure, not script-no-op).
      return accumulator.value;
    }

  }

  /** Adapter that overrides delegateFor — DSL is {@code testBlock { setVerbatim 99 }}. */
  private static final class CustomDelegateAdapter
      extends SingleClosureAdapter<Integer, IntBox> {

    CustomDelegateAdapter() {
      super("testBlock", -1);
    }

    @Override
    protected IntBox newBuilder() {
      return new IntBox();
    }

    @Override
    public Integer extract(final IntBox accumulator) {
      return accumulator.value;
    }

    @Override
    protected Object delegateFor(final IntBox builder) {
      return new GroovyObjectSupport() {
        @Override
        public Object invokeMethod(final String name, final Object args) {
          // Verbatim-store: name → value mapping. Test below pins the contract
          // that PrizeWeightsAdapter relies on.
          if ("setVerbatim".equals(name)) {
            final Object[] arr =
                args instanceof Object[] objs ? objs : new Object[] {args};
            builder.value = ((Number) arr[0]).intValue();
            return null;
          }
          throw new UnsupportedOperationException("unknown: " + name);
        }
      };
    }
  }

  @Test
  public void variableName_isExposedAsTopLevelDslKeyword_andNoOpScriptYieldsBuilderDefaults() {
    // The adapter's variable name is the only top-level Groovy entry point;
    // a script that doesn't call testBlock {…} leaves the accumulator at its
    // builder defaults (here, IntBox.value == 0). The empty() sentinel fires
    // on eval failure, not on script-no-op.
    final TestAdapter adapter = new TestAdapter();
    final Integer result =
        GroovySettingsHost.INSTANCE.evaluate(adapter, "// nothing\n", "test:singleClosure_noBlock");

    assertEquals(Integer.valueOf(0), result);
  }

  @Test
  public void evalFailure_returnsAdapterEmptySentinel() {
    // Pin the empty()-on-eval-failure path: a thrown script returns SENTINEL_EMPTY.
    final TestAdapter adapter = new TestAdapter();
    final Integer result =
        GroovySettingsHost.INSTANCE.evaluate(
            adapter, "throw new RuntimeException('boom')", "test:singleClosure_throws");

    assertSame(TestAdapter.SENTINEL_EMPTY, result);
  }

  @Test
  public void emptySentinel_returnedOnAdapterEmptyContract() {
    final TestAdapter adapter = new TestAdapter();
    assertSame(TestAdapter.SENTINEL_EMPTY, adapter.empty());
  }

  @Test
  public void newBuilder_returnsFreshAccumulatorPerEvaluation() {
    // Accumulator must be per-bind, not shared, so concurrent loads (and
    // re-evaluation on hot-reload) don't bleed state.
    final TestAdapter adapter = new TestAdapter();
    final IntBox a = invokeNewBuilder(adapter);
    final IntBox b = invokeNewBuilder(adapter);

    assertNotSame("accumulator must be fresh per bind", a, b);
  }

  @Test
  public void delegateFor_defaultIsBuilderItself() {
    // Default delegateFor returns the accumulator. Adapters with public
    // builder methods (BombBuilder etc.) rely on this dispatch.
    final TestAdapter adapter = new TestAdapter();
    final IntBox box = new IntBox();
    final Object delegate = invokeDelegateFor(adapter, box);

    assertSame(box, delegate);
  }

  @Test
  public void delegateFor_overrideRoutesInvokeMethodDispatch() {
    // CustomDelegateAdapter overrides delegateFor → its block dispatches via
    // GroovyObjectSupport.invokeMethod, which is the contract
    // PrizeWeightsAdapter relies on for catch-all key handling.
    final CustomDelegateAdapter adapter = new CustomDelegateAdapter();
    final String src = "testBlock { setVerbatim 99 }\n";

    final Integer result =
        GroovySettingsHost.INSTANCE.evaluate(adapter, src, "test:singleClosure_customDelegate");

    assertEquals(Integer.valueOf(99), result);
  }

  @Test
  public void allowedImports_defaultIsEmpty() {
    final TestAdapter adapter = new TestAdapter();
    assertTrue("default allowed imports must be empty (most blocks need no imports)",
        adapter.allowedImports().isEmpty());
  }

  /** Reflective bridge to the protected newBuilder. */
  private static IntBox invokeNewBuilder(final TestAdapter adapter) {
    try {
      final java.lang.reflect.Method m =
          SingleClosureAdapter.class.getDeclaredMethod("newBuilder");
      m.setAccessible(true);
      return (IntBox) m.invoke(adapter);
    } catch (final ReflectiveOperationException e) {
      throw new AssertionError("newBuilder not accessible", e);
    }
  }

  /** Reflective bridge to the protected delegateFor. */
  private static Object invokeDelegateFor(final TestAdapter adapter, final IntBox box) {
    try {
      final java.lang.reflect.Method m =
          SingleClosureAdapter.class.getDeclaredMethod("delegateFor", Object.class);
      m.setAccessible(true);
      return m.invoke(adapter, box);
    } catch (final ReflectiveOperationException e) {
      throw new AssertionError("delegateFor not accessible", e);
    }
  }
}
