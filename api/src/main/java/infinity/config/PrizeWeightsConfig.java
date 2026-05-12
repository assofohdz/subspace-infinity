// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Per-arena prize-spawn weights; keys use canonical {@code PrizeWeight} names. See REFERENCE.md {@code ## PrizeWeight}. */
public record PrizeWeightsConfig(Map<String, Integer> weights) {

  public PrizeWeightsConfig {
    Objects.requireNonNull(weights, "weights");
    weights = Collections.unmodifiableMap(new LinkedHashMap<>(weights));
  }

  /** Empty-weights default; arenas without a typed fragment get no prizes. */
  public static final PrizeWeightsConfig DEFAULTS = new PrizeWeightsConfig(Map.of());
}
