// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import infinity.ai.bt.Behavior;
import infinity.ai.bt.Selector;
import infinity.ai.bt.Sequence;
import infinity.ai.steer.Evade;
import infinity.ai.steer.OrbitTarget;
import infinity.ai.steer.Pursue;
import infinity.ai.steer.Wander;
import infinity.es.ship.weapons.WeaponType;

/**
 * v1 "Brawler" archetype — {@code Selector(EvadeBranch, EngageBranch, PursueBranch,
 * WanderFallback)}. Disengage + flee on low energy; engage when in weapon range
 * (orbit + fire); pursue when target visible but out of range; wander otherwise. Leaves
 * are stateless so the root is a shared singleton; per-bot Pursue/Wander/OrbitTarget/Evade
 * instances live on the per-bot blackboard. See ADR-0009.
 */
public final class CombatantBrain implements BrainArchetype {

  public static final String NAME = "Brawler";

  // Reynolds lead-prediction window for the pursue + evade branches.
  private static final double LEAD_TIME_SECONDS = 0.5;

  // Reynolds wander parameters — small circle just ahead of the agent with bounded jitter.
  // Tuned for Subspace open-arena gameplay; per-arena overrides land via Groovy CCP in slice #08.
  private static final double WANDER_RADIUS = 1.0;
  private static final double WANDER_DISTANCE = 2.0;
  private static final double WANDER_JITTER_RADIANS = 0.5;

  // Combat tuning — defaults for bullet engagement. Per-arena Groovy CCP overrides land
  // in slice #08 (BotBrainConfig + per-archetype tunables).
  private static final double WEAPON_RANGE_WORLD_UNITS = 20.0;
  private static final double ORBIT_RADIUS_WORLD_UNITS = 15.0;

  // Evade threshold — flee when current energy drops below 60% of max. Subspace canon
  // pubs commonly tune disengage around half pool; 0.6 gives the bot a recovery buffer.
  private static final double LOW_ENERGY_FRACTION = 0.6;

  // Rate-shaped thrust magnitude — full forward.
  private static final double FULL_THRUST = 1.0;

  private static final Behavior ROOT =
      new Selector(
          new Sequence(new LowEnergy(LOW_ENERGY_FRACTION), new HasTarget(), new SteerEvade()),
          new Sequence(
              new HasTarget(),
              new InWeaponRange(WEAPON_RANGE_WORLD_UNITS),
              new SteerOrbitTarget(),
              new FireWeapon(WeaponType.BULLET)),
          new Sequence(new HasTarget(), new SteerPursue()),
          new SteerWander());

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Behavior createRoot() {
    return ROOT;
  }

  @Override
  public Blackboard createBlackboard() {
    return new Blackboard(
        new Pursue(LEAD_TIME_SECONDS, FULL_THRUST),
        new Wander(WANDER_RADIUS, WANDER_DISTANCE, WANDER_JITTER_RADIANS, FULL_THRUST),
        new OrbitTarget(ORBIT_RADIUS_WORLD_UNITS, FULL_THRUST),
        new Evade(LEAD_TIME_SECONDS, FULL_THRUST));
  }
}
