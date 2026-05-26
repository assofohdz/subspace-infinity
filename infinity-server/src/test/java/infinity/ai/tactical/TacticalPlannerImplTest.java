// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import infinity.ai.brain.Blackboard;
import infinity.ai.objective.ArenaObjective;
import infinity.ai.objective.GoalTile;
import infinity.ai.steer.Evade;
import infinity.ai.steer.OrbitTarget;
import infinity.ai.steer.Pursue;
import infinity.ai.steer.Wander;
import infinity.config.ZoneBotAiConfig;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;

public class TacticalPlannerImplTest {

  private static final TacticalGoal GOAL_A = new Engage(new EntityId(1));
  private static final TacticalGoal GOAL_B = new Disengage(new EntityId(2));

  private Blackboard bb;
  private ZoneBotAiConfig cfg;

  @Before
  public void setUp() {
    bb = new Blackboard(new Pursue(0.5, 1.0), new Wander(1, 2, 0.5, 1.0),
        new OrbitTarget(15, 1.0), new Evade(0.5, 1.0));
    cfg = ZoneBotAiConfig.DEFAULTS; // cadence 150, stickiness 0.10, minFraction 0.25, minWeight 0.05
  }

  private TacticalPlanner planner(final Behaviour... behaviours) {
    return new TacticalPlannerImpl(List.of(behaviours), () -> cfg);
  }

  @Test
  public void picksMaxIntrinsicTimesWeight() {
    // a: 0.4 × 1.0 = 0.40 ; b: 0.8 × 1.0 = 0.80 → b wins.
    final TacticalPlanner p =
        planner(new FixedBehaviour("a", GOAL_A, 0.4), new FixedBehaviour("b", GOAL_B, 0.8));
    final ArchetypeConfig arch = new ArchetypeConfig("t", Map.of("a", 1.0, "b", 1.0));
    assertEquals(GOAL_B, p.select(bb, arch));
  }

  @Test
  public void weightAmplifiesIntrinsic() {
    // a: 0.5 × 1.0 = 0.50 ; b: 0.6 × 0.5 = 0.30 → a wins despite lower weight.
    final TacticalPlanner p =
        planner(new FixedBehaviour("a", GOAL_A, 0.5), new FixedBehaviour("b", GOAL_B, 0.6));
    final ArchetypeConfig arch = new ArchetypeConfig("t", Map.of("a", 1.0, "b", 0.5));
    assertEquals(GOAL_A, p.select(bb, arch));
  }

  @Test
  public void subFractionThresholdBehavioursSkipped() {
    // threshold = max(1.0) × 0.25 = 0.25 ; b weight 0.2 < 0.25 → not enumerated even though it
    // would score higher. a (the only eligible) wins.
    final TacticalPlanner p =
        planner(new FixedBehaviour("a", GOAL_A, 0.1), new FixedBehaviour("b", GOAL_B, 1.0));
    final ArchetypeConfig arch = new ArchetypeConfig("t", Map.of("a", 1.0, "b", 0.2));
    assertEquals(GOAL_A, p.select(bb, arch));
  }

  @Test
  public void stickinessKeepsRunningGoalWithinMargin() {
    bb.setCurrentGoal(GOAL_A);
    // current a: 0.50 ; rival b: 0.55 ; margin 0.10 → 0.55 ≤ 0.60 → stay on a.
    final TacticalPlanner p =
        planner(new FixedBehaviour("a", GOAL_A, 0.50), new FixedBehaviour("b", GOAL_B, 0.55));
    final ArchetypeConfig arch = new ArchetypeConfig("t", Map.of("a", 1.0, "b", 1.0));
    assertEquals(GOAL_A, p.select(bb, arch));
  }

  @Test
  public void stickinessYieldsWhenRivalBeatsMargin() {
    bb.setCurrentGoal(GOAL_A);
    // current a: 0.50 ; rival b: 0.65 ; margin 0.10 → 0.65 > 0.60 → switch to b.
    final TacticalPlanner p =
        planner(new FixedBehaviour("a", GOAL_A, 0.50), new FixedBehaviour("b", GOAL_B, 0.65));
    final ArchetypeConfig arch = new ArchetypeConfig("t", Map.of("a", 1.0, "b", 1.0));
    assertEquals(GOAL_B, p.select(bb, arch));
  }

