// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import infinity.config.PrizeConfig;
import java.util.List;

/**
 * Typed Groovy adapter for {@code prize.groovy} fragments. Parses a
 * {@code prize { … }} block into a {@link PrizeConfig} record. Replaces the
 * legacy {@code GroovyWeaponsLoader.loadPrize}.
 *
 * <p>Script DSL:
 *
 * <pre>{@code
 * prize {
 *     maxExist  8000    // [Prize] PrizeMaxExist (centiseconds → ms ×10)
 * }
 * }</pre>
 *
 * <p>Other Subspace {@code [Prize]} keys (PrizeFactor, PrizeDelay,
 * MultiPrizeCount, PrizeMinExist, DeathPrizeTime, EngineShutdownTime, etc.)
 * aren't in {@link PrizeConfig} today — they're polish-bag work / B-slice
 * candidates per the pipeline tracker. The adapter only exposes fields that
 * have a typed config + active consumer.
 */
public final class PrizeAdapter
    implements GroovySettingsAdapter<PrizeConfig, PrizeAdapter.PrizeBuilder> {

  /** Stateless; safe to share across calls. */
  public static final PrizeAdapter INSTANCE = new PrizeAdapter();

  private PrizeAdapter() {}

  @Override
  public List<String> allowedImports() {
    return List.of();
  }

  @Override
  public PrizeBuilder bind(final Binding binding) {
    final PrizeBuilder builder = new PrizeBuilder();
    binding.setVariable("prize", new PrizeClosure(builder));
    return builder;
  }

  @Override
  public PrizeConfig extract(final PrizeBuilder accumulator) {
    return accumulator.build();
  }

  @Override
  public PrizeConfig empty() {
    return PrizeConfig.DEFAULTS;
  }

  /** Bound to the {@code prize} variable in the script. */
  private static final class PrizeClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final PrizeBuilder builder;

    PrizeClosure(final PrizeBuilder builder) {
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

  /** Delegate for the {@code prize { ... }} block. */
  public static final class PrizeBuilder {

    private long defaultDecayMs = PrizeConfig.DEFAULTS.defaultDecayMs();

    PrizeBuilder() {}

    /**
     * {@code [Prize] PrizeMaxExist} in <em>centiseconds</em>; the adapter
     * multiplies by 10 to store milliseconds (Subspace VIE convention).
     */
    public void maxExist(final int centiseconds) {
      this.defaultDecayMs = centiseconds * 10L;
    }

    PrizeConfig build() {
      return new PrizeConfig(
          defaultDecayMs,
          PrizeConfig.DEFAULTS.defaultMaxCount(),
          PrizeConfig.DEFAULTS.bountyValue());
    }
  }
}
