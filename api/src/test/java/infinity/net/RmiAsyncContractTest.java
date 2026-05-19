// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.net;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.jme3.network.service.rmi.Asynchronous;
import infinity.net.chat.ChatSession;
import infinity.net.chat.ChatSessionListener;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

/**
 * RMI/network glue smoke test for the {@code infinity.net.*} session and
 * listener contracts. Seed coverage per Tier-2 finding #4.
 *
 * <p>jME's RMI layer dispatches each invocation either synchronously
 * (caller blocks until the remote returns) or asynchronously (fire-and-
 * forget) based on whether the interface method is annotated
 * {@link Asynchronous}. Player-input commands ({@code setView},
 * {@code setMovementInput}, {@code attack} …) and listener callbacks
 * ({@code notifyLoginStatus}, {@code setPlayer} …) MUST be async — a
 * synchronous client→server input call would block the render thread
 * for one full RTT per frame, and a synchronous server→client callback
 * would let one slow client stall the network thread for everyone.
 *
 * <p>This test pins those annotations so a stray edit (e.g. dropping
 * {@code @Asynchronous} on a refactor) is caught before it ships. We
 * deliberately don't enumerate every method by name — instead each
 * interface declares which methods are intentionally synchronous, and
 * everything else must be async.
 */
public class RmiAsyncContractTest {

  /** Methods that are intentionally synchronous (return data, called rarely). */
  private static final Set<String> SYNC_ALLOWLIST_ACCOUNT_SESSION =
      new HashSet<>(Arrays.asList("getServerInfo"));

  private static final Set<String> SYNC_ALLOWLIST_GAME_SESSION =
      new HashSet<>(Arrays.asList("getPlayer", "getPlayerLocation"));

  private static final Set<String> SYNC_ALLOWLIST_CHAT_SESSION =
      new HashSet<>(Arrays.asList("getPlayerNames"));

  /** Listener interfaces are pure callbacks — every method must be async. */
  private static final Set<String> SYNC_ALLOWLIST_EMPTY = new HashSet<>();

  @Test
  public void accountSession_inputMethodsAreAsync() {
    assertAllNonAllowlistedMethodsAreAsync(AccountSession.class, SYNC_ALLOWLIST_ACCOUNT_SESSION);
  }

  @Test
  public void accountSessionListener_callbacksAreAsync() {
    assertAllNonAllowlistedMethodsAreAsync(AccountSessionListener.class, SYNC_ALLOWLIST_EMPTY);
  }

  @Test
  public void gameSession_inputMethodsAreAsync() {
    assertAllNonAllowlistedMethodsAreAsync(GameSession.class, SYNC_ALLOWLIST_GAME_SESSION);
  }

  @Test
  public void gameSessionListener_callbacksAreAsync() {
    assertAllNonAllowlistedMethodsAreAsync(GameSessionListener.class, SYNC_ALLOWLIST_EMPTY);
  }

  @Test
  public void chatSession_inputMethodsAreAsync() {
    assertAllNonAllowlistedMethodsAreAsync(ChatSession.class, SYNC_ALLOWLIST_CHAT_SESSION);
  }

  @Test
  public void chatSessionListener_callbacksAreAsync() {
    assertAllNonAllowlistedMethodsAreAsync(ChatSessionListener.class, SYNC_ALLOWLIST_EMPTY);
  }

  @Test
  public void hotPathInputMethodsAreUnreliable() {
    // Per-frame movement input is the highest-volume RMI traffic in the
    // game. Subspace canon and the existing comments in GameSession
    // (`reliable = false` on setView/setMovementInput/move) require these
    // to be unreliable — losing one update is harmless because the next
    // frame replaces it; reliable resends would queue stale input behind
    // current input under packet loss.
    assertAsyncReliable(GameSession.class, "setView", false);
    assertAsyncReliable(GameSession.class, "move", false);
  }

  @Test
  public void rareCommandMethodsAreReliable() {
    // Action / attack / avatar / toggle / map are user-initiated commands
    // sent at human cadence. Dropping one would silently lose a brick
    // placement or weapon swap, which the player would notice. Stay
    // reliable.
    assertAsyncReliable(GameSession.class, "action", true);
    assertAsyncReliable(GameSession.class, "attack", true);
    assertAsyncReliable(GameSession.class, "avatar", true);
    assertAsyncReliable(GameSession.class, "toggle", true);
  }

  // -------------------------------------------------------------------------

  private static void assertAllNonAllowlistedMethodsAreAsync(
      final Class<?> iface, final Set<String> syncAllowlist) {
    final List<String> violations = new ArrayList<>();
    for (final Method m : iface.getDeclaredMethods()) {
      if (m.isSynthetic() || m.isBridge()) {
        continue;
      }
      final boolean async = m.isAnnotationPresent(Asynchronous.class);
      final boolean allowedSync = syncAllowlist.contains(m.getName());
      if (!async && !allowedSync) {
        violations.add(m.getName());
      }
      if (async && allowedSync) {
        violations.add(
            m.getName() + " is on the sync allowlist but carries @Asynchronous — pick one");
      }
    }
    if (!violations.isEmpty()) {
      fail(
          iface.getSimpleName()
              + " methods missing @Asynchronous (or in conflict with sync allowlist): "
              + violations);
    }
  }

  private static void assertAsyncReliable(
      final Class<?> iface, final String methodName, final boolean expectReliable) {
    Method found = null;
    for (final Method m : iface.getDeclaredMethods()) {
      if (m.getName().equals(methodName)) {
        found = m;
        break;
      }
    }
    assertNotNull(iface.getSimpleName() + "." + methodName + " must exist", found);

    final Asynchronous async = found.getAnnotation(Asynchronous.class);
    assertNotNull(
        iface.getSimpleName() + "." + methodName + " must carry @Asynchronous", async);
    assertTrue(
        iface.getSimpleName()
            + "."
            + methodName
            + " expected reliable="
            + expectReliable
            + " but was reliable="
            + async.reliable(),
        async.reliable() == expectReliable);
  }
}
