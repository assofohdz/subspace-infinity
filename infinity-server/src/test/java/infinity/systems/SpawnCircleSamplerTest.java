// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.SplittableRandom;
import org.junit.Test;

/**
 * Unit coverage for {@link SpawnCircleSampler}. The sampler is the canonical
 * "uniform-in-disc" primitive used by {@code RandomRadiusSpawnPlacement} to
 * pick a sample point around its configured {@code center}.
 *
 * <p>Three contract pillars covered:
 * <ol>
 *   <li><b>Centre-only on zero/negative radius.</b> {@code radius <= 0}
 *       returns the centre verbatim — deterministic, no RNG draw. This is
 *       how the "exact point spawn" case stays trivially correct.
 *   <li><b>All samples within radius.</b> N=10 000 samples around an
 *       arbitrary centre at an arbitrary radius — every one must satisfy
 *       {@code dist(sample, centre) <= radius + ε}.
 *   <li><b>Uniform-in-disc distribution.</b> Ring-density check: the disc
 *       splits into K equal-area rings; uniform sampling lands the same
 *       count in each ring (modulo Monte-Carlo noise). This is the property
 *       {@code r = sqrt(rand) * R} buys us — without the sqrt, samples
 *       would cluster toward the centre (the failure mode the task
 *       description explicitly warned about).
 * </ol>
 *
 * <p>All randomness is RNG-injected ({@link SplittableRandom} seeded with a
 * fixed value) so the assertions are deterministic across machines/runs.
 */
public class SpawnCircleSamplerTest {

  /** Floating-point slack for the "within radius" assertion. */
  private static final double EPSILON = 1.0e-9;

  /**
   * Number of samples in the distribution-shape tests. Chosen so the
   * per-ring chi-square noise is comfortably below the 2× tolerance gate.
   */
  private static final int SAMPLES = 10_000;

  @Test
  public void radiusZeroReturnsCentre() {
    final double[] s = SpawnCircleSampler.sample(100.0, 200.0, 0.0);
    assertNotNull(s);
    assertEquals(2, s.length);
    assertEquals(100.0, s[0], 0.0);
    assertEquals(200.0, s[1], 0.0);
  }

  @Test
  public void negativeRadiusReturnsCentre() {
    // Defensive: a negative author error should not crash and not RNG-draw.
    final double[] s = SpawnCircleSampler.sample(7.0, -3.0, -50.0);
    assertEquals(7.0, s[0], 0.0);
    assertEquals(-3.0, s[1], 0.0);
  }

  @Test
  public void allSamplesWithinRadius() {
    final double centerX = 512.0;
    final double centerY = 768.0;
    final double radius = 256.0;
    final SplittableRandom rng = new SplittableRandom(0xC0FFEEL);

    for (int i = 0; i < SAMPLES; i++) {
      final double[] s = SpawnCircleSampler.sample(centerX, centerY, radius, rng);
      final double dx = s[0] - centerX;
      final double dy = s[1] - centerY;
      final double dist = Math.sqrt(dx * dx + dy * dy);
      assertTrue(
          "sample " + i + " at (" + s[0] + ", " + s[1] + ") is "
              + dist + " from centre — exceeds radius " + radius,
          dist <= radius + EPSILON);
    }
  }

  /**
   * Uniform-in-disc test: split the disc into 4 equal-area rings and verify
   * each ring receives ≈ {@code SAMPLES/4} samples. Equal-area rings have
   * outer radii {@code R*sqrt(k/4)} for {@code k ∈ {1,2,3,4}} (since area
   * scales as {@code r²}). A non-{@code sqrt(rand)}-corrected sampler would
   * over-fill the inner rings and under-fill the outer — this catches the
   * canonical "forgot the sqrt" bug.
   */
  @Test
  public void distributionIsUniformInDisc() {
    final double centerX = 0.0;
    final double centerY = 0.0;
    final double radius = 100.0;
    final int rings = 4;
    final SplittableRandom rng = new SplittableRandom(0xDEADBEEFL);

    final int[] counts = new int[rings];
    for (int i = 0; i < SAMPLES; i++) {
      final double[] s = SpawnCircleSampler.sample(centerX, centerY, radius, rng);
      final double r = Math.sqrt(s[0] * s[0] + s[1] * s[1]);
      // Map r ∈ [0, R] → ring index k where outer-radius is R*sqrt((k+1)/rings).
      // Equivalently, ring = floor((r/R)² * rings), clamped to [0, rings-1].
      final double normSq = r / radius * r / radius;
      final int ring = Math.min(rings - 1, (int) Math.floor(normSq * rings));
      counts[ring]++;
    }

    // Expect SAMPLES/rings per bucket; allow ±20% Monte-Carlo slack.
    // (Chi-square at N=10k with 4 bins comfortably tolerates 20%; the buggy
    // "no-sqrt" sampler would skew inner buckets by 4×+ and trivially fail.)
    final int expected = SAMPLES / rings;
    final int slack = expected / 5; // 20%
    for (int k = 0; k < rings; k++) {
      assertTrue(
          "ring " + k + " count " + counts[k]
              + " outside [" + (expected - slack) + ", " + (expected + slack)
              + "] — distribution not uniform-in-disc",
          counts[k] >= expected - slack && counts[k] <= expected + slack);
    }
  }
}
