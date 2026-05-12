// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/** Game-wide engine knobs (unit scales, physics radii) loaded once from {@code engine.groovy}; tier above arena/zone/preset — same across every arena in the build. */
public record EngineConfig(
    double subspaceVelocityScale,
    double maxProjectileSpeedJme,
    double shipMaxSpeedScale,
    double bombThrustScale,
    double bulletRadius,
    double bombRadius,
    double mineRadius,
    double thorRadius,
    double prizeRadius,
    double burstRadius,
    double repelRadius,
    double over1Radius,
    double over2Radius,
    double over5Radius,
    double flagRadius,
    double shipRadius) {

  /** Subspace-canonical baseline used when no {@code engine.groovy} is on the classpath. */
  public static final EngineConfig DEFAULTS =
      new EngineConfig(
          0.01, 100.0, 0.01, 0.0005,
          0.125, 0.5, 0.5, 0.5, 0.5, 0.125, 0.125, 0.5, 1.0, 0.1, 0.5, 1.0);
}
