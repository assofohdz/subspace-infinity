// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import infinity.config.BulletConfig;

/** Typed adapter for {@code bullet {…}} → {@link BulletConfig}. REFERENCE.md §Bullet. */
public final class BulletAdapter
    extends SingleClosureAdapter<BulletConfig, BulletAdapter.BulletBuilder> {

  public static final BulletAdapter INSTANCE = new BulletAdapter();

  private BulletAdapter() {
    super("bullet", BulletConfig.DEFAULTS);
  }

  @Override
  protected BulletBuilder newBuilder() {
    return new BulletBuilder();
  }

  @Override
  public BulletConfig extract(final BulletBuilder accumulator) {
    return accumulator.build();
  }

  /** Delegate for the {@code bullet { ... }} block. */
  public static final class BulletBuilder {

    private int damage = BulletConfig.DEFAULTS.damage();
    private int damageUpgrade = BulletConfig.DEFAULTS.damageUpgrade();
    private long decayMs = BulletConfig.DEFAULTS.decayMs();

    BulletBuilder() {}

    /** {@code [Bullet] BulletDamageLevel} — base damage at bullet level 1. */
    public void damageLevel(final int value) {
      this.damage = value;
    }

    /** {@code [Bullet] BulletDamageUpgrade} — additional damage per bullet level above 1. */
    public void damageUpgrade(final int value) {
      this.damageUpgrade = value;
    }

    /** {@code [Bullet] BulletAliveTime} — centiseconds (×10 → ms). */
    public void aliveTime(final int centiseconds) {
      this.decayMs = Validators.centisecondsToMs("bullet.aliveTime", centiseconds);
    }

    BulletConfig build() {
      return new BulletConfig(damage, damageUpgrade, decayMs);
    }
  }
}
