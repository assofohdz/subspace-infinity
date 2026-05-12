// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.GroovyObjectSupport;
import infinity.config.PrizeWeightsConfig;
import java.util.LinkedHashMap;
import java.util.Map;

/** Typed adapter for {@code prizeWeights {…}} → {@link PrizeWeightsConfig}. REFERENCE.md §PrizeWeight. */
public final class PrizeWeightsAdapter
    extends SingleClosureAdapter<PrizeWeightsConfig, PrizeWeightsAdapter.WeightsBuilder> {

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

  /** Catch-all delegate; each {@code PrizeName Weight} line lands in {@link #invokeMethod}. */
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
