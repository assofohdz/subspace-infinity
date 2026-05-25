// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.math;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.simsilica.mathd.Vec3d;
import org.junit.Test;

public class Vec2dTest {

  private static final double EPS = 1e-9;

  @Test
  public void fromXzDropsY() {
    final Vec2d v = Vec2d.fromXz(new Vec3d(3, 99, 7));
    assertEquals(3.0, v.x, EPS);
    assertEquals(7.0, v.y, EPS);
  }

  @Test
  public void toXzPutsYAtZero() {
    final Vec3d v = new Vec2d(3, 7).toXz();
    assertEquals(3.0, v.x, EPS);
    assertEquals(0.0, v.y, EPS);
    assertEquals(7.0, v.z, EPS);
  }

  @Test
  public void arithmetic() {
    final Vec2d a = new Vec2d(1, 2);
    final Vec2d b = new Vec2d(3, 5);
    assertEquals(new Vec2d(4, 7), a.add(b));
    assertEquals(new Vec2d(-2, -3), a.subtract(b));
    assertEquals(new Vec2d(2, 4), a.mult(2.0));
    assertEquals(13.0, a.dot(b), EPS);
  }

  @Test
  public void length() {
    final Vec2d v = new Vec2d(3, 4);
    assertEquals(25.0, v.lengthSq(), EPS);
    assertEquals(5.0, v.length(), EPS);
  }

  @Test
  public void normalizeUnit() {
    final Vec2d n = new Vec2d(0, 4).normalize();
    assertEquals(0.0, n.x, EPS);
    assertEquals(1.0, n.y, EPS);
    assertEquals(1.0, n.length(), EPS);
  }

  @Test
  public void normalizeZeroIsZero() {
    assertEquals(Vec2d.ZERO, new Vec2d(0, 0).normalize());
    assertEquals(Vec2d.ZERO, new Vec2d(1e-12, 0).normalize());
  }

  @Test
  public void equalsAndHashCode() {
    assertEquals(new Vec2d(1.5, 2.5), new Vec2d(1.5, 2.5));
    assertEquals(new Vec2d(1.5, 2.5).hashCode(), new Vec2d(1.5, 2.5).hashCode());
    assertTrue(!new Vec2d(1, 2).equals(new Vec2d(2, 1)));
  }
}
