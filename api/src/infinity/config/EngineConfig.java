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
 * <p>Currently scoped to projectile-speed translation (slice 10). Future
 * audit work (slice P2) may expand the record with additional unit-bridge
 * or physics-engine knobs.
 *
 * @param subspaceVelocityScale multiplier applied to per-ship Subspace
 *     velocity values ({@code BulletSpeed} / {@code BombSpeed} /
 *     {@code BurstSpeed}) at fire time to land in jME world-units / sec.
 *     Default {@code 0.01} produces SVS canonical {@code 2000 → 20} jME
 *     units / sec, and the existing trench warbird's {@code 5000 → 50}
 *     (matches today's hardcoded {@code addLocal(0,0,50)} behaviour for
 *     bullets).
 * @param maxProjectileSpeedJme post-translation cap (jME world-units /
 *     sec). Clamps the absolute projectile speed after multiplication to
 *     prevent physics-breaking values for large per-ship knobs (e.g.
 *     trench javelin's legacy {@code BulletSpeed 64636} would translate
 *     to 646.36 jME without a cap — physics-breaking ground). Default
 *     {@code 100.0} sits comfortably above today's typical 20-50 range
 *     while staying short of the ~330 ceiling the operator flagged as
 *     unsafe.
 */
public record EngineConfig(double subspaceVelocityScale, double maxProjectileSpeedJme) {

  /**
   * Subspace-canonical baseline used when no {@code engine.groovy} is on
   * the classpath. Scale {@code 0.01} matches the implicit factor we
   * inferred from existing inline magic numbers ({@code 5000 * 0.01 = 50}
   * for bullets); cap {@code 100} bounds the worst-case translated value
   * without restricting today's tuning ranges.
   */
  public static final EngineConfig DEFAULTS = new EngineConfig(0.01, 100.0);
}
