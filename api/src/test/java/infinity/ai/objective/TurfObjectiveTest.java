// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.ai.objective;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class TurfObjectiveTest {

  @Test
  public void assignRoleSplitsByIdParity() {
    final TurfObjective obj = new TurfObjective(List.of());
    final ArenaSnapshot snap = bot -> 0; // team unused by the simple turf split
    assertEquals("flag-defender", obj.assignRole(new EntityId(2), snap));
    assertEquals("flag-attacker", obj.assignRole(new EntityId(3), snap));
  }

  @Test
  public void deathmatchAssignsDefaultRole() {
    assertEquals("default", new DeathmatchObjective().assignRole(new EntityId(1), bot -> 0));
  }

  @Test
  public void staticGoalTilesAreTheFlagTiles() {
    final TurfObjective obj = new TurfObjective(List.of(new GoalTile(5, 7)));
    assertEquals("turf", obj.name());
    assertEquals(List.of(new GoalTile(5, 7)), obj.staticGoalTiles());
    assertEquals(2.0, obj.behaviourBias().get("hold-position"), 0.0);
  }

  @Test
  public void flagTilesAreDefensivelyCopied() {
    final List<GoalTile> mutable = new ArrayList<>();
    mutable.add(new GoalTile(1, 1));
    final TurfObjective obj = new TurfObjective(mutable);
    mutable.add(new GoalTile(2, 2));
    assertEquals(1, obj.staticGoalTiles().size());
    assertThrows(
        UnsupportedOperationException.class, () -> obj.staticGoalTiles().add(new GoalTile(3, 3)));
  }

  @Test
  public void deathmatchIsIdentity() {
    final DeathmatchObjective obj = new DeathmatchObjective();
    assertEquals("deathmatch", obj.name());
    assertTrue(obj.behaviourBias().isEmpty());
    assertTrue(obj.staticGoalTiles().isEmpty());
  }
}
