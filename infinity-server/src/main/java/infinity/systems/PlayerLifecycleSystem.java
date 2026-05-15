// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import com.simsilica.event.EventBus;
import com.simsilica.sim.SimTime;
import infinity.events.arena.PlayerEnteredSession;
import infinity.net.AccountEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Translates {@link AccountEvent#playerLoggedOn} into {@link PlayerEnteredSession}. Login is the "fully up" boundary: connection→player mapping is populated, client RMI callbacks are shared, real player name is known. */
public final class PlayerLifecycleSystem extends BaseInfinitySystem {

  private static final Logger log = LoggerFactory.getLogger(PlayerLifecycleSystem.class);

  public PlayerLifecycleSystem() {
    // no-arg ctor — wiring happens in initialize()
  }

  @Override
  protected void initialize() {
    EventBus.addListener(this, AccountEvent.playerLoggedOn);
  }

  @Override
  protected void terminate() {
    EventBus.removeListener(this, AccountEvent.playerLoggedOn);
  }

  @Override
  public void start() {
    // no-op: event-driven only
  }

  @Override
  public void stop() {
    // no-op: cleanup in terminate
  }

  @Override
  public void update(final SimTime tpf) {
    // no-op: purely event-driven
  }

  /** EventBus reflective dispatch — login is the canonical "fully up" boundary. */
  public void onPlayerLoggedOn(final AccountEvent event) {
    if (log.isInfoEnabled()) {
      log.info(
          "PlayerLifecycleSystem.onPlayerLoggedOn player={} name={} → publishing PlayerEnteredSession",
          event.getPlayerEntity(),
          event.getPlayerName());
    }
    EventBus.publish(
        PlayerEnteredSession.playerEnteredSession,
        new PlayerEnteredSession(event.getPlayerEntity(), event.getPlayerName()));
  }
}
