// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import java.util.List;
import java.util.Objects;

/** Base for adapters whose DSL is one top-level {@code variable {…}} block. Override {@link #delegateFor} for non-builder dispatch. */
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

  /** Fresh builder per {@link #bind}. */
  protected abstract B newBuilder();

  /** Override to dispatch via a non-builder delegate (e.g. {@code GroovyObjectSupport.invokeMethod}). */
  protected Object delegateFor(final B builder) {
    return builder;
  }

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
