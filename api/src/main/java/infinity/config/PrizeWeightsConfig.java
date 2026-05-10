// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Per-arena prize-spawn weights — one entry per Subspace prize type
 * ({@code Repel}, {@code XRadar}, {@code MultiFire}, …) mapped to its
 * spawn-frequency weight (raw integer; {@code 0} means "never spawn").
 *
 * <p>Read at prize-spawn time by {@code PrizeSystem.arenaSelector}, which
 * builds a weighted random selector from the non-zero entries.
 *
 * <p>Populated from the typed {@code prize-weights.groovy} fragment via
 * {@code PrizeWeightsAdapter} (B3 vertical slice). Arenas without a typed
 * fragment fall back to {@link #DEFAULTS} (empty map = no prizes spawn).
 *
 * <p>Keys use Subspace canonical {@code PrizeWeight} names exactly
 * ({@code QuickCharge}, {@code Energy}, {@code XRadar}, …) so the
 * downstream {@code RandomSelector<String>} key shape is unchanged from
 * the legacy {@code Ini}-routed path.
 *
 * @param weights immutable name→weight map; entries with weight {@code 0}
 *     are kept (PrizeSystem filters them at selector-build time)
 */
public record PrizeWeightsConfig(Map<String, Integer> weights) {

  public PrizeWeightsConfig {
    Objects.requireNonNull(weights, "weights");
    weights = Collections.unmodifiableMap(new LinkedHashMap<>(weights));
  }

  /**
   * Empty-weights default: arenas without a typed {@code prize-weights.groovy}
   * fragment get no prizes (PrizeSystem.arenaSelector falls through to its
   * built-in fallback selector). Matches the pre-B3 behaviour for arenas
   * with a missing or empty {@code [PrizeWeight]} INI section.
   */
  public static final PrizeWeightsConfig DEFAULTS = new PrizeWeightsConfig(Map.of());
}
