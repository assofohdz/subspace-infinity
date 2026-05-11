// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/**
 * Pure helper that samples a uniformly-distributed point inside a disc
 * (circle). Extracted so both {@code ArenaLogic.sampleTeamSpawn} (per-team
 * disc) and {@code ArenaLogic.resolveArenaSpawn}'s legacy-fallback path
 * (single-spawn disc via {@code SpawnConfig.spawnRadius}) share the same
 * math and the same RNG-injection seam.
 *
 * <p>Uniform-in-disc derivation: the naive {@code (rand*r, rand*θ)} sample
 * is <em>not</em> uniform — it clusters toward the centre because the
 * mapping {@code (r, θ) → (x, y)} has Jacobian {@code r}. To compensate,
 * draw the radius as {@code sqrt(rand) * R}; that gives a density {@code
 * ∝ r}, which cancels the Jacobian and yields a uniform 2D distribution.
 * Angle {@code θ ∈ [0, 2π)} is drawn uniformly without correction.
 *
 * <p>Kept as a static helper (no instance state) so call sites stay
 * stateless and the per-tick allocation cost is just the {@code double[2]}
 * return. RNG is injected so deterministic tests can pass a seeded
 * {@link RandomGenerator}; production calls
 * {@link #sample(double, double, double)} which lazily allocates a
 * thread-local {@link SplittableRandom}.
 *
 * <p>This is the single canonical "sample uniform inside a circle"
 * primitive for the spawn pipeline. If you find yourself writing
 * {@code sqrt(Math.random()) * r * Math.cos(...)} somewhere else in
 * {@code infinity.systems.*}, route through here instead.
 */
public final class SpawnCircleSampler {

  /**
   * Per-thread RNG. {@link SplittableRandom} is non-thread-safe but
   * dedicating one instance per thread makes that fine and avoids the
   * shared-lock cost of {@link java.util.concurrent.ThreadLocalRandom}'s
   * legacy API on the sim thread.
   */
  private static final ThreadLocal<SplittableRandom> RNG =
      ThreadLocal.withInitial(SplittableRandom::new);

  private SpawnCircleSampler() {
    // utility
  }

  /**
   * Sample uniformly inside the disc of radius {@code radius} centred at
   * {@code (centerX, centerY)}. {@code radius <= 0} returns the centre
   * verbatim — deterministic, no RNG draw — so authors get exact-point
   * spawn behaviour for free without having to seed an RNG path.
   *
   * <p>Production entry-point: uses the thread-local RNG.
   *
   * @return a 2-element {@code double[]} {@code {x, y}}
   */
  public static double[] sample(
      final double centerX, final double centerY, final double radius) {
    return sample(centerX, centerY, radius, RNG.get());
  }

  /**
   * RNG-injected variant for deterministic tests. The radius is drawn as
   * {@code sqrt(rand) * R} so the 2D density is uniform; {@code θ} is
   * drawn uniformly in {@code [0, 2π)}.
   *
   * @param rng random generator; tests typically pass a seeded
   *     {@link SplittableRandom} for repeatability
   * @return a 2-element {@code double[]} {@code {x, y}}
   */
  public static double[] sample(
      final double centerX,
      final double centerY,
      final double radius,
      final RandomGenerator rng) {
    if (radius <= 0.0) {
      return new double[] {centerX, centerY};
    }
    final double r = radius * Math.sqrt(rng.nextDouble());
    final double theta = rng.nextDouble() * 2.0 * Math.PI;
    return new double[] {centerX + r * Math.cos(theta), centerY + r * Math.sin(theta)};
  }
}
