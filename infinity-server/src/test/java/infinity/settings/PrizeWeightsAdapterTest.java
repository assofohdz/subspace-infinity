// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import infinity.config.PrizeWeightsConfig;
import java.util.Map;
import org.junit.Test;

/** Pins {@link PrizeWeightsAdapter} DSL → {@link PrizeWeightsConfig}; see REFERENCE.md {@code ## PrizeWeight}. */
public class PrizeWeightsAdapterTest {

  @Test
  public void evaluate_canonicalKeys_capturedVerbatim() {
    // Canonical PrizeWeight names per REFERENCE.md §PrizeWeight (note VIE
    // naming quirks: "Recharge" is Full Charge, "QuickCharge" is actual
    // Recharge, "Energy" is Energy Upgrade).
    final String src =
        "prizeWeights {\n"
            + "  QuickCharge 80\n"
            + "  Energy 110\n"
            + "  Gun 50\n"
            + "  Bomb 50\n"
            + "  Portal 25\n"
            + "}\n";

    final PrizeWeightsConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(
            PrizeWeightsAdapter.INSTANCE, src, "test:weights_full");

    final Map<String, Integer> w = cfg.weights();
    assertEquals(Integer.valueOf(80), w.get("QuickCharge"));
    assertEquals(Integer.valueOf(110), w.get("Energy"));
    assertEquals(Integer.valueOf(50), w.get("Gun"));
    assertEquals(Integer.valueOf(50), w.get("Bomb"));
    assertEquals(Integer.valueOf(25), w.get("Portal"));
    assertEquals(5, w.size());
  }

  @Test
  public void evaluate_emptyBlock_yieldsEmptyWeightsMap() {
    final PrizeWeightsConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(
            PrizeWeightsAdapter.INSTANCE, "prizeWeights { }\n", "test:weights_empty");

    assertTrue("empty fragment → empty weights", cfg.weights().isEmpty());
  }

  @Test
  public void zeroWeight_storedExplicitly_canonNeverSpawn() {
    // Subspace canon: weight 0 means the prize never spawns. The adapter must
    // store the entry verbatim — PrizeSystem distinguishes "absent" (use
    // default weight) from "explicit 0" (forbidden).
    final String src =
        "prizeWeights {\n"
            + "  Bomb 0\n"
            + "  Energy 100\n"
            + "}\n";

    final PrizeWeightsConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(
            PrizeWeightsAdapter.INSTANCE, src, "test:weights_zero");

    assertTrue("zero-weight key must be present, not silently dropped", cfg.weights().containsKey("Bomb"));
    assertEquals(Integer.valueOf(0), cfg.weights().get("Bomb"));
    assertEquals(Integer.valueOf(100), cfg.weights().get("Energy"));
  }

  @Test
  public void nonNumericValue_rejected_returnsAdapterEmpty() {
    final String src = "prizeWeights { Bomb 'oops' }\n";

    final PrizeWeightsConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(
            PrizeWeightsAdapter.INSTANCE, src, "test:weights_nonNumeric");

    assertSame(PrizeWeightsConfig.DEFAULTS, cfg);
  }

  @Test
  public void weightsMap_isUnmodifiable() {
    // PrizeWeightsConfig wraps the input in an unmodifiableMap so consumers
    // can hold the reference safely. Pin the contract.
    final String src = "prizeWeights { Bomb 10 }\n";

    final PrizeWeightsConfig cfg =
        GroovySettingsHost.INSTANCE.evaluate(
            PrizeWeightsAdapter.INSTANCE, src, "test:weights_unmod");

    try {
      cfg.weights().put("Whatever", 42);
      throw new AssertionError("expected UnsupportedOperationException — map must be unmodifiable");
    } catch (final UnsupportedOperationException expected) {
      // ok
    }
  }
}
