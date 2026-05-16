// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.matchstructure;

import static org.junit.Assert.assertFalse;

import com.simsilica.es.base.DefaultEntityData;
import infinity.es.arena.ArenaId;
import infinity.modules.ModuleContext;
import infinity.modules.RoundOutcome;
import java.util.Map;
import org.junit.Test;

/** Pins {@link ContinuousMatchStructure}'s never-ends semantics. */
public final class ContinuousMatchStructureTest {

  @Test
  public void shouldMatchEnd_alwaysFalse() {
    final DefaultEntityData ed = new DefaultEntityData();
    final ArenaId arenaId = new ArenaId("ffa", ed.createEntity());
    final ContinuousMatchStructure m = new ContinuousMatchStructure(
        new ModuleContext(arenaId, arenaId.getOwner(), ed, null, null));

    assertFalse("round-end never escalates to match-end",
        m.shouldMatchEnd(arenaId, new RoundOutcome(7, "any", Map.of(), Map.of())));
    assertFalse("undecided round still doesn't end the match",
        m.shouldMatchEnd(arenaId, new RoundOutcome(-1, "", Map.of(), Map.of())));
  }
}
