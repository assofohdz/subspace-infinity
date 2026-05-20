// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.wincondition;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import infinity.es.CrownHolder;
import infinity.es.Frequency;
import infinity.es.arena.ArenaId;
import infinity.modules.ModuleContext;
import infinity.modules.WinnerDeclaration;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;

/** Pins both roles of {@link LastCrownStandingWinCondition}: terminator + decider. */
public final class LastCrownStandingWinConditionTest {

  private DefaultEntityData ed;
  private ArenaId arenaId;
  private LastCrownStandingWinCondition module;

  @Before
  public void setUp() {
    ed = new DefaultEntityData();
    arenaId = new ArenaId("koth", ed.createEntity());
    module = new LastCrownStandingWinCondition(
        new ModuleContext(arenaId, arenaId.getOwner(), ed, null, null));
  }

  @Test
  public void noCrowns_doesNotTerminate_decidesUndecided() {
    assertFalse("no holders, no termination", module.checkTermination(arenaId).isPresent());
    assertEquals(WinnerDeclaration.UNDECIDED, module.declareWinner(arenaId));
  }

  @Test
  public void twoCrowns_doesNotTerminate() {
    spawnHolder(0, 1);
    spawnHolder(1, 1);

    assertFalse("two holders is not last-crown",
        module.checkTermination(arenaId).isPresent());
  }

  @Test
  public void oneCrown_terminates_andDecidesThatFreq() {
    spawnHolder(7, 3);

    final Optional<String> reason = module.checkTermination(arenaId);
    assertTrue("single holder triggers termination", reason.isPresent());
    assertEquals(7, module.declareWinner(arenaId).winningFreq());
  }

  @Test
  public void cachedWinner_persistsThroughLaterEntityChanges() {
    final EntityId survivor = spawnHolder(2, 1);
    final Optional<String> reason = module.checkTermination(arenaId);
    assertTrue(reason.isPresent());
    // Survivor dies (component removed); the decider still returns the cached winner.
    ed.removeComponent(survivor, CrownHolder.class);

    assertEquals("cached winner sticky until onRoundStart",
        2, module.declareWinner(arenaId).winningFreq());
  }

  @Test
  public void onRoundStart_reArms_clearsCachedWinner() {
    spawnHolder(0, 1);
    assertTrue(module.checkTermination(arenaId).isPresent());

    module.onRoundStart(arenaId, 2);

    assertEquals("re-armed after round-start",
        WinnerDeclaration.UNDECIDED, module.declareWinner(arenaId));
  }

  @Test
  public void foreignArenaHolder_ignored() {
    final EntityId foreign = ed.createEntity();
    ed.setComponent(foreign, new ArenaId("ffa", ed.createEntity()));
    ed.setComponent(foreign, new CrownHolder(1));
    ed.setComponent(foreign, new Frequency(99));

    assertFalse(module.checkTermination(arenaId).isPresent());
  }

  private EntityId spawnHolder(final int freq, final int crowns) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, arenaId);
    ed.setComponent(ship, new CrownHolder(crowns));
    ed.setComponent(ship, new Frequency(freq));
    return ship;
  }
}
