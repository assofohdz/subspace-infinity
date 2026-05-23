// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import infinity.ai.bt.AlwaysSucceed;
import infinity.ai.bt.Behavior;
import infinity.ai.bt.Selector;
import infinity.ai.bt.Sequence;
import infinity.ai.steer.Evade;
import infinity.ai.steer.OrbitTarget;
import infinity.ai.steer.Pursue;
import infinity.ai.steer.Wander;
import infinity.config.BotBrainConfig;
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

  // Reynolds wander parameters — small circle just ahead of the agent with bounded jitter.
  // Not externalized to Groovy in v1 — wander shape rarely needs per-arena tuning. Promote
  // to BotBrainConfig if a future arena needs differently-flavoured idle drift.
  private static final double WANDER_RADIUS = 1.0;
  private static final double WANDER_DISTANCE = 2.0;
  private static final double WANDER_JITTER_RADIANS = 0.5;

  // Rate-shaped thrust magnitude — full forward.
  private static final double FULL_THRUST = 1.0;

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public Behavior createRoot(final BotBrainConfig config) {
    return new Selector(
        new Sequence(
            new LowEnergy(config.evadeEnergyFraction()), new HasTarget(), new SteerEvade()),
        new Sequence(
            new HasTarget(),
            new InWeaponRange(config.engageRange()),
            new SteerOrbitTarget(),
            // Fire-when-aimed: inner Selector swallows mis-aim so the orbit intent
            // written by SteerOrbitTarget survives even when we can't shoot this tick.
            new Selector(
                new Sequence(
                    new InAimRange(config.aimConeDegrees()),
                    new FireWeapon(WeaponType.BULLET)),
                new AlwaysSucceed())),
        new Sequence(new HasTarget(), new SteerPursue()),
        new SteerWander());
  }

  @Override
  public Blackboard createBlackboard(final BotBrainConfig config) {
    return new Blackboard(
        new Pursue(config.leadPredictionSeconds(), FULL_THRUST),
        new Wander(WANDER_RADIUS, WANDER_DISTANCE, WANDER_JITTER_RADIANS, FULL_THRUST),
        new OrbitTarget(config.orbitRadius(), FULL_THRUST),
        new Evade(config.leadPredictionSeconds(), FULL_THRUST));
  }
}
