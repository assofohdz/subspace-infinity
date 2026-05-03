// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import infinity.config.PrizeWeightsConfig;
import java.util.LinkedHashMap;
import java.util.List;
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
 */
public final class PrizeWeightsAdapter
    implements GroovySettingsAdapter<PrizeWeightsConfig, PrizeWeightsAdapter.WeightsBuilder> {

  /** Stateless; safe to share across calls. */
  public static final PrizeWeightsAdapter INSTANCE = new PrizeWeightsAdapter();

  private PrizeWeightsAdapter() {}

  @Override
  public List<String> allowedImports() {
    return List.of();
  }

  @Override
  public WeightsBuilder bind(final Binding binding) {
    final WeightsBuilder builder = new WeightsBuilder();
    binding.setVariable("prizeWeights", new WeightsClosure(builder));
    return builder;
  }

  @Override
  public PrizeWeightsConfig extract(final WeightsBuilder accumulator) {
    return accumulator.build();
  }

  @Override
  public PrizeWeightsConfig empty() {
    return PrizeWeightsConfig.DEFAULTS;
  }

  /** Bound to the {@code prizeWeights} variable in the script. */
  private static final class WeightsClosure extends Closure<Void> {
    private static final long serialVersionUID = 1L;

    private final WeightsBuilder builder;

    WeightsClosure(final WeightsBuilder builder) {
      super(null);
      this.builder = builder;
    }

    @SuppressWarnings("unused") // invoked via Groovy dispatch
    public Void doCall(final Closure<?> body) {
      body.setDelegate(new WeightsDelegate(builder));
      body.setResolveStrategy(DELEGATE_FIRST);
      body.call();
      return null;
    }
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
