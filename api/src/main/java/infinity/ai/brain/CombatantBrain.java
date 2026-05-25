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
import infinity.ai.tactical.Disengage;
import infinity.ai.tactical.Engage;
import infinity.ai.tactical.IsGoal;
import infinity.ai.tactical.NavigateToTile;
import infinity.ai.tactical.Search;
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
    final Behavior engage = engageBranch(config);
    return new Selector(
        // Per-tick reactive override — preempts the chosen goal mid-execution (ADR-0013).
        new Sequence(
            new LowEnergy(config.evadeEnergyFraction()), new HasTarget(), new SteerEvade()),

        // Goal-driven branches: the TacticalPlanner's choice dispatches here (ADR-0013).
        new Sequence(new IsGoal(Engage.class), engage),
        new Sequence(new IsGoal(Disengage.class), new HasTarget(), new SteerEvade()),
        new Sequence(new IsGoal(NavigateToTile.class), new SteerToGoalTile()),
        new Sequence(new IsGoal(Search.class), new SteerWander()),

        // v1 fallback when no goal is set (planner not yet run) or a goal branch failed
        // (e.g. Engage out of range). Flow-field approach first (rounds walls toward the target);
        // straight-line pursue when nav isn't live (SteerApproachTarget fails through).
        engage,
        new Sequence(new HasTarget(), new SteerApproachTarget()),
        new Sequence(new HasTarget(), new SteerPursue()),
        new SteerWander());
  }

  /**
   * Engage subtree (reused by the {@code Engage} goal branch + the no-goal fallback): orbit +
   * fire-when-aimed. Gated on {@link HasLineOfSight} — a wall-occluded target fails here so the BT
   * falls through to {@code SteerApproachTarget} (navigate around the wall) instead of orbiting +
   * firing through it. With no nav wired, LoS degrades to SUCCESS (v1 in-range orbit).
   */
  private static Behavior engageBranch(final BotBrainConfig config) {
    return new Sequence(
        new HasTarget(),
        new HasLineOfSight(),
        new InWeaponRange(config.engageRange()),
        new SteerOrbitTarget(),
        // Fire-when-aimed: inner Selector swallows mis-aim so the orbit intent written by
        // SteerOrbitTarget survives even when we can't shoot this tick.
        new Selector(
            new Sequence(
                new InAimRange(config.aimConeDegrees()), new FireWeapon(WeaponType.BULLET)),
            new AlwaysSucceed()));
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
