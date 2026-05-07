// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.BulletConfig;
import java.util.List;

/**
 * Typed Groovy adapter for {@code bullet.groovy} fragments. Parses a
 * {@code bullet { … }} block into a {@link BulletConfig} record. Replaces the
 * legacy {@code GroovyWeaponsLoader.loadBullet} that read {@code [Bullet]}
 * keys from the merged INI store.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * bullet {
 *     damageLevel    200    // [Bullet] BulletDamageLevel
 *     damageUpgrade  100    // [Bullet] BulletDamageUpgrade
 *     aliveTime      550    // [Bullet] BulletAliveTime (centiseconds → ms ×10 at the boundary)
 * }
 * }</pre>
 *
 * <p>Any property the script omits keeps its {@link BulletConfig#DEFAULTS}
 * value (Subspace-canonical baseline). Failure to parse / evaluate falls back
 * to {@code DEFAULTS} via {@link #empty} — the host logs the exception with
 * source-path context.
 */
public final class BulletAdapter
    implements GroovySettingsAdapter<BulletConfig, BulletAdapter.BulletBuilder> {

  /** Stateless; safe to share across calls. */
  public static final BulletAdapter INSTANCE = new BulletAdapter();

  private BulletAdapter() {}

  @Override
  public List<String> allowedImports() {
    // The bullet DSL takes only primitives; no class references in scripts.
    return List.of();
  }

  @Override
  public BulletBuilder bind(final Binding binding) {
    final BulletBuilder builder = new BulletBuilder();
    binding.setVariable("bullet", new BulletClosure(builder));
    return builder;
  }

  @Override
  public BulletConfig extract(final BulletBuilder accumulator) {
    return accumulator.build();
  }

  @Override
  public BulletConfig empty() {
    return BulletConfig.DEFAULTS;
  }

  /** Bound to the {@code bullet} variable in the script. */
  private static final class BulletClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final BulletBuilder builder;

    BulletClosure(final BulletBuilder builder) {
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

    /**
     * {@code [Bullet] BulletAliveTime} in <em>centiseconds</em>; the adapter
     * multiplies by 10 to store milliseconds (Subspace VIE convention).
     */
    public void aliveTime(final int centiseconds) {
      this.decayMs = centiseconds * 10L;
    }

    BulletConfig build() {
      return new BulletConfig(damage, damageUpgrade, decayMs);
    }
  }
}
