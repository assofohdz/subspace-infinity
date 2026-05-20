// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.roundstructure;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.sim.SimTime;
import infinity.config.CrownResetRoundStructureConfig;
import infinity.es.arena.ArenaId;
import infinity.es.arena.RoundEndPending;
import infinity.modules.ModuleContext;
import infinity.modules.RoundStructureModule;
import infinity.sim.ChatHostedPoster;
import infinity.sim.MessageTypes;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nullable;

/**
 * KOTH round structure — timer terminator with crown-themed chat announcements. Identical
 * timer state-machine to {@code TimedRoundStructure}; the differentiator is announcement
 * text ("N minutes until coronation") and intent (this round structure pairs with the
 * {@code Crowns} mechanic + {@code LastCrownStandingWinCondition} for KOTH composition).
 * Crown lifecycle (distribute / despawn) lives on the {@code Crowns} mechanic.
 *
 * <p>Announces every {@link #ANNOUNCE_INTERVAL_NANOS} via
 * {@link ChatHostedPoster#postArenaMessage} — service filters delivery to players in this
 * arena.
 */
public final class CrownResetRoundStructure implements RoundStructureModule {

  private static final long ANNOUNCE_INTERVAL_NANOS = TimeUnit.MINUTES.toNanos(1);
  private static final String CHAT_SENDER = "crown-keeper";

  private final EntityData ed;
  private final ArenaId arenaId;
  private final EntityId arenaEntity;
  private final long durationNanos;
  private final ChatHostedPoster chat;
  private long roundStartNanos = -1;
  private long nextAnnounceNanos = -1;
  private boolean emitted;

  public CrownResetRoundStructure(
      final ModuleContext ctx, final CrownResetRoundStructureConfig config) {
    this.ed = ctx.ed();
    this.arenaId = ctx.arenaId();
    this.arenaEntity = ctx.arenaEntity();
    this.durationNanos = TimeUnit.MINUTES.toNanos(config.minutes());
    this.chat = ctx.chat();
  }

  @Override
  public void onRoundStart(final ArenaId arenaIdParam, final int roundNumber) {
    roundStartNanos = -1;
    nextAnnounceNanos = -1;
    emitted = false;
  }

  @Override
  public void tickRoundStructure(final ArenaId arenaIdParam, final SimTime time) {
    if (emitted) {
      return;
    }
    final long now = time.getTime();
    if (roundStartNanos < 0) {
      roundStartNanos = now;
      nextAnnounceNanos = now + ANNOUNCE_INTERVAL_NANOS;
      return;
    }
    final long elapsed = now - roundStartNanos;
    if (elapsed >= durationNanos) {
      ed.setComponent(arenaEntity, new RoundEndPending());
      emitted = true;
      return;
    }
    if (now >= nextAnnounceNanos) {
      announceTimeRemaining(durationNanos - elapsed);
      nextAnnounceNanos += ANNOUNCE_INTERVAL_NANOS;
    }
  }

  /** Posts {@code "N minute(s) until coronation"} via {@link ChatHostedPoster#postArenaMessage}. */
  private void announceTimeRemaining(final long remainingNanos) {
    @Nullable final ChatHostedPoster poster = chat;
    if (poster == null) {
      return;
    }
    final long remainingMinutes = TimeUnit.NANOSECONDS.toMinutes(remainingNanos);
    if (remainingMinutes <= 0) {
      return;
    }
    final String unit = remainingMinutes == 1 ? "minute" : "minutes";
    poster.postArenaMessage(
        CHAT_SENDER, MessageTypes.MESSAGE, arenaId,
        remainingMinutes + " " + unit + " until coronation");
  }
}
