// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.field.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class CombatDensityFieldTest {

  private static final double EPS = 1e-9;

  /** 20x20, origin (0,0), radius 3, decay 0.5. */
  private static CombatDensityField field() {
    return new CombatDensityField(20, 20, 0, 0, 3, 0.5);
  }

  @Test
  public void shotSplatsPeakAtOrigin() {
    final CombatDensityField f = field();
    f.addShot(10, 10);
    assertEquals(1.0, f.valueAt(10, 10), EPS);
    assertTrue(f.valueAt(11, 10) > 0.0);
    assertEquals("beyond radius is cold", 0.0, f.valueAt(14, 10), EPS);
  }

  @Test
  public void decayFadesHeat() {
    final CombatDensityField f = field();
    f.addShot(10, 10);
    f.decay(); // 0.5
    assertEquals(0.5, f.valueAt(10, 10), EPS);
    f.decay(); // 0.25
    assertEquals(0.25, f.valueAt(10, 10), EPS);
  }

  @Test
  public void incrementalAccumulationAcrossCadences() {
    final CombatDensityField f = field();
    f.addShot(10, 10); // heat 1.0
    f.decay(); // 0.5
    f.addShot(10, 10); // 0.5 + 1.0 = 1.5 — remembers recent combat
    assertEquals(1.5, f.valueAt(10, 10), EPS);
  }

  @Test
  public void coldFieldIsZero() {
    assertEquals(0.0, field().valueAt(10, 10), EPS);
  }

  @Test
  public void outOfBoundsReadsZero() {
    final CombatDensityField f = field();
    f.addShot(10, 10);
    assertEquals(0.0, f.valueAt(-1, 10), EPS);
    assertEquals(0.0, f.valueAt(99, 99), EPS);
  }
}
