// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import infinity.ai.brain.Blackboard;
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
