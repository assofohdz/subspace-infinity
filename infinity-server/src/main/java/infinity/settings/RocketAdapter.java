// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.RocketConfig;

/** Typed adapter for {@code rocket {…}} → {@link RocketConfig}. REFERENCE.md §Rocket. */
public final class RocketAdapter
    extends SingleClosureAdapter<RocketConfig, RocketAdapter.RocketBuilder> {

  public static final RocketAdapter INSTANCE = new RocketAdapter();

  private RocketAdapter() {
    super("rocket", RocketConfig.DEFAULTS);
  }

  @Override
  protected RocketBuilder newBuilder() {
    return new RocketBuilder();
  }

  @Override
  public RocketConfig extract(final RocketBuilder accumulator) {
    return accumulator.build();
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
