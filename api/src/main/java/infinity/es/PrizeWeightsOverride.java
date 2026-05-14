// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.es;

import com.simsilica.es.EntityComponent;
import java.util.Map;

/**
 * Optional per-spawner override for prize-type weights. Sparse map merged on
 * top of the spawner's arena defaults at selection time by {@code
 * PrizeSpawnerSystem}. Keys are prize-type strings (matching {@code PrizeTypes}
 * constants — {@code "Bomb"}, {@code "Gun"}, ...); values are non-negative
 * weights. Authored declaratively via {@code arena.groovy}'s
 * {@code spawners { spawn ..., weights: [...] }} block (see
 * {@code SpawnerSpec.weightOverrides}).
 *
 * <p>Server-only — like {@code Spawner} itself, prize spawners aren't visible
 * to clients, so no network serializer registration is needed.
 *
 * @author Asser
 */
public class PrizeWeightsOverride implements EntityComponent {

  private final Map<String, Integer> overrides;

  public PrizeWeightsOverride() {
    this(Map.of());
  }

  public PrizeWeightsOverride(final Map<String, Integer> overrides) {
    this.overrides = overrides == null ? Map.of() : Map.copyOf(overrides);
  }

  /** Unmodifiable view of the overrides. Never {@code null}; may be empty. */
  public Map<String, Integer> getOverrides() {
    return overrides;
  }
}
