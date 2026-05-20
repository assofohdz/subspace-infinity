// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.modules.roundstructure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.SimTime;
import infinity.config.TimedRoundStructureConfig;
import infinity.es.arena.ArenaId;
import infinity.es.arena.RoundEndPending;
import infinity.modules.ModuleContext;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandBiFunction;
import infinity.sim.CommandFunction;
import infinity.sim.CommandTriFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/** Pins {@link TimedRoundStructure}'s emit-on-elapsed semantics. */
public final class TimedRoundStructureTest {

  private static final String ARENA_NAME = "test";

  @Test
  public void elapsedBeforeDuration_doesNotEmit() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    final ArenaId arenaId = new ArenaId(ARENA_NAME, arenaEntity);
    final TimedRoundStructure m = new TimedRoundStructure(
        new ModuleContext(arenaId, arenaEntity, ed, null, null), new TimedRoundStructureConfig(5));

    m.onRoundStart(arenaId, 1);
    m.tickRoundStructure(arenaId, simTimeAt(0L));
    m.tickRoundStructure(arenaId, simTimeAt(TimeUnit.MINUTES.toNanos(4)));

    assertNull("not yet elapsed", ed.getComponent(arenaEntity, RoundEndPending.class));
  }

  @Test
  public void elapsedAtDuration_emitsRoundEndPending() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    final ArenaId arenaId = new ArenaId(ARENA_NAME, arenaEntity);
    final TimedRoundStructure m = new TimedRoundStructure(
        new ModuleContext(arenaId, arenaEntity, ed, null, null), new TimedRoundStructureConfig(5));

    m.onRoundStart(arenaId, 1);
    m.tickRoundStructure(arenaId, simTimeAt(0L));
    m.tickRoundStructure(arenaId, simTimeAt(TimeUnit.MINUTES.toNanos(5)));

    assertNotNull(ed.getComponent(arenaEntity, RoundEndPending.class));
  }

  @Test
  public void roundStartReArmsTimer() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    final ArenaId arenaId = new ArenaId(ARENA_NAME, arenaEntity);
    final TimedRoundStructure m = new TimedRoundStructure(
        new ModuleContext(arenaId, arenaEntity, ed, null, null), new TimedRoundStructureConfig(5));

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

  @Test
  public void announcesRemainingTimeEveryMinute_skipsZeroMark() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    final ArenaId arenaId = new ArenaId(ARENA_NAME, arenaEntity);
    final CapturingChat chat = new CapturingChat();
    final TimedRoundStructure m = new TimedRoundStructure(
        new ModuleContext(arenaId, arenaEntity, ed, chat, null), new TimedRoundStructureConfig(5));

    m.onRoundStart(arenaId, 1);
    m.tickRoundStructure(arenaId, simTimeAt(0L));
    // T = 0: no announcement (just cached start).
    assertEquals(0, chat.messages.size());

    m.tickRoundStructure(arenaId, simTimeAt(TimeUnit.MINUTES.toNanos(1)));
    m.tickRoundStructure(arenaId, simTimeAt(TimeUnit.MINUTES.toNanos(2)));
    m.tickRoundStructure(arenaId, simTimeAt(TimeUnit.MINUTES.toNanos(3)));
    m.tickRoundStructure(arenaId, simTimeAt(TimeUnit.MINUTES.toNanos(4)));
    // T = 5min: round ends; no countdown announcement at the end mark.
    m.tickRoundStructure(arenaId, simTimeAt(TimeUnit.MINUTES.toNanos(5)));

    assertEquals(
        List.of(
            "4 minutes remaining",
            "3 minutes remaining",
            "2 minutes remaining",
            "1 minute remaining"),
        chat.messages);
    assertNotNull("round emitted at the 5-min mark",
        ed.getComponent(arenaEntity, RoundEndPending.class));
  }

  @Test
  public void announceTargetsThisArena() {
    final DefaultEntityData ed = new DefaultEntityData();
    final EntityId arenaEntity = ed.createEntity();
    final ArenaId arenaId = new ArenaId(ARENA_NAME, arenaEntity);
    final CapturingChat chat = new CapturingChat();
    final TimedRoundStructure m = new TimedRoundStructure(
        new ModuleContext(arenaId, arenaEntity, ed, chat, null),
        new TimedRoundStructureConfig(2));

    m.onRoundStart(arenaId, 1);
    m.tickRoundStructure(arenaId, simTimeAt(0L));
    m.tickRoundStructure(arenaId, simTimeAt(TimeUnit.MINUTES.toNanos(1)));

    assertEquals(1, chat.messages.size());
    assertEquals("announcement targets this arena (service filters delivery)",
        arenaId.getArena(), chat.arenaTargets.get(0).getArena());
  }

  private static SimTime simTimeAt(final long simNanos) {
    final SimTime t = new SimTime();
    t.setCurrentTime(simNanos);
    t.update(0L);
    return t;
  }

  /** Test-only chat poster — captures public-message bodies for assertion. */
  private static final class CapturingChat implements ChatHostedPoster {
    final List<String> messages = new ArrayList<>();

    @Override
    public void postPublicMessage(final String from, final int messageType, final String message) {
      messages.add(message);
    }

    @Override
    public void postPrivateMessage(
        final String from, final int messageType, final EntityId targetEntityId, final String message) {
      // not under test
    }

    @Override
    public void postTeamMessage(
        final String from, final int messageType, final int targetFrequency, final String message) {
      // not under test
    }

    /** Per-arena targets also captured for assertion symmetry; service does the filtering. */
    final List<ArenaId> arenaTargets = new ArrayList<>();

    @Override
    public void postArenaMessage(
        final String from,
        final int messageType,
        final ArenaId arena,
        final String message) {
      messages.add(message);
      arenaTargets.add(arena);
    }

    @Override
    public void registerPatternTriConsumer(
        final Pattern pattern,
        final String description,
        final CommandTriFunction<EntityId, EntityId, Matcher, String> c) {
      // not under test
    }

    @Override
    public void registerPatternBiConsumer(
        final Pattern pattern,
        final String description,
        final CommandBiFunction<EntityId, Matcher, String> c) {
      // not under test
    }

    @Override
    public void removePatternConsumer(final Pattern pattern) {
      // not under test
    }

    @Override
    public void registerCommandConsumer(
        final String cmd, final String helptext, final CommandFunction<Matcher, String> c) {
      // not under test
    }

    @Override
    public void removeCommandConsumer(final String cmd) {
      // not under test
    }
  }
}
