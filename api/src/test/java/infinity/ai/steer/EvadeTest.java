// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.steer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverSnapshot;
import infinity.ai.NearbyShip;
import infinity.ai.PerceptionSnapshot;
import java.util.List;
import org.junit.Test;

public class EvadeTest {

  private static final PerceptionSnapshot EMPTY =
      new PerceptionSnapshot(List.of(), List.of(), List.of());

  private static final double LEAD_TIME = 0.5;
  private static final double THRUST = 1.0;

  @Test
  public void noThreatSetReturnsNull() {
    final Evade evade = new Evade(LEAD_TIME, THRUST);
    assertNull(evade.steer(atOrigin(), EMPTY));
  }

  @Test
  public void threatBehindRunsStraight() {
    // Threat at -Z, bot facing +Z → already running away. yaw ≈ 0, full thrust.
    final Evade evade = new Evade(LEAD_TIME, THRUST);
    evade.setThreat(stationaryThreatAt(0, -10));
    final Vec3d intent = evade.steer(atOrigin(), EMPTY);
    assertNotNull(intent);
    assertEquals(0.0, intent.x, 1e-9);
    assertEquals(THRUST, intent.z, 1e-9);
  }

  @Test
  public void threatAheadForcesFlip() {
    // Threat at +Z, bot facing +Z → need to turn 180°. Max yaw signal (sign chosen
    // arbitrarily by the perpendicular-edge case in Evade).
    final Evade evade = new Evade(LEAD_TIME, THRUST);
    evade.setThreat(stationaryThreatAt(0, 10));
    final Vec3d intent = evade.steer(atOrigin(), EMPTY);
    assertNotNull(intent);
    assertEquals("threat-ahead should drive max-magnitude yaw", 1.0, Math.abs(intent.x), 1e-9);
    assertEquals(THRUST, intent.z, 1e-9);
  }

  @Test
  public void threatToLeftProducesNegativeYaw() {
    // Threat at +X (bot's left). Away direction = -X. yaw = left.dot(awayDir) = +X·-X = -1.
    final Evade evade = new Evade(LEAD_TIME, THRUST);
    evade.setThreat(stationaryThreatAt(10, 0));
    final Vec3d intent = evade.steer(atOrigin(), EMPTY);
    assertNotNull(intent);
    assertTrue("threat-to-left should yaw away (negative)", intent.x < 0.0);
    assertEquals(THRUST, intent.z, 1e-9);
  }

  @Test
  public void leadPredictionExtrapolatesThreatVelocity() {
    // Threat at (0, 0, 10), velocity (10, 0, 0). After 0.5s, predicted at (5, 0, 10).
    // Away direction (from bot at origin) ≈ normalize((-5, 0, -10)) — mostly -Z, some -X.
    // Bot at identity facing +Z: forward.dot(away) = -Z·forward = negative → fwd<0 → max-flip.
    final Evade evade = new Evade(LEAD_TIME, THRUST);
    evade.setThreat(new NearbyShip(new EntityId(99), new Vec3d(0, 0, 10),
        new Quatd(), new Vec3d(10, 0, 0), 0));
    final Vec3d intent = evade.steer(atOrigin(), EMPTY);
    assertNotNull(intent);
    // Max magnitude from the flip-clamp branch.
    assertEquals(1.0, Math.abs(intent.x), 1e-9);
  }

  private static MoverSnapshot atOrigin() {
    return new MoverSnapshot(new Vec3d(), new Quatd(), new Vec3d());
  }

  private static NearbyShip stationaryThreatAt(final double x, final double z) {
    return new NearbyShip(new EntityId(99), new Vec3d(x, 0, z), new Quatd(), new Vec3d(), 0);
  }
}
