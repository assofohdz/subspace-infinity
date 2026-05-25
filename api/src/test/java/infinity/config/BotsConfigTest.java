// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import infinity.Ship;
import java.util.List;
import org.junit.Test;

public class BotsConfigTest {

  @Test
  public void expandedRosterRepeatsByCountInBlockOrder() {
    final BotsConfig cfg =
        new BotsConfig(
            List.of(
                new BotShipConfig(Ship.WARBIRD, 2),
                new BotShipConfig(Ship.SHARK, 1),
                new BotShipConfig(Ship.LEVIATHAN, 1)));
    assertEquals(
        List.of(Ship.WARBIRD, Ship.WARBIRD, Ship.SHARK, Ship.LEVIATHAN), cfg.expandedRoster());
  }

  @Test
  public void emptyConfigIsEmptyWithNoRoster() {
    assertTrue(BotsConfig.DEFAULTS.isEmpty());
    assertTrue(BotsConfig.DEFAULTS.expandedRoster().isEmpty());
  }

  @Test
  public void tweakForReturnsShipEntryElseEmpty() {
    final BehaviourTweak t = new BehaviourTweak("anchor", TweakOp.MULTIPLY, 1.3);
    final BotsConfig cfg =
        new BotsConfig(List.of(new BotShipConfig(Ship.LEVIATHAN, 1, List.of(t))));
    assertEquals(List.of(t), cfg.tweakFor(Ship.LEVIATHAN));
    assertTrue(cfg.tweakFor(Ship.WARBIRD).isEmpty());
  }

  @Test
  public void multiplyTweakScalesAddTweakOffsets() {
    assertEquals(0.39, new BehaviourTweak("x", TweakOp.MULTIPLY, 1.3).apply(0.30), 1e-9);
    assertEquals(0.50, new BehaviourTweak("x", TweakOp.ADD, 0.20).apply(0.30), 1e-9);
  }

  @Test
  public void fromSymbolMapsStarAndPlus() {
    assertEquals(TweakOp.MULTIPLY, TweakOp.fromSymbol("*"));
    assertEquals(TweakOp.ADD, TweakOp.fromSymbol("+"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void fromSymbolRejectsUnknown() {
    TweakOp.fromSymbol("/");
  }
}
