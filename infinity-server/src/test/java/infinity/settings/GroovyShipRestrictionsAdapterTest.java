// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import infinity.Ship;
import infinity.config.ArenaConfig;
import infinity.config.ShipRestrictionsConfig;
import org.junit.Test;

/** End-to-end DSL → {@link ShipRestrictionsConfig} via the arena-loader {@code shipRestrictions{…}} block. */
public class GroovyShipRestrictionsAdapterTest {

  private static final String ARENA_OPEN = "arena {\n";
  private static final String BLOCK_CLOSE = "}\n";
  private static final String RESTRICTIONS_OPEN = "  shipRestrictions {\n";
  private static final String RESTRICTIONS_CLOSE = "  }\n";
  private static final String VPATH = "t.groovy";

  @Test
  public void allowList_restrictsToListed() {
    final String src =
        ARENA_OPEN
            + RESTRICTIONS_OPEN
            + "    allow Ship.WARBIRD, Ship.JAVELIN\n"
            + RESTRICTIONS_CLOSE
            + BLOCK_CLOSE;
    final ArenaConfig cfg = new GroovyArenaLoader().evaluateSourceForTest(src, VPATH);
    final ShipRestrictionsConfig r = cfg.shipRestrictions();
    assertTrue("WARBIRD allowed", r.isAllowed(Ship.WARBIRD));
    assertTrue("JAVELIN allowed", r.isAllowed(Ship.JAVELIN));
    assertFalse("SPIDER not in allow-list", r.isAllowed(Ship.SPIDER));
    assertEquals(-1, r.maxPerTeam(Ship.WARBIRD));
  }

  @Test
  public void denyList_blocksListed_othersAllowed() {
    final String src =
        ARENA_OPEN
            + RESTRICTIONS_OPEN
            + "    deny Ship.SHARK\n"
            + RESTRICTIONS_CLOSE
            + BLOCK_CLOSE;
    final ArenaConfig cfg = new GroovyArenaLoader().evaluateSourceForTest(src, VPATH);
    final ShipRestrictionsConfig r = cfg.shipRestrictions();
    assertFalse("SHARK denied", r.isAllowed(Ship.SHARK));
    assertTrue("WARBIRD allowed (not in deny-list)", r.isAllowed(Ship.WARBIRD));
  }

  @Test
  public void mixed_allowWinsOverDeny() {
    // Allow-list non-empty → restricts to allow set; deny is effectively unreachable.
    final String src =
        ARENA_OPEN
            + RESTRICTIONS_OPEN
            + "    allow Ship.WARBIRD\n"
            + "    deny Ship.WARBIRD\n"
            + RESTRICTIONS_CLOSE
            + BLOCK_CLOSE;
    final ArenaConfig cfg = new GroovyArenaLoader().evaluateSourceForTest(src, VPATH);
    assertTrue("allow-list wins", cfg.shipRestrictions().isAllowed(Ship.WARBIRD));
  }

  @Test
  public void maxPerTeam_perEntry_capturedAsInt() {
    final String src =
        ARENA_OPEN
            + RESTRICTIONS_OPEN
            + "    maxPerTeam Ship.WARBIRD, 4\n"
            + "    maxPerTeam Ship.SPIDER, 2\n"
            + RESTRICTIONS_CLOSE
            + BLOCK_CLOSE;
    final ArenaConfig cfg = new GroovyArenaLoader().evaluateSourceForTest(src, VPATH);
    final ShipRestrictionsConfig r = cfg.shipRestrictions();
    assertEquals(4, r.maxPerTeam(Ship.WARBIRD));
    assertEquals(2, r.maxPerTeam(Ship.SPIDER));
    assertEquals(-1, r.maxPerTeam(Ship.JAVELIN));
  }

  @Test
  public void omitted_yieldsDefaults() {
    final String src = ARENA_OPEN + "  map 'foo.lvl'\n" + BLOCK_CLOSE;
    final ArenaConfig cfg = new GroovyArenaLoader().evaluateSourceForTest(src, VPATH);
    final ShipRestrictionsConfig r = cfg.shipRestrictions();
    assertTrue("every ship allowed by default", r.isAllowed(Ship.WARBIRD));
    assertTrue("every ship allowed by default", r.isAllowed(Ship.SHARK));
    assertEquals(-1, r.maxPerTeam(Ship.WARBIRD));
  }
}
