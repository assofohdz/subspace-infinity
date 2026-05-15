// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import com.simsilica.event.EventBus;
import com.simsilica.sim.SimTime;
import infinity.events.arena.PlayerEnteredSession;
import infinity.events.arena.TargetedEvent;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Translates {@link PlayerEnteredSession} into a {@link TargetedEvent} with tag {@code "welcome"} for the entering player. */
public final class WelcomeService extends BaseInfinitySystem {

  private static final Logger log = LoggerFactory.getLogger(WelcomeService.class);

  public WelcomeService() {
    // no-arg ctor — wiring happens in initialize()
  }

  @Override
  protected void initialize() {
    EventBus.addListener(this, PlayerEnteredSession.playerEnteredSession);
  }

  @Override
  protected void terminate() {
    EventBus.removeListener(this, PlayerEnteredSession.playerEnteredSession);
  }

  @Override
  public void start() {
    // no-op: event-driven only
  }

  @Override
  public void stop() {
    // no-op: cleanup in terminate
  }

  /** EventBus reflective dispatch — name pattern is {@code on<EventTypeName>}. */
  public void onPlayerEnteredSession(final PlayerEnteredSession event) {
    if (log.isDebugEnabled()) {
      log.debug(
          "onPlayerEnteredSession player={} name={} → publishing TargetedEvent(welcome)",
          event.getPlayer(),
          event.getPlayerName());
    }
    EventBus.publish(
        TargetedEvent.targeted,
        new TargetedEvent(Set.of(event.getPlayer()), "welcome", event.getPlayerName()));
  }

  @Override
  public void update(final SimTime tpf) {
    // no-op: purely event-driven
  }
}
