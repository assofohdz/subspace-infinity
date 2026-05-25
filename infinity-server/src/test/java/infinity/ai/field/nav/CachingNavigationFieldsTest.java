// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.nav;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import infinity.ai.field.DistanceField;
import infinity.ai.field.NavigationFields;
import infinity.math.Vec2d;
import org.junit.Test;

public class CachingNavigationFieldsTest {

  private static boolean[][] open(final int w, final int h) {
    final boolean[][] g = new boolean[h][w];
    for (final boolean[] row : g) {
      java.util.Arrays.fill(row, true);
    }
    return g;
  }

  @Test
  public void fieldForCachesPerGoal() {
    final NavigationFields nav = new CachingNavigationFields(open(8, 8));
    final DistanceField a = nav.fieldFor(2, 2);
    final DistanceField b = nav.fieldFor(2, 2);
    assertSame(a, b);
  }

  @Test
  public void evictForcesRebuild() {
    final NavigationFields nav = new CachingNavigationFields(open(8, 8));
    final DistanceField a = nav.fieldFor(2, 2);
    nav.evict(2, 2);
    assertNotSame(a, nav.fieldFor(2, 2));
  }

  @Test
  public void distinctGoalsAreDistinctFields() {
    final NavigationFields nav = new CachingNavigationFields(open(8, 8));
    assertNotSame(nav.fieldFor(1, 1), nav.fieldFor(2, 2));
  }

  @Test
  public void gradientForHeadsToGoal() {
    final NavigationFields nav = new CachingNavigationFields(open(8, 8));
    final Vec2d dir = nav.gradientFor(0, 0).directionAt(5, 5);
    assertTrue(dir.x < 0);
    assertTrue(dir.y < 0);
  }
}