  @Test
  public void invalidatedRunningGoalLosesStickinessProtection() {
    bb.setCurrentGoal(GOAL_A);
    // a no longer enumerates (target gone) — only b offers; b wins regardless of margin.
    final TacticalPlanner p =
        planner(new EmptyBehaviour("a"), new FixedBehaviour("b", GOAL_B, 0.01));
    final ArchetypeConfig arch = new ArchetypeConfig("t", Map.of("a", 1.0, "b", 1.0));
    assertEquals(GOAL_B, p.select(bb, arch));
  }

  @Test
  public void nullWhenNothingOffered() {
    final TacticalPlanner p = planner(new EmptyBehaviour("a"));
    final ArchetypeConfig arch = new ArchetypeConfig("t", Map.of("a", 1.0));
    assertNull(p.select(bb, arch));
  }

  @Test
  public void unlistedBehaviourNeverEnumerated() {
    final TacticalPlanner p = planner(new FixedBehaviour("a", GOAL_A, 1.0));
    final ArchetypeConfig arch = new ArchetypeConfig("t", Map.of("b", 1.0)); // a not listed
    assertNull(p.select(bb, arch));
  }

  @Test
  public void baselineBehaviourFitsTrackEnergy() {
    bb.setTarget(new NearbyShipFixture().make());
    final EngageBehaviour engage = new EngageBehaviour();
    final DisengageBehaviour disengage = new DisengageBehaviour();
    final Engage eg = new Engage(new EntityId(9));
    final Disengage dg = new Disengage(new EntityId(9));

    bb.setEnergy(90, 100);
    assertTrue(engage.intrinsicScore(eg, bb) > disengage.intrinsicScore(dg, bb));

    bb.setEnergy(10, 100);
    assertTrue(disengage.intrinsicScore(dg, bb) > engage.intrinsicScore(eg, bb));
  }

  @Test
  public void objectiveBiasFlipsSelection() {
    // No bias: b (0.5) beats a (0.4). objectiveBias{a:2.0} → a eff weight 2.0, 0.4×2.0=0.80 > 0.5.
    bb.setArenaContext(ctxBiasing(Map.of("a", 2.0)));
    final TacticalPlanner p =
        planner(new FixedBehaviour("a", GOAL_A, 0.4), new FixedBehaviour("b", GOAL_B, 0.5));
    final ArchetypeConfig arch = new ArchetypeConfig("t", Map.of("a", 1.0, "b", 1.0));
    assertEquals(GOAL_A, p.select(bb, arch));
  }

  @Test
  public void roleBiasFlipsSelection() {
    // No bias: b (0.5) beats a (0.4). roleBias{a:2.0} → a eff weight 2.0, 0.4×2.0=0.80 > 0.5.
    bb.setRoleBias(Map.of("a", 2.0));
    final TacticalPlanner p =
        planner(new FixedBehaviour("a", GOAL_A, 0.4), new FixedBehaviour("b", GOAL_B, 0.5));
    final ArchetypeConfig arch = new ArchetypeConfig("t", Map.of("a", 1.0, "b", 1.0));
    assertEquals(GOAL_A, p.select(bb, arch));
  }

  @Test
  public void objectiveAndRoleBiasMultiply() {
    // objectiveBias{a:0.5} × roleBias{a:2.0} = 1.0 (identity) → tie goes to first-offered (a).
    bb.setArenaContext(ctxBiasing(Map.of("a", 0.5)));
    bb.setRoleBias(Map.of("a", 2.0));
    final TacticalPlanner p =
        planner(new FixedBehaviour("a", GOAL_A, 0.5), new FixedBehaviour("b", GOAL_B, 0.5));
    final ArchetypeConfig arch = new ArchetypeConfig("t", Map.of("a", 1.0, "b", 1.0));
    assertEquals(GOAL_A, p.select(bb, arch));
  }

