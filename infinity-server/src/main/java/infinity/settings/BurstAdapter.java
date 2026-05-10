// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.BurstFireConfig;

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
    extends SingleClosureAdapter<BurstFireConfig, BurstAdapter.BurstBuilder> {

  /** Stateless; safe to share across calls. */
  public static final BurstAdapter INSTANCE = new BurstAdapter();

  private BurstAdapter() {
    super("burst", BurstFireConfig.DEFAULTS);
  }

  @Override
  protected BurstBuilder newBuilder() {
    return new BurstBuilder();
  }

  @Override
  public BurstFireConfig extract(final BurstBuilder accumulator) {
    return accumulator.build();
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
