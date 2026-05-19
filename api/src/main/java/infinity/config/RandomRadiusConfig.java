// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Random-radius spawn module kwargs. Exactly one of {@code center} (single-point, FFA)
 * or {@code centers} (per-freq map, multi-team) must be set. {@code radius} is the
 * arena-local-tile radius around the chosen center; {@code 0} = exact-point spawn.
 *
 * <p>Coordinate convention: arena-local tiles (REFERENCE.md ## Spawn). {@code (0, 0)} = NW;
 * {@code (1024, 1024)} = SE. The {@code [x, z]} pair lists are encoded as Groovy lists:
 * {@code center: [512, 512]} or {@code centers: ["0": [...], "1": [...]]}.
 *
 * <p>Per-freq lookup wraps via {@code freq % centers.size()}.
 */
public record RandomRadiusConfig(
    @Nullable List<Integer> center,
    @Nullable Map<String, List<Integer>> centers,
    double radius) {

  public RandomRadiusConfig {
    if ((center == null) == (centers == null)) {
      throw new IllegalArgumentException(
          "RandomRadiusConfig requires exactly one of 'center' or 'centers'");
    }
    if (center != null && center.size() != 2) {
      throw new IllegalArgumentException(
          "RandomRadiusConfig.center must be [x, z] (size 2); got " + center);
    }
    if (centers != null) {
      if (centers.isEmpty()) {
        throw new IllegalArgumentException("RandomRadiusConfig.centers must not be empty");
      }
      for (final Map.Entry<String, List<Integer>> e : centers.entrySet()) {
        if (e.getValue() == null || e.getValue().size() != 2) {
          throw new IllegalArgumentException(
              "RandomRadiusConfig.centers[" + e.getKey() + "] must be [x, z]; got " + e.getValue());
        }
      }
    }
    if (radius < 0.0 || Double.isNaN(radius) || Double.isInfinite(radius)) {
      throw new IllegalArgumentException("RandomRadiusConfig.radius must be finite >= 0; got " + radius);
    }
  }

  /** Picks the center for {@code freq}; per-freq map wraps via modulo. */
  public List<Integer> centerFor(final int freq) {
    if (center != null) {
      return center;
    }
    final int size = centers.size();
    final int idx = ((freq % size) + size) % size;
    final List<Integer> picked = centers.get(Integer.toString(idx));
    return picked != null ? picked : centers.values().iterator().next();
  }
}
