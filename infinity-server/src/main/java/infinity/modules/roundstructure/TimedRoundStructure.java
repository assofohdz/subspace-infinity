// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.roundstructure;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.sim.SimTime;
import infinity.config.TimedRoundStructureConfig;
import infinity.es.arena.ArenaId;
import infinity.es.arena.RoundEndPending;
import infinity.modules.ModuleContext;
import infinity.modules.RoundStructureModule;
import java.util.concurrent.TimeUnit;

/**
 * Terminator-style round structure — emits {@link RoundEndPending} on the arena
 * entity once {@code minutes} have elapsed since the round started. Re-arms on
 * each {@code onRoundStart} so subsequent rounds re-time from their own start.
 */
public final class TimedRoundStructure implements RoundStructureModule {

  private final EntityData ed;
  private final EntityId arenaEntity;
  private final long durationNanos;
  private long roundStartNanos = -1;
  private boolean emitted;

  public TimedRoundStructure(
      final ModuleContext ctx, final TimedRoundStructureConfig config) {
    this.ed = ctx.ed();
    this.arenaEntity = ctx.arenaEntity();
    this.durationNanos = TimeUnit.MINUTES.toNanos(config.minutes());
  }

  @Override
  public void onRoundStart(final ArenaId arenaId, final int roundNumber) {
    roundStartNanos = -1;
    emitted = false;
  }

  @Override
  public void tickRoundStructure(final ArenaId arenaId, final SimTime time) {
    if (emitted) {
      return;
    }
    final long now = time.getTime();
    if (roundStartNanos < 0) {
      roundStartNanos = now;
      return;
    }
    if (now - roundStartNanos < durationNanos) {
      return;
    }
    ed.setComponent(arenaEntity, new RoundEndPending());
    emitted = true;
  }
}
