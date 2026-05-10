// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.client.states;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.simsilica.ethereal.TimeSource;
import infinity.sim.TimeManager;
import org.junit.Test;

/**
 * Lifecycle smoke test for {@link TimeState} — the {@code BaseAppState} that
 * latches a per-frame timestamp from a {@link TimeSource} so visuals don't
 * drift on intra-frame jitter.
 *
 * <p>Seed coverage for {@code infinity.client.*} per Tier-2 finding #4. The
 * jME {@code BaseAppState} lifecycle ({@code initialize} → {@code update}
 * → {@code cleanup}) is invoked directly via the public/protected API rather
 * than through {@code AppStateManager}, mirroring the synthetic-fixture
 * style of {@code SpawnerProjectionTest}: no real {@code Application},
 * no render loop. {@code TimeState}'s {@code initialize}, {@code cleanup},
 * {@code onEnable}, and {@code onDisable} are no-ops, so passing
 * {@code null} for the {@code AppStateManager} / {@code Application} is
 * safe — the protected hooks never dereference them.
 */
public class TimeStateTest {

  /** Manual {@link TimeSource} so the test owns the clock. */
  private static final class FixedTimeSource implements TimeSource {
    private long nanos;

    FixedTimeSource(final long initialNanos) {
      this.nanos = initialNanos;
    }

    void set(final long nanos) {
      this.nanos = nanos;
    }

    @Override
    public long getTime() {
      return nanos;
    }
  }

  @Test
  public void update_latchesTimeSourceValueIntoFrameTime() {
    final FixedTimeSource clock = new FixedTimeSource(1_000L);
    final TimeState state = new TimeState(clock);

    // Pre-update: frameTime starts at 0 because update() hasn't latched yet.
    assertEquals("frameTime starts at 0 before first update", 0L, state.getTime());

    state.update(0.016f);
    assertEquals("update() latches timeSource.getTime() into frameTime", 1_000L, state.getTime());

    clock.set(2_500L);
    // Without a second update, the latched frameTime must remain stable —
    // that's the whole point of the latching behaviour.
    assertEquals("frameTime stays stable until next update", 1_000L, state.getTime());

    state.update(0.016f);
    assertEquals("next update() picks up the new clock value", 2_500L, state.getTime());
  }

  @Test
  public void update_recordsRealTime() {
    final FixedTimeSource clock = new FixedTimeSource(0L);
    final TimeState state = new TimeState(clock);

    final long before = System.nanoTime();
    state.update(0.016f);
    final long after = System.nanoTime();

    final long real = state.getRealTime();
    assertTrue("realTime must fall inside the [before, after] window",
        real >= before && real <= after);
  }

  @Test
  public void update_withoutTimeSource_isNoOp() {
    // No-arg ctor leaves timeSource null. update() must guard against that
    // and not throw — TimeState gets attached early in the boot sequence.
    final TimeState state = new TimeState();
    state.update(0.016f);
    assertEquals("frameTime stays 0 when timeSource is null", 0L, state.getTime());
  }

  @Test
  public void setTimeSource_swapsClock() {
    final FixedTimeSource first = new FixedTimeSource(100L);
    final TimeState state = new TimeState(first);

    state.update(0.016f);
    assertEquals(100L, state.getTime());

    final FixedTimeSource second = new FixedTimeSource(500L);
    state.setTimeSource(second);
    assertSame("getTimeSource exposes the live reference", second, state.getTimeSource());

    state.update(0.016f);
    assertEquals("frameTime now reflects the swapped source", 500L, state.getTime());
  }

  @Test
  public void implementsTimeManagerInterface() {
    // TimeManager is the api-side seam used by client systems that need a
    // tick clock without dragging in jME's BaseAppState. Guard against an
    // accidental rename / removal of the implements clause.
    final TimeState state = new TimeState();
    assertNotNull("TimeState must implement TimeManager", (TimeManager) state);
  }

  @Test
  public void lifecycle_initializeAndCleanup_runWithoutApplication() {
    // Drives the public BaseAppState lifecycle (initialize → cleanup). We
    // pass null for the AppStateManager + Application because TimeState's
    // protected hooks are no-ops; the framework calls cleanup() with the
    // app reference it captured during initialize, which is also null here.
    // This proves the AppState can attach + detach without a real jME
    // Application — the fundamental "smoke test" claim.
    final TimeState state = new TimeState(new FixedTimeSource(0L));
    state.initialize(null, null);
    assertTrue("isInitialized() must be true after initialize()", state.isInitialized());

    state.update(0.016f);

    state.cleanup();
    assertFalse("isInitialized() must be false after cleanup()", state.isInitialized());
  }
}
