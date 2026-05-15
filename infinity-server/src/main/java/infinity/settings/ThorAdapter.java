// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.ThorConfig;

/** Typed adapter for {@code thor {…}} blocks → {@link ThorConfig}. Infinity divergence — no Subspace canon. */
public final class ThorAdapter extends SingleClosureAdapter<ThorConfig, ThorAdapter.ThorBuilder> {

  public static final ThorAdapter INSTANCE = new ThorAdapter();

  private ThorAdapter() {
    super("thor", ThorConfig.DEFAULTS);
  }

  @Override
  protected ThorBuilder newBuilder() {
    return new ThorBuilder();
  }

  @Override
  public ThorConfig extract(final ThorBuilder accumulator) {
    return accumulator.build();
  }

  /** Delegate for the {@code thor { ... }} block. */
  public static final class ThorBuilder {

    private int damage = ThorConfig.DEFAULTS.damage();
    private long decayMs = ThorConfig.DEFAULTS.decayMs();
    private int launchVelocity = ThorConfig.DEFAULTS.launchVelocity();

    ThorBuilder() {}

    /** Damage applied on Thor hit. */
    public void damage(final int value) {
      this.damage = value;
    }

    /** Thor projectile lifetime — centiseconds (×10 → ms at the loader boundary). */
    public void aliveTimeCs(final int centiseconds) {
      this.decayMs = Validators.centisecondsToMs("thor.aliveTimeCs", centiseconds);
    }

    /** Forward launch velocity stamped on the Thor projectile (jME world-units/sec). */
    public void launchVelocity(final int value) {
      this.launchVelocity = Validators.nonNegativeInt("thor.launchVelocity", value);
    }

    ThorConfig build() {
      return new ThorConfig(damage, decayMs, launchVelocity);
    }
  }
}
