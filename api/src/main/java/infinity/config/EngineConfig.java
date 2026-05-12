// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Game-wide engine knobs (unit scales, physics radii) loaded once from {@code engine.groovy}; tier above arena/zone/preset.
 *
 * <p>Three velocity scales are intentionally separate (math-fit per playtest, not a duplicate):
 * {@link #subspaceVelocityScale} ({@code 0.01}) maps Subspace projectile speeds to jME ({@code 5000→50});
 * {@link #shipMaxSpeedScale} ({@code 0.01}) maps ship {@code Speed} to the velocity cap (separate because
 * the projectile fit felt too fast for ship max-speed under {@code LinearDamping 0.99});
 * {@link #bombThrustScale} ({@code 0.0005}) maps bomb recoil thrust (separate because the projectile fit
 * felt too pushy when recoil landed). {@link #maxProjectileSpeedJme} clamps absolute output for physics safety.
 *
 * <p><b>Divergence:</b> Subspace canon authors per-ship {@code Radius} (e.g. {@code 14} px in {@code [Misc]}).
 * Infinity treats collision radii as engine-tier globals — same across every arena, not gameplay tuning.
 */
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
