// SPDX-License-Identifier: BSD-3-Clause
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import com.simsilica.mathd.Vec3d;
import infinity.es.ship.weapons.FireRequest;
import infinity.es.ship.weapons.WeaponType;
import org.junit.Test;

/**
 * Happy-path coverage for the P2-k-weapons split — verifies the three new fire systems
 * exist as separate classes with the post-split shape (eligibility/spawn/audio) and the
 * {@link FireRequest} record carries the post-eligibility payload.
 *
 * <p>Deeper drain round-trips (sessionAttack → FireRequest → projectile + sound) require
 * MPhys / PhysicsSpace plumbing — pin the shape here; rely on launch-time integration for
 * the wire-level behaviour.
 */
public class WeaponsFireSplitTest {

  @Test
  public void fireRequest_record_carriesPayload() {
    final Vec3d loc = new Vec3d(1, 2, 3);
    final Vec3d vel = new Vec3d(4, 5, 6);
    final FireRequest req = new FireRequest(WeaponType.BULLET, loc, vel);
    assertEquals("weaponType preserved", WeaponType.BULLET, req.weaponType());
    assertSame("location preserved", loc, req.location());
    assertSame("velocity preserved", vel, req.velocity());
  }

  @Test
  public void fireRequest_noArgCtor_yieldsNullPayload() {
    final FireRequest req = new FireRequest();
    assertEquals("default weaponType", (byte) 0, req.weaponType());
    assertNull("default location", req.location());
    assertNull("default velocity", req.velocity());
  }

  @Test
  public void splitSystems_areDistinctClasses() throws Exception {
    final Class<?> eligibility =
        Class.forName("infinity.systems.ship.WeaponsFireEligibilitySystem");
    final Class<?> spawn =
        Class.forName("infinity.systems.ship.WeaponsProjectileSpawnSystem");
    final Class<?> audio =
        Class.forName("infinity.systems.ship.WeaponsFireAudioSystem");
    assertNotNull(eligibility);
    assertNotNull(spawn);
    assertNotNull(audio);
    // sessionAttack lives on the eligibility system (the queue owner) post-split.
    assertNotNull(eligibility.getDeclaredMethod(
        "sessionAttack", com.simsilica.es.EntityId.class, byte.class));
  }

}
