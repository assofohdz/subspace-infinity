// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.DecoyConfig;
import java.util.List;

/**
 * Typed Groovy adapter for {@code decoy.groovy} fragments. Parses a
 * {@code decoy { … }} block into a {@link DecoyConfig} record.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * decoy {
 *     aliveTime  3000   // [Misc] DecoyAliveTime (centiseconds → ms ×10)
 * }
 * }</pre>
 */
public final class DecoyAdapter
    implements GroovySettingsAdapter<DecoyConfig, DecoyAdapter.DecoyBuilder> {

  /** Stateless; safe to share across calls. */
  public static final DecoyAdapter INSTANCE = new DecoyAdapter();

  private DecoyAdapter() {}

  @Override
  public List<String> allowedImports() {
    return List.of();
  }

  @Override
  public DecoyBuilder bind(final Binding binding) {
    final DecoyBuilder builder = new DecoyBuilder();
    binding.setVariable("decoy", new DecoyClosure(builder));
    return builder;
  }

  @Override
  public DecoyConfig extract(final DecoyBuilder accumulator) {
    return accumulator.build();
  }

  @Override
  public DecoyConfig empty() {
    return DecoyConfig.DEFAULTS;
  }

  /** Bound to the {@code decoy} variable in the script. */
  private static final class DecoyClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final DecoyBuilder builder;

    DecoyClosure(final DecoyBuilder builder) {
      super(null);
      this.builder = builder;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final Closure<?> body) {
      body.setDelegate(builder);
      body.setResolveStrategy(DELEGATE_FIRST);
      body.call();
      return null;
    }
  }

  /** Delegate for the {@code decoy { ... }} block. */
  public static final class DecoyBuilder {

    private long aliveTimeMs = DecoyConfig.DEFAULTS.aliveTimeMs();

    DecoyBuilder() {}

    /**
     * {@code [Misc] DecoyAliveTime} in <em>centiseconds</em>; the adapter
     * multiplies by 10 to store milliseconds (Subspace VIE convention).
     */
    public void aliveTime(final int centiseconds) {
      this.aliveTimeMs = centiseconds * 10L;
    }

    DecoyConfig build() {
      return new DecoyConfig(aliveTimeMs);
    }
  }
}
