// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import java.util.List;
import java.util.Objects;

/**
 * Shared base class for adapters whose DSL is a single top-level
 * {@code variable { … }} block (e.g. {@code bullet { … }},
 * {@code bomb { … }}, {@code prizeWeights { … }}). Eliminates the per-adapter
 * boilerplate of {@code allowedImports() == empty}, {@code bind()} that
 * wires a {@code Closure} subclass to a builder, and the
 * subclass-per-adapter pattern that just forwarded {@code doCall(Closure
 * body)} to {@code body.call()} with delegate set.
 *
 * <p>The adapter owns:
 *
 * <ul>
 *   <li>The DSL <b>variable name</b> (passed to the constructor).
 *   <li>The <b>builder type</b> (created by {@link #newBuilder()} on each
 *       {@link #bind} invocation).
 *   <li>The <b>extract logic</b> (typically {@code accumulator.build()},
 *       declared via {@link #extract}).
 *   <li>The <b>empty sentinel</b> (typically {@code XConfig.DEFAULTS}, passed
 *       to the constructor).
 * </ul>
 *
 * <p>Most adapters take only those 4 inputs. Adapters whose body delegates
 * to something other than the builder itself (e.g. {@link PrizeWeightsAdapter}
 * uses a separate {@code GroovyObjectSupport} delegate that forwards each
 * dynamic key→value line to the builder) override {@link #delegateFor}.
 *
 * <p>Adapters that need explicit imports beyond the auto-imports
 * ({@code java.lang.*}, {@code java.util.*}) override
 * {@link #allowedImports} — none of the post-B1 fragment adapters do today.
 *
 * @param <C> typed config record this adapter produces
 * @param <B> mutable builder type the DSL closures write into
 */
public abstract class SingleClosureAdapter<C, B>
    implements GroovySettingsAdapter<C, B> {

  private final String variableName;
  private final C empty;

  protected SingleClosureAdapter(final String variableName, final C empty) {
    this.variableName = Objects.requireNonNull(variableName, "variableName");
    this.empty = Objects.requireNonNull(empty, "empty");
  }

  @Override
  public List<String> allowedImports() {
    return List.of();
  }

  @Override
  public final B bind(final Binding binding) {
    final B builder = newBuilder();
    binding.setVariable(variableName, new BlockClosure<>(this, builder));
    return builder;
  }

  @Override
  public final C empty() {
    return empty;
  }

  /** Construct a fresh, empty builder. Called once per {@link #bind}. */
  protected abstract B newBuilder();

  /**
   * Object the body closure delegates to. Defaults to the builder itself —
   * which is what most adapters want (the builder's setters are method
   * calls in the DSL body). Override to redirect when the DSL needs a
   * dispatching delegate (e.g. {@link PrizeWeightsAdapter} where each line
   * is a dynamic {@code Key Value} pair handled via {@code invokeMethod}).
   */
  protected Object delegateFor(final B builder) {
    return builder;
  }

  /**
   * Single shared {@link Closure} subclass used by every adapter — replaces
   * the per-adapter {@code XClosure extends Closure<Void>} that every
   * fragment adapter previously declared as a private inner class with an
   * identical body. Bound to the adapter's variable name in
   * {@link #bind}; invoked by Groovy as {@code doCall(Closure body)}.
   */
  private static final class BlockClosure<B> extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final transient SingleClosureAdapter<?, B> adapter;
    private final transient B builder;

    BlockClosure(final SingleClosureAdapter<?, B> adapter, final B builder) {
      super(null);
      this.adapter = adapter;
      this.builder = builder;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final Closure<?> body) {
      body.setDelegate(adapter.delegateFor(builder));
      body.setResolveStrategy(DELEGATE_FIRST);
      body.call();
      return null;
    }
  }
}
