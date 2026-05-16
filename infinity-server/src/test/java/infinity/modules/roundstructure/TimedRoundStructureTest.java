// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.roundstructure;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.SimTime;
import infinity.config.TimedRoundStructureConfig;
import infinity.es.arena.ArenaId;
import infinity.es.arena.RoundEndPending;
import infinity.modules.ModuleContext;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

/** Pins {@link TimedRoundStructure}'s emit-on-elapsed semantics. */
public final class TimedRoundStructureTest {

  @Test
  public void elapsedBeforeDuration_doesNotEmit() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    final ArenaId arenaId = new ArenaId("test", arenaEntity);
    final TimedRoundStructure m = new TimedRoundStructure(
        new ModuleContext(arenaId, arenaEntity, ed), new TimedRoundStructureConfig(5));

    m.onRoundStart(arenaId, 1);
    m.tickRoundStructure(arenaId, simTimeAt(0L));
    m.tickRoundStructure(arenaId, simTimeAt(TimeUnit.MINUTES.toNanos(4)));

    assertNull("not yet elapsed", ed.getComponent(arenaEntity, RoundEndPending.class));
  }

  @Test
  public void elapsedAtDuration_emitsRoundEndPending() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    final ArenaId arenaId = new ArenaId("test", arenaEntity);
    final TimedRoundStructure m = new TimedRoundStructure(
        new ModuleContext(arenaId, arenaEntity, ed), new TimedRoundStructureConfig(5));

    m.onRoundStart(arenaId, 1);
    m.tickRoundStructure(arenaId, simTimeAt(0L));
    m.tickRoundStructure(arenaId, simTimeAt(TimeUnit.MINUTES.toNanos(5)));

    assertNotNull(ed.getComponent(arenaEntity, RoundEndPending.class));
  }

  @Test
  public void roundStartReArmsTimer() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    final ArenaId arenaId = new ArenaId("test", arenaEntity);
    final TimedRoundStructure m = new TimedRoundStructure(
        new ModuleContext(arenaId, arenaEntity, ed), new TimedRoundStructureConfig(5));

    m.onRoundStart(arenaId, 1);
    m.tickRoundStructure(arenaId, simTimeAt(0L));
    m.tickRoundStructure(arenaId, simTimeAt(TimeUnit.MINUTES.toNanos(5)));
    ed.removeComponent(arenaEntity, RoundEndPending.class);

    // Re-arm for round 2.
    m.onRoundStart(arenaId, 2);
    m.tickRoundStructure(arenaId, simTimeAt(TimeUnit.MINUTES.toNanos(5)));
    m.tickRoundStructure(arenaId, simTimeAt(TimeUnit.MINUTES.toNanos(9)));
    assertNull("round 2 not yet elapsed", ed.getComponent(arenaEntity, RoundEndPending.class));

    m.tickRoundStructure(arenaId, simTimeAt(TimeUnit.MINUTES.toNanos(10)));
    assertNotNull("round 2 elapsed", ed.getComponent(arenaEntity, RoundEndPending.class));
  }

  private static SimTime simTimeAt(final long simNanos) {
    final SimTime t = new SimTime();
    t.setCurrentTime(simNanos);
    t.update(0L);
    return t;
  }
}
