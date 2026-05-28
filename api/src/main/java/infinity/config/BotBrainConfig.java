// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

/**
 * Per-arena tuning template for the bot brain (ADR-0009 / ADR-0010). Default
 * archetype is "Brawler"; ADR-0010 envisions multiple archetypes per arena, but v1
 * scopes to one config slot — extend to per-archetype map when slice #08+ adds a
 * second {@code BrainArchetype}. Authored via a {@code botBrain { }} block in
 * {@code arena.groovy} (loaded by {@link infinity.settings.BotBrainAdapter});
 * consumers should read this template only at blackboard-construction time, never
 * on the per-tick hot path (per Config-Component Projection, ADR-0002).
 *
 * <p>Field units (all world-coord doubles unless noted):
 * <ul>
 *   <li>{@code perceptionRadius} — sphere radius for the broadphase body query</li>
 *   <li>{@code engageRange} — max distance for {@code InWeaponRange}</li>
 *   <li>{@code orbitRadius} — desired distance for {@code OrbitTarget}</li>
 *   <li>{@code evadeEnergyFraction} — {@code 0..1}; flee when current energy &lt; this × max</li>
 *   <li>{@code leadPredictionSeconds} — Reynolds pursue/evade prediction window</li>
 *   <li>{@code aimConeDegrees} — half-angle of the firing cone</li>
 *   <li>{@code wanderRadius} — Reynolds wander circle radius (world units)</li>
 *   <li>{@code wanderDistance} — Reynolds wander circle distance ahead of agent (world units)</li>
 *   <li>{@code wanderJitterRadians} — Reynolds wander angular jitter per tick (radians)</li>
 * </ul>
 */
public record BotBrainConfig(
    String archetypeName,
    double perceptionRadius,
    double engageRange,
    double orbitRadius,
    double evadeEnergyFraction,
    double leadPredictionSeconds,
    double aimConeDegrees,
    double wanderRadius,
    double wanderDistance,
    double wanderJitterRadians) {

  public static final BotBrainConfig DEFAULTS =
      new BotBrainConfig("Brawler", 30.0, 20.0, 15.0, 0.6, 0.5, 15.0, 1.0, 2.0, 0.5);

  public BotBrainConfig() {
    this("Brawler", 30.0, 20.0, 15.0, 0.6, 0.5, 15.0, 1.0, 2.0, 0.5);
  }
}