  @Test
  public void objectiveBiasZeroMutesBehaviour() {
    // objectiveBias{b:0.0} → b effective weight 0 → skipped; a wins despite its lower intrinsic fit.
    bb.setArenaContext(ctxBiasing(Map.of("b", 0.0)));
    final TacticalPlanner p =
        planner(new FixedBehaviour("a", GOAL_A, 0.4), new FixedBehaviour("b", GOAL_B, 0.9));
    final ArchetypeConfig arch = new ArchetypeConfig("t", Map.of("a", 1.0, "b", 1.0));
    assertEquals(GOAL_A, p.select(bb, arch));
  }

  @Test
  public void equalScoreGoalsLockOntoFirstNoThrash() {
    // Regression: hold-position offers several near-equidistant flag goals with equal score. The
    // planner must commit to one and hold it, not flip between them each cycle (the flag-thrash bug).
    final TacticalGoal g1 = new NavigateToTile(10, 0);
    final TacticalGoal g2 = new NavigateToTile(12, 0);
    final TacticalPlanner p =
        planner(new MultiGoalBehaviour("hold-position", List.of(g1, g2), 0.5));
    final ArchetypeConfig arch = new ArchetypeConfig("t", Map.of("hold-position", 1.0));
    final TacticalGoal first = p.select(bb, arch);
    assertEquals(g1, first); // first-listed wins the tie
    bb.setCurrentGoal(first);
    assertEquals("running goal held, not flipped to equal-score sibling", g1, p.select(bb, arch));
  }

  /** Behaviour offering a fixed list of goals, all at the same intrinsic score. */
  private static final class MultiGoalBehaviour implements Behaviour {
    private final String name;
    private final List<TacticalGoal> goals;
    private final double score;

    MultiGoalBehaviour(final String name, final List<TacticalGoal> goals, final double score) {
      this.name = name;
      this.goals = goals;
      this.score = score;
    }

    @Override
    public String name() {
      return this.name;
    }

    @Override
    public List<TacticalGoal> enumerate(final Blackboard bb) {
      return this.goals;
    }

    @Override
    public double intrinsicScore(final TacticalGoal goal, final Blackboard bb) {
      return this.score;
    }
  }

  /** Context whose objective applies the given multiplicative behaviour bias; no spatial fields. */
  private static ServerBotAiArenaContext ctxBiasing(final Map<String, Double> bias) {
    final ArenaObjective objective =
        new ArenaObjective() {
          @Override
          public String name() {
            return "fake";
          }

          @Override
          public Map<String, Double> behaviourBias() {
            return bias;
          }

          @Override
          public List<GoalTile> staticGoalTiles() {
            return List.of();
          }
        };
    return new ServerBotAiArenaContext(
        null, null, null, 0, 0, null, null, null, null, List.of(), 0.0, objective);
  }

  /** Behaviour that always offers {@code goal} with a fixed intrinsic score. */
  private static final class FixedBehaviour implements Behaviour {
    private final String name;
    private final TacticalGoal goal;
    private final double score;

    FixedBehaviour(final String name, final TacticalGoal goal, final double score) {
      this.name = name;
      this.goal = goal;
      this.score = score;
    }

    @Override
    public String name() {
      return this.name;
    }

    @Override
    public List<TacticalGoal> enumerate(final Blackboard bb) {
      return List.of(this.goal);
    }

    @Override
    public double intrinsicScore(final TacticalGoal goal, final Blackboard bb) {
      return this.score;
    }
  }

  /** Behaviour that offers nothing (e.g. its target despawned). */
  private static final class EmptyBehaviour implements Behaviour {
    private final String name;

    EmptyBehaviour(final String name) {
      this.name = name;
    }

    @Override
    public String name() {
      return this.name;
    }

    @Override
    public List<TacticalGoal> enumerate(final Blackboard bb) {
      return List.of();
    }

    @Override
    public double intrinsicScore(final TacticalGoal goal, final Blackboard bb) {
      return 0.0;
    }
  }

  /** Builds a throwaway {@code NearbyShip} so target-gated behaviours enumerate. */
  private static final class NearbyShipFixture {
    infinity.ai.NearbyShip make() {
      return new infinity.ai.NearbyShip(
          new EntityId(9),
          new com.simsilica.mathd.Vec3d(5, 0, 0),
          new com.simsilica.mathd.Quatd(),
          new com.simsilica.mathd.Vec3d(),
          1);
    }
  }
}
