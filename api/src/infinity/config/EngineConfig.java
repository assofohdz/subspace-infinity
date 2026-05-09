// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

/**
 * Game-wide developer-tunable engine knobs — the fourth config tier above
 * preset / arena / zone. Loaded once at server startup from
 * {@code engine.groovy} (classpath resource, packaged inside the jar
 * because these are developer-tuned, not operator-tuned). Live-reload
 * deferred — restart-tuned for now.
 *
 * <p>Distinct from the per-arena {@code ConfigRegistry} (gameplay /
 * balance) and per-arena {@code ArenaConfig} (structural / map). Engine
 * tier is for unit-conversion and physics-engine-level constants that
 * should be the same across every arena and zone in the running build.
 *
 * <p>Slice 10 introduced the projectile-speed translation knobs.
 * Slice S1-cal+S2-cal (engine-tier scale calibration polish-bag) added
 * separate per-mechanic scales for ship max-speed and bomb recoil
 * because the playtest verdict on slice S1 was "max-speed too high"
 * and on slice S2 was "bomb recoil too strong" — both root-caused to
 * {@code subspaceVelocityScale 0.01} being math-fit for projectile
 * speed only, not for ship-impulse magnitude or ship-feel cap.
 *
 * @param subspaceVelocityScale multiplier applied to per-ship Subspace
 *     velocity values ({@code BulletSpeed} / {@code BombSpeed} /
 *     {@code BurstSpeed}) at fire time to land in jME world-units / sec.
 *     Default {@code 0.01} produces SVS canonical {@code 2000 → 20} jME
 *     units / sec, and the existing trench warbird's {@code 5000 → 50}
 *     (matches today's hardcoded {@code addLocal(0,0,50)} behaviour for
 *     bullets). Consumed by {@link infinity.es.ship.weapons.BulletSpeed} /
 *     {@link infinity.es.ship.weapons.BombSpeed} /
 *     {@link infinity.es.ship.weapons.BurstSpeed} fire paths only.
 * @param maxProjectileSpeedJme post-translation cap (jME world-units /
 *     sec). Clamps the absolute projectile speed after multiplication to
 *     prevent physics-breaking values for large per-ship knobs (e.g.
 *     trench javelin's legacy {@code BulletSpeed 64636} would translate
 *     to 646.36 jME without a cap — physics-breaking ground). Default
 *     {@code 100.0} sits comfortably above today's typical 20-50 range
 *     while staying short of the ~330 ceiling the operator flagged as
 *     unsafe. Reused by the bomb-recoil path (S2) since recoil shares
 *     the same physics-safety concern for absurd authored values.
 * @param shipMaxSpeedScale multiplier applied to the ship's
 *     {@code Speed} component (raw Subspace velocity units) at consumer
 *     time in {@code PlayerDriver.update} to derive the jME max-speed
 *     cap. Default {@code 0.01} maps trench warbird's
 *     {@code Speed 2000 → 20 jME/sec} cap (steady-state ~19.75 under
 *     {@code LinearDamping 0.99}), putting ship max at ~40% of bullet
 *     velocity (slice S1-cal). Distinct from
 *     {@code subspaceVelocityScale} because the math fit for projectile
 *     speed (5000 → 50) gave ship max-speed values that felt too fast
 *     once {@code LinearDamping 0.99} was wired in slice S1.
 * @param bombThrustScale multiplier applied to per-ship
 *     {@link infinity.es.ship.weapons.BombThrust} at fire time in
 *     {@code WeaponsSystem.applyBombRecoil} to derive the jME recoil
 *     impulse magnitude. Default {@code 0.0005} maps SVS canon
 *     {@code BombThrust 400 → 0.2 jME/sec} backward impulse (slice
 *     S2-cal) — a subtle nudge (~1% of ship max-speed) rather than
 *     a strong shove. Distinct from {@code subspaceVelocityScale}
 *     because the projectile fit (400 × 0.01 = 4.0) felt too pushy
 *     once recoil landed in slice S2. Cap reuses
 *     {@link #maxProjectileSpeedJme} for physics-safety on absurd
 *     authored values.
 * @param bulletRadius bullet collision-shape radius in jME world units
 *     (1 unit ≈ 1 tile per moss-world-grid). Default {@code 0.125} matches
 *     the retired {@code CorePhysicsConstants.BULLETSIZERADIUS}. Subspace
 *     canon does not author per-projectile collision radius — this is a
 *     Moss/Infinity concept driven by {@link com.simsilica.mphys.ShapeInfo}.
 *     Slice projectile-radius-pattern4 lifted these radii into engine-tier
 *     because they are physics-engine facts identical across every arena
 *     in the build, not gameplay tuning that arena authors should override.
 * @param bombRadius bomb collision-shape radius (jME world units). Default
 *     {@code 0.5} matches retired {@code BOMBSIZERADIUS}. See
 *     {@link #bulletRadius} for the divergence rationale.
 * @param mineRadius mine collision-shape radius (jME world units). Default
 *     {@code 0.5} matches retired {@code MINESIZERADIUS}. See
 *     {@link #bulletRadius} for the divergence rationale.
 * @param thorRadius thor collision-shape radius (jME world units). Default
 *     {@code 0.5} matches retired {@code THORSIZERADIUS}. See
 *     {@link #bulletRadius} for the divergence rationale.
 * @param prizeRadius prize collision-shape radius (jME world units).
 *     Default {@code 0.5} matches retired {@code PRIZESIZERADIUS}. See
 *     {@link #bulletRadius} for the divergence rationale.
 * @param burstRadius burst projectile collision-shape radius (jME world
 *     units). Default {@code 0.125} matches retired
 *     {@code BURSTSIZERADIUS}. See {@link #bulletRadius} for the
 *     divergence rationale.
 * @param repelRadius repel collision-shape radius (jME world units).
 *     Default {@code 0.125} matches retired {@code REPELRADIUS}. See
 *     {@link #bulletRadius} for the divergence rationale.
 * @param over1Radius generic decoration "Over1" collision-shape radius
 *     (jME world units). Default {@code 0.5} matches retired
 *     {@code OVER1SIZERADIUS}. See {@link #bulletRadius} for the
 *     divergence rationale.
 * @param over2Radius generic decoration "Over2" collision-shape radius
 *     (jME world units). Default {@code 1.0} matches retired
 *     {@code OVER2SIZERADIUS}. See {@link #bulletRadius} for the
 *     divergence rationale.
 * @param over5Radius generic decoration "Over5" collision-shape radius
 *     (jME world units). Default {@code 0.1} matches retired
 *     {@code OVER5SIZERADIUS}. See {@link #bulletRadius} for the
 *     divergence rationale.
 * @param flagRadius flag collision-shape radius (jME world units).
 *     Default {@code 0.5} matches retired {@code FLAGSIZERADIUS}. See
 *     {@link #bulletRadius} for the divergence rationale.
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
    double flagRadius) {

  /**
   * Subspace-canonical baseline used when no {@code engine.groovy} is on
   * the classpath. Scale {@code 0.01} matches the implicit factor we
   * inferred from existing inline magic numbers ({@code 5000 * 0.01 = 50}
   * for bullets); cap {@code 100} bounds the worst-case translated value
   * without restricting today's tuning ranges.
   * {@code shipMaxSpeedScale 0.01} + {@code bombThrustScale 0.0005}
   * land trench feel at ~40% of bullet velocity for ship max and a
   * subtle (~1% of max-speed) recoil — values dialed in via S1-cal /
   * S2-cal playtest. Collision radii match the retired
   * {@code CorePhysicsConstants.*SIZERADIUS} family — slice
   * projectile-radius-pattern4.
   */
  public static final EngineConfig DEFAULTS =
      new EngineConfig(
          0.01, 100.0, 0.01, 0.0005,
          0.125, 0.5, 0.5, 0.5, 0.5, 0.125, 0.125, 0.5, 1.0, 0.1, 0.5);
}
