// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.tactical;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import infinity.ai.brain.Blackboard;
import infinity.ai.capability.BotSynergyTable;
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
  public void disengageFitTracksEnergy() {
    // disengage stays a baseline behaviour (1 − energyFraction): flee harder as energy drains.
    final DisengageBehaviour disengage = new DisengageBehaviour();
    final Disengage dg = new Disengage(new EntityId(9));
    bb.setEnergy(90, 100);
    final double high = disengage.intrinsicScore(dg, bb);
    bb.setEnergy(10, 100);
    final double low = disengage.intrinsicScore(dg, bb);
    assertTrue("disengage wants out more at low energy", low > high);
  }

  @Test
  public void engageScoreIsWeightedSumOverInputs() {
    // engage (upgraded, ADR-0016): score = convex sum of SEED_FIT coefficients over the inputs.
    final EngageBehaviour engage = new EngageBehaviour(() -> new BotSynergyTable(Map.of()));
    final Engage eg = new Engage(new EntityId(9));
    bb.setSituationalInputs(inputs(1.0, 1.0));
    assertEquals("all fit inputs 1.0 ⇒ convex sum = 1.0", 1.0, engage.intrinsicScore(eg, bb), 1e-9);
    bb.setSituationalInputs(inputs(0.0, 1.0)); // los=1 (engageable) but fit inputs 0
    assertEquals("all fit inputs 0 ⇒ 0", 0.0, engage.intrinsicScore(eg, bb), 1e-9);
  }

  @Test
  public void engageReadsFitCoefficientsFromTableNotSeed() {
    // Table fit weights range_fit alone → score tracks range_fit only, proving live coeff reads.
    final BotSynergyTable table =
        new BotSynergyTable(Map.of(), Map.of("engage", Map.of("range_fit", 1.0)));
    final EngageBehaviour engage = new EngageBehaviour(() -> table);
    bb.setSituationalInputs(
        SituationalInputs.builder().set("range_fit", 0.5).set("energy_adv", 1.0).build());
    assertEquals(0.5, engage.intrinsicScore(new Engage(new EntityId(9)), bb), 1e-9);
  }

  @Test
  public void assassinateScoresIsolatedHighBountyTarget() {
    final AssassinateBehaviour assassin =
        new AssassinateBehaviour(() -> new BotSynergyTable(Map.of()));
    bb.setTarget(new NearbyShipFixture().make());
    final Engage eg = new Engage(new EntityId(9));
    // SEED: 0.30 bounty_pull + 0.30 isolation + 0.20 approach_safety + 0.20 concealment
    bb.setSituationalInputs(
        SituationalInputs.builder()
            .set("los", 1.0)
            .set("bounty_pull", 1.0)
            .set("isolation", 1.0)
            .set("approach_safety", 1.0)
            .set("concealment", 1.0)
            .build());
    assertEquals(1.0, assassin.intrinsicScore(eg, bb), 1e-9);
    // a crowded, low-bounty target (only los set) scores 0 — not a pick.
    bb.setSituationalInputs(SituationalInputs.builder().set("los", 1.0).build());
    assertEquals(0.0, assassin.intrinsicScore(eg, bb), 1e-9);
  }

  @Test
  public void assassinateWithoutLineOfSightEnumeratesNothing() {
    final AssassinateBehaviour assassin =
        new AssassinateBehaviour(() -> new BotSynergyTable(Map.of()));
    bb.setTarget(new NearbyShipFixture().make());
    bb.setSituationalInputs(SituationalInputs.builder().set("los", 0.0).build());
    assertTrue("occluded target not picked", assassin.enumerate(bb).isEmpty());
  }

  @Test
  public void engageWithoutLineOfSightEnumeratesNothing() {
    final EngageBehaviour engage = new EngageBehaviour(() -> new BotSynergyTable(Map.of()));
    bb.setSelf(new infinity.ai.MoverState(
        new com.simsilica.mathd.Vec3d(), new com.simsilica.mathd.Quatd(),
        new com.simsilica.mathd.Vec3d()));
    bb.setPerception(new infinity.ai.PerceptionSnapshot(
        List.of(new NearbyShipFixture().make()), List.of(), List.of()));
    bb.setSituationalInputs(inputs(1.0, 0.0)); // los = 0 ⇒ occluded
    assertTrue("occluded target not engaged", engage.enumerate(bb).isEmpty());
    bb.setSituationalInputs(inputs(1.0, 1.0)); // los = 1 ⇒ clear
    assertEquals("clear target engaged", 1, engage.enumerate(bb).size());
  }

  @Test
  public void engageOffersAllAliveThreatsNearestFirst() {
    // Regression for bot-ai-v3 #01.2: when two threats are near-equidistant, the planner's
    // stickiness guard needs the running Engage's target in the candidate list so its score
    // is computed (not left at NEGATIVE_INFINITY). EngageBehaviour now offers ALL alive threats
    // — nearest first so the no-current-goal tiebreak still picks nearest.
    final EngageBehaviour engage = new EngageBehaviour(() -> new BotSynergyTable(Map.of()));
    bb.setSelf(new infinity.ai.MoverState(
        new com.simsilica.mathd.Vec3d(0, 0, 0), new com.simsilica.mathd.Quatd(),
        new com.simsilica.mathd.Vec3d()));
    final infinity.ai.NearbyShip far =
        new infinity.ai.NearbyShip(new EntityId(7),
            new com.simsilica.mathd.Vec3d(10, 0, 0), new com.simsilica.mathd.Quatd(),
            new com.simsilica.mathd.Vec3d(), 1);
    final infinity.ai.NearbyShip near =
        new infinity.ai.NearbyShip(new EntityId(5),
            new com.simsilica.mathd.Vec3d(3, 0, 0), new com.simsilica.mathd.Quatd(),
            new com.simsilica.mathd.Vec3d(), 1);
    bb.setPerception(new infinity.ai.PerceptionSnapshot(List.of(far, near), List.of(), List.of()));
    bb.setSituationalInputs(inputs(1.0, 1.0));
    final List<TacticalGoal> goals = engage.enumerate(bb);
    assertEquals("both threats enumerated", 2, goals.size());
    assertEquals("nearest first", new Engage(new EntityId(5)), goals.get(0));
    assertEquals("farthest second", new Engage(new EntityId(7)), goals.get(1));
  }

  @Test
  public void engageStickinessHoldsAcrossEquidistantThreats() {
    // Two threats at ~equal distance; running goal is Engage(threat-A). On the next planner
    // cadence, threat-B becomes nearest by a hair. Pre-fix: Engage(B) was the only candidate
    // → Engage(A)'s score stayed -INF → withinMargin bypassed → goal flipped to B (thrash).
    // Post-fix: Engage(A) AND Engage(B) both in the candidate list with equal intrinsicScore;
    // stickiness keeps the running Engage(A) within margin.
    final EngageBehaviour engage = new EngageBehaviour(() -> new BotSynergyTable(Map.of()));
    bb.setSelf(new infinity.ai.MoverState(
        new com.simsilica.mathd.Vec3d(0, 0, 0), new com.simsilica.mathd.Quatd(),
        new com.simsilica.mathd.Vec3d()));
    bb.setSituationalInputs(inputs(1.0, 1.0));
    final EntityId aId = new EntityId(5);
    final EntityId bId = new EntityId(7);
    final infinity.ai.NearbyShip a =
        new infinity.ai.NearbyShip(aId,
            new com.simsilica.mathd.Vec3d(3.0, 0, 0), new com.simsilica.mathd.Quatd(),
            new com.simsilica.mathd.Vec3d(), 1);
    final infinity.ai.NearbyShip bNearer =
        new infinity.ai.NearbyShip(bId,
            new com.simsilica.mathd.Vec3d(2.99, 0, 0), new com.simsilica.mathd.Quatd(),
            new com.simsilica.mathd.Vec3d(), 1);

    // Cycle 1: A is nearest, no running goal → plan picks Engage(A).
    bb.setPerception(new infinity.ai.PerceptionSnapshot(List.of(a, bNearer), List.of(), List.of()));
    // Set A as the running goal directly (matches the runtime invariant that bb.currentGoal is set
    // by the prior planner cycle).
    bb.setCurrentGoal(new Engage(aId));

    // Cycle 2: B becomes nearest by epsilon. Plan again — stickiness must hold A.
    final TacticalPlanner p = planner(engage);
    final ArchetypeConfig arch = new ArchetypeConfig("t", Map.of("engage", 1.0));
    assertEquals(
        "stickiness keeps Engage(A) when B becomes nearest by a hair",
        new Engage(aId),
        p.select(bb, arch));
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

  /** Engage's five fit inputs all set to {@code fit}, plus {@code los}; the engage-test helper. */
  private static SituationalInputs inputs(final double fit, final double los) {
    return SituationalInputs.builder()
        .set("range_fit", fit)
        .set("energy_adv", fit)
        .set("recharge_rdy", fit)
        .set("support", fit)
        .set("bounty_pull", fit)
        .set("los", los)
        .build();
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
