// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import static org.junit.Assert.assertEquals;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverState;
import infinity.ai.NearbyShip;
import infinity.ai.PerceptionSnapshot;
import infinity.ai.bt.Behavior;
import infinity.ai.tactical.Disengage;
import infinity.ai.tactical.Engage;
import infinity.ai.tactical.Search;
import infinity.config.BotBrainConfig;
import org.junit.Before;
import org.junit.Test;

/** The goal-dispatched {@link CombatantBrain} root routes the planner's goal to the matching branch. */
public class CombatantBrainGoalDispatchTest {

  private static final EntityId TARGET_ID = new EntityId(2);

  private final CombatantBrain archetype = new CombatantBrain();
  private Behavior root;
  private Blackboard bb;

  @Before
  public void setUp() {
    root = archetype.createRoot(BotBrainConfig.DEFAULTS);
    bb = archetype.createBlackboard(BotBrainConfig.DEFAULTS);
    // Self at origin facing +Z; high energy so the low-energy reactive override never fires.
    bb.setSelf(new MoverState(new Vec3d(), new Quatd(), new Vec3d()));
    bb.setPerception(PerceptionSnapshot.EMPTY);
    bb.setEnergy(90, 100);
  }

  /** Target 90° to the right, in range but outside the aim cone (so no fire / no WeaponsFiring needed). */
  private NearbyShip targetInRange() {
    return new NearbyShip(TARGET_ID, new Vec3d(5, 0, 0), new Quatd(), new Vec3d(), 2);
  }

  @Test
  public void engageGoalRoutesToEngageBranch() {
    bb.setTarget(targetInRange());
    bb.setCurrentGoal(new Engage(TARGET_ID));
    root.tick(bb);
    assertEquals("Engage", bb.lastBranch());
  }

  @Test
  public void disengageGoalRoutesToEvade() {
    bb.setTarget(targetInRange());
    bb.setCurrentGoal(new Disengage(TARGET_ID));
    root.tick(bb);
    assertEquals("Evade", bb.lastBranch());
  }

  @Test
  public void searchGoalRoutesToWander() {
    bb.setCurrentGoal(new Search()); // no target
    root.tick(bb);
    assertEquals("Wander", bb.lastBranch());
  }

  @Test
  public void nullGoalFallsBackToV1Engage() {
    bb.setTarget(targetInRange());
    bb.setCurrentGoal(null); // planner hasn't run — v1 fallback engages an in-range target
    root.tick(bb);
    assertEquals("Engage", bb.lastBranch());
  }

  @Test
  public void nullGoalNoTargetFallsBackToWander() {
    bb.setCurrentGoal(null);
    root.tick(bb);
    assertEquals("Wander", bb.lastBranch());
  }

  @Test
  public void navigateGoalIsDormantAndFallsThrough() {
    // No arena context wired (slice #03 pending) → SteerToGoalTile fails → v1 fallback wander.
    bb.setCurrentGoal(new infinity.ai.tactical.NavigateToTile(new com.simsilica.mworld.TileId(0L)));
    root.tick(bb);
    assertEquals("Wander", bb.lastBranch());
  }
}
