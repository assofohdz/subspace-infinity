// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.capability;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import infinity.BombLevel;
import infinity.BulletLevel;
import infinity.Ship;
import infinity.config.BombStats;
import infinity.config.BulletStats;
import infinity.config.MineStats;
import infinity.config.ShipConfig;
import infinity.config.ShipStat;
import infinity.config.StatusStats;
import java.util.List;
import org.junit.Test;

public class CapabilityDeriverTest {

  private static final double EPS = 1e-6;

  // Warbird: fastest, best gun (L4 @25cs), no mines/cloak.
  private static final ShipConfig WARBIRD =
      ship(Ship.WARBIRD, 300, 400, 250, 1500, 600,
          new BulletStats(BulletLevel.LEVEL_1, BulletLevel.LEVEL_4, 0, 25, 400),
          null, null, null);

  // Shark: slower, weak gun (L1 @50cs), carries mines + cloak.
  private static final ShipConfig SHARK =
      ship(Ship.SHARK, 200, 300, 150, 1000, 400,
          new BulletStats(BulletLevel.LEVEL_1, BulletLevel.LEVEL_1, 0, 50, 300),
          null,
          new MineStats(BombLevel.BOMB_1, BombLevel.BOMB_1, 0, 100, 200),
          new StatusStats(1, 0));

  private static final List<ShipConfig> ARENA = List.of(WARBIRD, SHARK);

  @Test
  public void mobilityNormalizesToOneForFastest() {
    final ArenaCapabilityNorms n = CapabilityDeriver.deriveNorms(ARENA);
    assertEquals(1.0, CapabilityDeriver.derive(WARBIRD, n).mobility(), EPS);
    assertTrue(CapabilityDeriver.derive(SHARK, n).mobility() < 1.0);
  }

  @Test
  public void minesGateAndCloakDeriveFromConfig() {
    final ArenaCapabilityNorms n = CapabilityDeriver.deriveNorms(ARENA);
    final CapabilityProfile shark = CapabilityDeriver.derive(SHARK, n);
    final CapabilityProfile warbird = CapabilityDeriver.derive(WARBIRD, n);
    assertEquals(1, shark.maxMines()); // has-mines gate
    assertEquals(0, warbird.maxMines());
    assertTrue(shark.cloak());
    assertFalse(warbird.cloak());
  }

  @Test
  public void burstDamageFavoursBetterGun() {
    final ArenaCapabilityNorms n = CapabilityDeriver.deriveNorms(ARENA);
    final CapabilityProfile warbird = CapabilityDeriver.derive(WARBIRD, n);
    final CapabilityProfile shark = CapabilityDeriver.derive(SHARK, n);
    assertEquals(1.0, warbird.burstDamage(), EPS); // L4 @25cs is the arena peak
    assertTrue(shark.burstDamage() < warbird.burstDamage());
  }

  @Test
  public void placeholderGatesAreFalse() {
    final CapabilityProfile p = CapabilityDeriver.derive(WARBIRD, CapabilityDeriver.deriveNorms(ARENA));
    assertFalse(p.bombBounce());
    assertFalse(p.bulletBounce());
    assertFalse(p.attachReceive());
  }

  @Test
  public void unarmedShipHasZeroDamageDims() {
    final ShipConfig unarmed = ship(Ship.TERRIER, 150, 200, 100, 2000, 800, null, null, null, null);
    final List<ShipConfig> arena = List.of(WARBIRD, unarmed);
    final CapabilityProfile p = CapabilityDeriver.derive(unarmed, CapabilityDeriver.deriveNorms(arena));
    assertEquals(0.0, p.burstDamage(), EPS);
    assertEquals(0.0, p.areaDamage(), EPS);
    assertEquals(0.0, p.rangeProfile(), EPS);
    assertTrue("tanky terrier still has tankiness", p.tankiness() > 0);
  }

  private static ShipStat stat(final int max) {
    return new ShipStat(max, max, 0);
  }

  private static ShipConfig ship(
      final Ship type,
      final int speed,
      final int rot,
      final int thrust,
      final int energy,
      final int recharge,
      final BulletStats bullets,
      final BombStats bombs,
      final MineStats mines,
      final StatusStats cloak) {
    return new ShipConfig(
        type, stat(rot), stat(thrust), stat(speed), stat(recharge), stat(energy),
        0.0, 0.0, 0.0, 500.0,
        bombs, null, bullets, mines, null, null,
        null, null, null, null, null,
        cloak, null, null, null, false);
  }
}
