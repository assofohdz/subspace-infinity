// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.BombConfig;
import java.util.List;

/**
 * Typed Groovy adapter for {@code bomb.groovy} fragments. Parses a
 * {@code bomb { … }} block into a {@link BombConfig} record. Replaces the
 * legacy {@code GroovyWeaponsLoader.loadBomb} that read {@code [Bomb]} keys
 * from the merged INI store.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * bomb {
 *     damageLevel  750     // [Bomb] BombDamageLevel
 *     aliveTime    6000    // [Bomb] BombAliveTime (centiseconds → ms ×10)
 * }
 * }</pre>
 *
 * <p>Other Subspace {@code [Bomb]} keys (BombExplodeDelay, BombExplodePixels,
 * ProximityDistance, JitterTime, BombSafety, EBombShutdownTime, etc.) aren't
 * in {@link BombConfig} today — they're polish-bag work, see the pipeline
 * tracker. The adapter only exposes fields that have a typed config + active
 * consumer.
 */
public final class BombAdapter
    implements GroovySettingsAdapter<BombConfig, BombAdapter.BombBuilder> {

  /** Stateless; safe to share across calls. */
  public static final BombAdapter INSTANCE = new BombAdapter();

  private BombAdapter() {}

  @Override
  public List<String> allowedImports() {
    return List.of();
  }

  @Override
  public BombBuilder bind(final Binding binding) {
    final BombBuilder builder = new BombBuilder();
    binding.setVariable("bomb", new BombClosure(builder));
    return builder;
  }

  @Override
  public BombConfig extract(final BombBuilder accumulator) {
    return accumulator.build();
  }

  @Override
  public BombConfig empty() {
    return BombConfig.DEFAULTS;
  }

  /** Bound to the {@code bomb} variable in the script. */
  private static final class BombClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final BombBuilder builder;

    BombClosure(final BombBuilder builder) {
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

  /** Delegate for the {@code bomb { ... }} block. */
  public static final class BombBuilder {

    private int damage = BombConfig.DEFAULTS.damage();
    private long decayMs = BombConfig.DEFAULTS.decayMs();

    BombBuilder() {}

    /** {@code [Bomb] BombDamageLevel} — damage applied on detonation. */
    public void damageLevel(final int value) {
      this.damage = value;
    }

    /**
     * {@code [Bomb] BombAliveTime} in <em>centiseconds</em>; the adapter
     * multiplies by 10 to store milliseconds (Subspace VIE convention).
     */
    public void aliveTime(final int centiseconds) {
      this.decayMs = centiseconds * 10L;
    }

    BombConfig build() {
      return new BombConfig(damage, decayMs);
    }
  }
}
