// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.brain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityId;
import infinity.ai.bt.Status;
import infinity.ai.steer.OrbitTarget;
import infinity.ai.steer.Pursue;
import infinity.ai.steer.Wander;
import infinity.es.ship.weapons.WeaponType;
import infinity.sim.WeaponsFiring;
import org.junit.Before;
import org.junit.Test;

public class FireWeaponTest {

  private Blackboard blackboard;
  private RecordingFiring firing;
  private FireWeapon action;

  @Before
  public void setUp() {
    blackboard =
        new Blackboard(new Pursue(0.5, 1.0), new Wander(1, 2, 0.5, 1.0), new OrbitTarget(15, 1.0));
    blackboard.setSelfId(new EntityId(42));
    firing = new RecordingFiring();
    blackboard.setFiring(firing);
    action = new FireWeapon(WeaponType.BULLET);
  }

  @Test
  public void missingFiringServiceReturnsFailure() {
    blackboard.setFiring(null);
    assertEquals(Status.FAILURE, action.tick(blackboard));
  }

  @Test
  public void missingSelfIdReturnsFailure() {
    blackboard.setSelfId(null);
    assertEquals(Status.FAILURE, action.tick(blackboard));
    assertNull(firing.lastAttacker);
  }

  @Test
  public void successDispatchesRequestWithCorrectShape() {
    assertEquals(Status.SUCCESS, action.tick(blackboard));
    assertEquals(new EntityId(42), firing.lastAttacker);
    assertEquals(WeaponType.BULLET, firing.lastWeaponType);
  }

  @Test
  public void weaponTypeFlowsThroughConstructor() {
    final FireWeapon bombAction = new FireWeapon(WeaponType.BOMB);
    assertEquals(Status.SUCCESS, bombAction.tick(blackboard));
    assertEquals(WeaponType.BOMB, firing.lastWeaponType);
  }

  private static final class RecordingFiring implements WeaponsFiring {
    EntityId lastAttacker;
    byte lastWeaponType;

    @Override
    public void requestFire(final EntityId attacker, final byte weaponType) {
      this.lastAttacker = attacker;
      this.lastWeaponType = weaponType;
    }
  }
}
