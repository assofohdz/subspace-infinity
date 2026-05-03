// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.RocketConfig;
import java.util.List;

/**
 * Typed Groovy adapter for {@code rocket.groovy} fragments. Parses a
 * {@code rocket { … }} block into a {@link RocketConfig} record.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * rocket {
 *     thrust  100   // [Rocket] RocketThrust (override Thrust while active)
 *     speed   3000  // [Rocket] RocketSpeed (override Speed while active)
 * }
 * }</pre>
 *
 * <p>Per-ship {@code RocketTime} (buff lifetime) lives in
 * {@code ships.groovy} via
 * {@link infinity.config.RocketStats#activeTimeCs} — Subspace authors it
 * per-ship, not arena-global.
 */
public final class RocketAdapter
    implements GroovySettingsAdapter<RocketConfig, RocketAdapter.RocketBuilder> {

  /** Stateless; safe to share across calls. */
  public static final RocketAdapter INSTANCE = new RocketAdapter();

  private RocketAdapter() {}

  @Override
  public List<String> allowedImports() {
    return List.of();
  }

  @Override
  public RocketBuilder bind(final Binding binding) {
    final RocketBuilder builder = new RocketBuilder();
    binding.setVariable("rocket", new RocketClosure(builder));
    return builder;
  }

  @Override
  public RocketConfig extract(final RocketBuilder accumulator) {
    return accumulator.build();
  }

  @Override
  public RocketConfig empty() {
    return RocketConfig.DEFAULTS;
  }

  /** Bound to the {@code rocket} variable in the script. */
  private static final class RocketClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final RocketBuilder builder;

    RocketClosure(final RocketBuilder builder) {
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

  /** Delegate for the {@code rocket { ... }} block. */
  public static final class RocketBuilder {

    private int thrust = RocketConfig.DEFAULTS.thrust();
    private int speed = RocketConfig.DEFAULTS.speed();

    RocketBuilder() {}

    /** {@code [Rocket] RocketThrust} — thrust override while rocket active. */
    public void thrust(final int value) {
      this.thrust = value;
    }

    /** {@code [Rocket] RocketSpeed} — top-speed override while rocket active. */
    public void speed(final int value) {
      this.speed = value;
    }

    RocketConfig build() {
      return new RocketConfig(thrust, speed);
    }
  }
}
