// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.MineConfig;
import java.util.List;

/**
 * Typed Groovy adapter for {@code mine.groovy} fragments. Parses a
 * {@code mine { … }} block into a {@link MineConfig} record. Replaces the
 * legacy {@code GroovyWeaponsLoader.loadMine}.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * mine {
 *     aliveTime  12000    // [Mine] MineAliveTime (centiseconds → ms ×10)
 * }
 * }</pre>
 *
 * <p>Damage today comes from the per-ship {@code MineCost} component
 * (Subspace's "mine cost = damage" convention), so the typed mine block
 * carries only decay. {@code TeamMaxMines} is a separate ⚠️ unwired key in
 * the pipeline tracker — when it gets a consumer it'll join {@link MineConfig}
 * and this DSL.
 */
public final class MineAdapter
    implements GroovySettingsAdapter<MineConfig, MineAdapter.MineBuilder> {

  /** Stateless; safe to share across calls. */
  public static final MineAdapter INSTANCE = new MineAdapter();

  private MineAdapter() {}

  @Override
  public List<String> allowedImports() {
    return List.of();
  }

  @Override
  public MineBuilder bind(final Binding binding) {
    final MineBuilder builder = new MineBuilder();
    binding.setVariable("mine", new MineClosure(builder));
    return builder;
  }

  @Override
  public MineConfig extract(final MineBuilder accumulator) {
    return accumulator.build();
  }

  @Override
  public MineConfig empty() {
    return MineConfig.DEFAULTS;
  }

  /** Bound to the {@code mine} variable in the script. */
  private static final class MineClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final MineBuilder builder;

    MineClosure(final MineBuilder builder) {
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

  /** Delegate for the {@code mine { ... }} block. */
  public static final class MineBuilder {

    private long decayMs = MineConfig.DEFAULTS.decayMs();

    MineBuilder() {}

    /**
     * {@code [Mine] MineAliveTime} in <em>centiseconds</em>; the adapter
     * multiplies by 10 to store milliseconds (Subspace VIE convention).
     */
    public void aliveTime(final int centiseconds) {
      this.decayMs = centiseconds * 10L;
    }

    MineConfig build() {
      return new MineConfig(decayMs);
    }
  }
}
