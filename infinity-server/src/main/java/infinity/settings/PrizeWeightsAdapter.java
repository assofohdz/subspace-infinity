// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.GroovyObjectSupport;
import infinity.config.PrizeWeightsConfig;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed Groovy adapter for {@code prize-weights.groovy} fragments. Parses a
 * {@code prizeWeights { … }} block into a {@link PrizeWeightsConfig} record.
 * Replaces the legacy {@code [PrizeWeight]} INI-mirror section that
 * {@code PrizeSystem} previously read via the merged {@code Ini}.
 *
 * <p>Script DSL — each line is a {@code PrizeName Weight} pair, matching
 * the Subspace canonical {@code [PrizeWeight]} key shape (PascalCase prize
 * names; raw integer weights):
 *
 * <pre>{@code
 * prizeWeights {
 *     QuickCharge  80
 *     Energy       70
 *     Rotation     60
 *     XRadar       100
 *     MultiFire    255
 *     Repel        100
 *     // ...
 * }
 * }</pre>
 *
 * <p>Catches every {@code KeyName Value} line via {@link WeightsDelegate}'s
 * {@code invokeMethod} (mirrors the legacy {@code SectionDelegate} pattern).
 * Keys are stored verbatim so downstream {@code PrizeSystem} keying logic is
 * unchanged from the pre-B3 INI path.
 *
 * <p>The DSL body delegates to {@link WeightsDelegate} (a {@code
 * GroovyObjectSupport}) rather than the builder itself — overrides
 * {@link #delegateFor} on {@link SingleClosureAdapter} to redirect.
 */
public final class PrizeWeightsAdapter
    extends SingleClosureAdapter<PrizeWeightsConfig, PrizeWeightsAdapter.WeightsBuilder> {

  /** Stateless; safe to share across calls. */
  public static final PrizeWeightsAdapter INSTANCE = new PrizeWeightsAdapter();

  private PrizeWeightsAdapter() {
    super("prizeWeights", PrizeWeightsConfig.DEFAULTS);
  }

  @Override
  protected WeightsBuilder newBuilder() {
    return new WeightsBuilder();
  }

  @Override
  public PrizeWeightsConfig extract(final WeightsBuilder accumulator) {
    return accumulator.build();
  }

  @Override
  protected Object delegateFor(final WeightsBuilder builder) {
    return new WeightsDelegate(builder);
  }

  /**
   * Catch-all delegate for the {@code prizeWeights { ... }} body. Each
   * {@code PrizeName Weight} line lands as an {@code invokeMethod} call
   * (Groovy dispatches them as method calls with one positional arg);
   * we translate to a single map put.
   */
  private static final class WeightsDelegate extends GroovyObjectSupport {

    private final WeightsBuilder builder;

    WeightsDelegate(final WeightsBuilder builder) {
      this.builder = builder;
    }

    @Override
    public Object invokeMethod(final String name, final Object args) {
      final Object[] arr = args instanceof Object[] objs ? objs : new Object[] {args};
      if (arr.length != 1) {
        throw new IllegalArgumentException(
            "prizeWeights key '" + name + "' takes exactly one weight (got " + arr.length + ")");
      }
      final Object value = arr[0];
      if (!(value instanceof Number n)) {
        throw new IllegalArgumentException(
            "prizeWeights key '" + name + "' weight must be numeric (got " + value + ")");
      }
      builder.put(name, n.intValue());
      return null;
    }
  }

  /** Mutable accumulator for the prizeWeights block. */
  public static final class WeightsBuilder {

    private final Map<String, Integer> weights = new LinkedHashMap<>();

    WeightsBuilder() {}

    void put(final String prizeName, final int weight) {
      weights.put(prizeName, weight);
    }

    PrizeWeightsConfig build() {
      return new PrizeWeightsConfig(weights);
    }
  }
}
