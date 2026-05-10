// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import com.simsilica.event.EventType;
import org.junit.Test;

/**
 * Smoke test for {@link AccountEvent} — the value object the
 * {@code AccountHostedService} publishes on the {@code EventBus} when a
 * player logs in or out. Seed coverage for {@code infinity.net.*} per
 * Tier-2 finding #4.
 *
 * <p>{@code AccountEvent} carries the {@link com.jme3.network.HostedConnection}
 * (server-side ABI), the player name, and the new player {@link EntityId} so
 * downstream listeners (chat, scoreboard, RMI subscribers) can react. The
 * {@code playerLoggedOn} / {@code playerLoggedOff} static
 * {@link EventType}s are the contract every subscriber filters on, so they
 * must be (a) non-null at class-load time and (b) distinct from each other.
 */
public class AccountEventTest {

  @Test
  public void eventTypes_areCreatedAtClassLoad() {
    assertNotNull("playerLoggedOn EventType must be initialised", AccountEvent.playerLoggedOn);
    assertNotNull("playerLoggedOff EventType must be initialised", AccountEvent.playerLoggedOff);
  }

  @Test
  public void eventTypes_areDistinct() {
    // logon and logoff must be filterable independently — same EventType
    // instance would silently fan out one notification to both subscriber
    // sets and break chat presence updates etc.
    assertNotEquals(
        "playerLoggedOn and playerLoggedOff must be distinct EventTypes",
        AccountEvent.playerLoggedOn,
        AccountEvent.playerLoggedOff);
  }

  @Test
  public void constructor_carriesAllFields() {
    final EntityId player = new EntityId(42L);

    // HostedConnection requires a real network stack to instantiate — but
    // AccountEvent only stores the reference and never dereferences it
    // until a subscriber pulls it out, so null is a fine stand-in for the
    // value-object smoke test.
    final AccountEvent ev = new AccountEvent(null, "Alice", player);

    assertNull("connection accessor returns the stored ref (null here)", ev.getConnection());
    assertEquals("Alice", ev.getPlayerName());
    assertSame(player, ev.getPlayerEntity());
  }

  @Test
  public void toString_containsKeyFields() {
    final EntityId player = new EntityId(7L);
    final AccountEvent ev = new AccountEvent(null, "Bob", player);

    final String s = ev.toString();
    assertNotNull(s);
    // toString is a debugging aid for log-grep — pin the field names so a
    // refactor of the toString helper doesn't silently strip them.
    assertTrue("toString must include the player name", s.contains("Bob"));
    assertTrue("toString must include the player entity", s.contains(player.toString()));
  }
}
