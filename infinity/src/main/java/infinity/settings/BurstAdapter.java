// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.BurstFireConfig;
import java.util.List;

/**
 * Typed Groovy adapter for {@code burst.groovy} fragments. Parses a
 * {@code burst { … }} block into a {@link BurstFireConfig} record. Replaces
 * the legacy {@code GroovyWeaponsLoader.loadBurst}.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * burst {
 *     damageLevel  515    // [Burst] BurstDamageLevel
 * }
 * }</pre>
 *
 * <p>Subspace VIE has no canonical {@code BurstShrapnelCount} or
 * {@code BurstAliveTime} keys; {@link BurstFireConfig#projectileCount} and
 * {@link BurstFireConfig#decayMs} stay on their Infinity defaults until/unless
 * a future slice surfaces them through this DSL.
 */
public final class BurstAdapter
    implements GroovySettingsAdapter<BurstFireConfig, BurstAdapter.BurstBuilder> {

  /** Stateless; safe to share across calls. */
  public static final BurstAdapter INSTANCE = new BurstAdapter();

  private BurstAdapter() {}

  @Override
  public List<String> allowedImports() {
    return List.of();
  }

  @Override
  public BurstBuilder bind(final Binding binding) {
    final BurstBuilder builder = new BurstBuilder();
    binding.setVariable("burst", new BurstClosure(builder));
    return builder;
  }

  @Override
  public BurstFireConfig extract(final BurstBuilder accumulator) {
    return accumulator.build();
  }

  @Override
  public BurstFireConfig empty() {
    return BurstFireConfig.DEFAULTS;
  }

  /** Bound to the {@code burst} variable in the script. */
  private static final class BurstClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final BurstBuilder builder;

    BurstClosure(final BurstBuilder builder) {
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

  /** Delegate for the {@code burst { ... }} block. */
  public static final class BurstBuilder {

    private int damage = BurstFireConfig.DEFAULTS.damage();

    BurstBuilder() {}

    /** {@code [Burst] BurstDamageLevel} — damage applied on burst-bullet hit. */
    public void damageLevel(final int value) {
      this.damage = value;
    }

    BurstFireConfig build() {
      return new BurstFireConfig(
          BurstFireConfig.DEFAULTS.projectileCount(),
          BurstFireConfig.DEFAULTS.decayMs(),
          damage);
    }
  }
}
