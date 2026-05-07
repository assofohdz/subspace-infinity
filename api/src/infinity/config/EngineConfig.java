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
 *     cap. Default {@code 0.025} maps trench warbird's
 *     {@code Speed 2000 → 50 jME/sec}, putting ship max in the same
 *     range as bullet velocity (slice S1-cal). Distinct from
 *     {@code subspaceVelocityScale} because the math fit for projectile
 *     speed (5000 → 50) gave ship max-speed values that felt too fast
 *     once {@code LinearDamping 0.99} was wired in slice S1.
 * @param bombThrustScale multiplier applied to per-ship
 *     {@link infinity.es.ship.weapons.BombThrust} at fire time in
 *     {@code WeaponsSystem.applyBombRecoil} to derive the jME recoil
 *     impulse magnitude. Default {@code 0.005} maps SVS canon
 *     {@code BombThrust 400 → 2.0 jME/sec} backward impulse (slice
 *     S2-cal). Distinct from {@code subspaceVelocityScale} because the
 *     projectile fit (400 × 0.01 = 4.0) felt too pushy once recoil
 *     landed in slice S2. Cap reuses {@link #maxProjectileSpeedJme} for
 *     physics-safety on absurd authored values.
 */
public record EngineConfig(
    double subspaceVelocityScale,
    double maxProjectileSpeedJme,
    double shipMaxSpeedScale,
    double bombThrustScale) {

  /**
   * Subspace-canonical baseline used when no {@code engine.groovy} is on
   * the classpath. Scale {@code 0.01} matches the implicit factor we
   * inferred from existing inline magic numbers ({@code 5000 * 0.01 = 50}
   * for bullets); cap {@code 100} bounds the worst-case translated value
   * without restricting today's tuning ranges.
   * {@code shipMaxSpeedScale 0.025} + {@code bombThrustScale 0.005}
   * land trench feel within range of pre-S1 max-speed and a
   * subtle-but-felt recoil per the S1-cal/S2-cal playtest targets.
   */
  public static final EngineConfig DEFAULTS = new EngineConfig(0.01, 100.0, 0.025, 0.005);
}
