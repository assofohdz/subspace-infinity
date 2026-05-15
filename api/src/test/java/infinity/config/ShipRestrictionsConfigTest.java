// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import infinity.Ship;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/** Defensive-copy + allow/deny precedence + maxPerTeam lookup for {@link ShipRestrictionsConfig}. */
public class ShipRestrictionsConfigTest {

  @Test
  public void defaults_everyShipAllowed() {
    assertTrue(ShipRestrictionsConfig.DEFAULTS.isAllowed(Ship.WARBIRD));
    assertTrue(ShipRestrictionsConfig.DEFAULTS.isAllowed(Ship.SHARK));
    assertEquals(-1, ShipRestrictionsConfig.DEFAULTS.maxPerTeam(Ship.WARBIRD));
  }

  @Test
  public void allowList_winsOverDenyList() {
    final ShipRestrictionsConfig cfg =
        new ShipRestrictionsConfig(
            EnumSet.of(Ship.WARBIRD), EnumSet.of(Ship.WARBIRD), Map.of());
    assertTrue("allow-list wins when both lists name the ship", cfg.isAllowed(Ship.WARBIRD));
    assertFalse("ship not in allow-list is denied", cfg.isAllowed(Ship.JAVELIN));
  }

  @Test
  public void emptyAllow_denyListBlocks() {
    final ShipRestrictionsConfig cfg =
        new ShipRestrictionsConfig(
            EnumSet.noneOf(Ship.class), EnumSet.of(Ship.SHARK), Map.of());
    assertFalse(cfg.isAllowed(Ship.SHARK));
    assertTrue(cfg.isAllowed(Ship.WARBIRD));
  }

  @Test
  public void maxPerTeam_missingShip_returnsMinusOne() {
    final ShipRestrictionsConfig cfg =
        new ShipRestrictionsConfig(
            EnumSet.noneOf(Ship.class),
            EnumSet.noneOf(Ship.class),
            Map.of(Ship.WARBIRD, Integer.valueOf(4)));
    assertEquals(4, cfg.maxPerTeam(Ship.WARBIRD));
    assertEquals(-1, cfg.maxPerTeam(Ship.JAVELIN));
  }

  @Test
  public void defensiveCopy_callerCannotMutate() {
    // Author-side mutation of the inputs must not leak into the record's stored state.
    final Set<Ship> allow = EnumSet.of(Ship.WARBIRD);
    final Set<Ship> deny = EnumSet.of(Ship.SHARK);
    final Map<Ship, Integer> caps = new HashMap<>();
    caps.put(Ship.WARBIRD, Integer.valueOf(2));

    final ShipRestrictionsConfig cfg = new ShipRestrictionsConfig(allow, deny, caps);

    allow.add(Ship.JAVELIN);
    deny.add(Ship.WEASEL);
    caps.put(Ship.WARBIRD, Integer.valueOf(99));

    assertFalse("post-construction caller mutation must not affect allow-list",
        cfg.isAllowed(Ship.JAVELIN));
    assertEquals("post-construction caller mutation must not affect maxPerTeam",
        2, cfg.maxPerTeam(Ship.WARBIRD));
    assertNotSame(allow, cfg.allowed());
  }

  @Test
  public void emptyInputs_yieldImmutableEmpty() {
    final ShipRestrictionsConfig cfg = ShipRestrictionsConfig.DEFAULTS;
    assertTrue(cfg.allowed().isEmpty());
    assertTrue(cfg.denied().isEmpty());
    assertTrue(cfg.maxPerTeam().isEmpty());
  }
}
