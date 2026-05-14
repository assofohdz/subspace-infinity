// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.EntityId;
import infinity.sim.AccessLevel;
import java.util.Set;
import org.junit.Test;

/**
 * Server-wiring smoke test for {@link AccountHostedService}'s access-level
 * lookup table. Seed coverage for {@code infinity.server.*} per Tier-2
 * finding #4.
 *
 * <p>{@code AccountHostedService} is a {@code AbstractHostedConnectionService}
 * subclass — its full {@code start()} path requires a hosted RMI service and
 * an {@code EntityDataHostedService}, which would force a real jME network
 * stack. The access-level helpers ({@link AccountHostedService#addOperator},
 * {@link AccountHostedService#getAccessLevel}, the {@code is*} predicates)
 * operate on an in-process {@code Map<EntityId, AccessLevel>} only; they
 * don't touch the network and stay testable in isolation.
 *
 * <p>The constructor takes a {@code serverInfo} string and re-initialises
 * the static {@code operators} map, so each test method gets a clean slate
 * by constructing a fresh service.
 */
public class AccountHostedServiceAccessLevelTest {

  private static final String TEST_SERVER_NAME = "test-server";

  private static EntityId id(final long raw) {
    return new EntityId(raw);
  }

  @Test
  public void getAccessLevel_unknownId_returnsPlayerLevel() {
    final AccountHostedService svc = new AccountHostedService(TEST_SERVER_NAME);

    assertEquals(
        "unknown ids default to PLAYER_LEVEL",
        AccessLevel.PLAYER_LEVEL,
        svc.getAccessLevel(id(42L)));
  }

  @Test
  public void getAccessLevel_nullId_returnsPlayerLevel() {
    final AccountHostedService svc = new AccountHostedService(TEST_SERVER_NAME);

    // Null is reachable from chat-command parsing paths where the source id
    // hasn't been resolved yet; the contract is "default to least privilege."
    assertEquals(AccessLevel.PLAYER_LEVEL, svc.getAccessLevel(null));
  }

  @Test
  public void addOperator_storesLevel_andTierPredicatesReflectIt() {
    final AccountHostedService svc = new AccountHostedService(TEST_SERVER_NAME);
    final EntityId moderator = id(7L);

    svc.addOperator(moderator, AccessLevel.MODERATOR_LEVEL);

    assertEquals(AccessLevel.MODERATOR_LEVEL, svc.getAccessLevel(moderator));
    assertTrue("moderator >= moderator", svc.isModerator(moderator));
    assertTrue("moderator >= ER", svc.isER(moderator));
    assertTrue("moderator >= LR", svc.isLR(moderator));
    assertFalse("moderator < SMod", svc.isSmod(moderator));
    assertFalse("moderator < Sysop", svc.isSysop(moderator));
    assertFalse("moderator < Owner", svc.isOwner(moderator));
  }

  @Test
  public void addOperator_atPlayerLevel_isStored() {
    // PLAYER_LEVEL is the lowest valid tier; adding it explicitly is
    // redundant (default) but should still be tracked rather than silently
    // dropped — the level boundary in addOperator() is `< PLAYER_LEVEL`,
    // so PLAYER_LEVEL itself is allowed.
    final AccountHostedService svc = new AccountHostedService(TEST_SERVER_NAME);
    final EntityId target = id(11L);

    svc.addOperator(target, AccessLevel.PLAYER_LEVEL);
    assertEquals(AccessLevel.PLAYER_LEVEL, svc.getAccessLevel(target));
  }

  @Test
  public void exactPredicates_distinguishExactTier() {
    final AccountHostedService svc = new AccountHostedService(TEST_SERVER_NAME);
    final EntityId zh = id(101L);
    final EntityId smod = id(102L);

    svc.addOperator(zh, AccessLevel.ZH_LEVEL);
    svc.addOperator(smod, AccessLevel.SMOD_LEVEL);

    assertTrue(svc.isZHExact(zh));
    assertFalse("ZH is not exact-Mod", svc.isModeratorExact(zh));
    assertTrue("SMod >= Mod", svc.isModerator(smod));
    assertFalse("SMod is not exact-Mod", svc.isModeratorExact(smod));
    assertTrue(svc.isSmodExact(smod));
  }

  @Test
  public void getAllOfAccessLevel_returnsOnlyMatching() {
    final AccountHostedService svc = new AccountHostedService(TEST_SERVER_NAME);
    final EntityId zh1 = id(1L);
    final EntityId zh2 = id(2L);
    final EntityId mod = id(3L);

    svc.addOperator(zh1, AccessLevel.ZH_LEVEL);
    svc.addOperator(zh2, AccessLevel.ZH_LEVEL);
    svc.addOperator(mod, AccessLevel.MODERATOR_LEVEL);

    final Set<EntityId> zhs = svc.getAllOfAccessLevel(AccessLevel.ZH_LEVEL);
    assertNotNull(zhs);
    assertEquals("two ZHs were registered", 2, zhs.size());
    assertTrue(zhs.contains(zh1));
    assertTrue(zhs.contains(zh2));
    assertFalse("Mod must not appear in the ZH set", zhs.contains(mod));
  }

  @Test
  public void getHostedConnection_unknownId_returnsNull() {
    final AccountHostedService svc = new AccountHostedService(TEST_SERVER_NAME);

    // We don't drive any real connection through the service; the lookup
    // must safely return null rather than throw on miss.
    assertNull(svc.getHostedConnection(id(999L)));
  }

  @Test
  public void clear_preservesBotLevelEntries() {
    final AccountHostedService svc = new AccountHostedService(TEST_SERVER_NAME);
    final EntityId bot = id(50L);
    final EntityId mod = id(51L);

    svc.addOperator(bot, AccessLevel.BOT_LEVEL);
    svc.addOperator(mod, AccessLevel.MODERATOR_LEVEL);

    svc.clear();

    assertEquals(
        "BOT_LEVEL entries survive clear() (Subspace canon: bots persist)",
        AccessLevel.BOT_LEVEL,
        svc.getAccessLevel(bot));
    assertEquals(
        "non-bot entries are wiped to PLAYER_LEVEL default",
        AccessLevel.PLAYER_LEVEL,
        svc.getAccessLevel(mod));
  }
}
