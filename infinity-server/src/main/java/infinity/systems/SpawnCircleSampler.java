// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import java.util.SplittableRandom;
import java.util.random.RandomGenerator;

/** Uniform sample inside a disc — radius drawn as {@code sqrt(rand) * R} to cancel the polar Jacobian. */
public final class SpawnCircleSampler {

  private static final ThreadLocal<SplittableRandom> RNG =
      ThreadLocal.withInitial(SplittableRandom::new);

  private SpawnCircleSampler() {
  }

  /** Production entry-point; uses the thread-local RNG. {@code radius <= 0} returns the centre. */
  public static double[] sample(
      final double centerX, final double centerY, final double radius) {
    return sample(centerX, centerY, radius, RNG.get());
  }

  /** RNG-injected variant for deterministic tests. */
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
