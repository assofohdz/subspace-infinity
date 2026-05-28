// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.steer;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import infinity.ai.MoverSnapshot;
import infinity.ai.field.DistanceField;
import infinity.ai.field.FieldGradient;
import infinity.ai.field.GradientField;
import infinity.ai.field.NavGrids;
import infinity.ai.field.NavigationFields;
import org.junit.Test;

public class WallRepulsionTest {

  private final WallRepulsion repel = new WallRepulsion(3, 1.0);

  private static MoverSnapshot at(final int x, final int z) {
    // Identity orientation: forward = +Z, left = +X.
    return new MoverSnapshot(new Vec3d(x, 0, z), new Quatd(), new Vec3d());
  }

  @Test
  public void openSurroundings_returnNull() {
    final MoverSnapshot self = at(3, 3); // 7x7, radius-3 box stays in-bounds + all passable
    assertNull(repel.steer(self, grid(open(7)), 0, 0));
  }

  @Test
  public void symmetricCorridor_cancelsToNull() {
    final boolean[][] g = open(7);
    for (int z = 0; z < 7; z++) {
      g[z][0] = false; // left wall column
      g[z][6] = false; // right wall column — symmetric about x=3
    }
    assertNull("opposed walls cancel — corridor isn't blocked", repel.steer(at(3, 3), grid(g), 0, 0));
  }

  @Test
  public void wallOnLeft_turnsTowardOpenRight() {
    final boolean[][] g = open(7);
    for (int z = 0; z < 7; z++) {
      g[z][0] = false; // wall column on the left
    }
    final Vec3d push = repel.steer(at(1, 3), grid(g), 0, 0);
    assertNotNull("a one-sided wall produces an escape", push);
    assertTrue("yaw turns toward open space (+X / right), away from the wall", push.x > 0.0);
  }

  private static boolean[][] open(final int n) {
    final boolean[][] g = new boolean[n][n];
    for (final boolean[] row : g) {
      java.util.Arrays.fill(row, true);
    }
    return g;
  }

  /** Minimal {@link NavigationFields} backed by a passability grid — only passableAt is exercised. */
  private static NavigationFields grid(final boolean[][] passable) {
    return new NavigationFields() {
      @Override
      public DistanceField fieldFor(final int gx, final int gy) {
        return DistanceField.EMPTY;
      }

      @Override
      public GradientField gradientFor(final int gx, final int gy) {
        return new FieldGradient(DistanceField.EMPTY);
      }

      @Override
      public void evict(final int gx, final int gy) {
        // no-op
      }

      @Override
      public boolean lineOfSight(final int ax, final int ay, final int bx, final int by) {
        return NavGrids.lineOfSight(passable, ax, ay, bx, by);
      }

      @Override
      public boolean passableAt(final int x, final int y) {
        return NavGrids.passable(passable, x, y);
      }
    };
  }
}
