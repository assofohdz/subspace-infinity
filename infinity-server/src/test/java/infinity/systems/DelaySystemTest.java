// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import com.simsilica.sim.SimTime;
import infinity.es.Delay;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

/**
 * Pins {@link Delay} + {@link DelaySystem} to SimTime semantics per ADR-0007.
 *
 * <p>Drives a controlled {@link SimTime} so the test is deterministic — wall-clock
 * would make this test flaky and would defeat the point of the fix.
 */
public class DelaySystemTest {

  /** Marker component used as the deferred payload. */
  public record Payload(int value) implements EntityComponent {
    public Payload() {
      this(0);
    }
  }

  @Test
  public void delay_notElapsed_doesNotApplyPayload() {
    final Fixture f = newFixture();
    try {
      final long start = 1_000_000_000L; // 1 sec sim-time
      final EntityId target = f.ed.createEntity();
      final Set<EntityComponent> payload = new HashSet<>();
      payload.add(new Payload(42));

      // 100 ms duration, starting at t=start.
      f.ed.setComponent(target, Delay.duration(start, 100L, payload, Delay.SET));

      // Tick at +50 ms — deadline at +100 ms not yet reached.
      f.system.update(simTimeAt(start + 50_000_000L));

      assertNull(
          "Payload not yet applied — Delay deadline not reached",
          f.ed.getComponent(target, Payload.class));
      assertNotNull(
          "Delay still present until deadline elapses",
          f.ed.getComponent(target, Delay.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void delay_elapsed_appliesPayloadAndRemovesDelay() {
    final Fixture f = newFixture();
    try {
      final long start = 1_000_000_000L;
      final EntityId target = f.ed.createEntity();
      final Set<EntityComponent> payload = new HashSet<>();
      payload.add(new Payload(7));

      f.ed.setComponent(target, Delay.duration(start, 100L, payload, Delay.SET));

      // Tick at +150 ms — past the +100 ms deadline.
      f.system.update(simTimeAt(start + 150_000_000L));

      final Payload applied = f.ed.getComponent(target, Payload.class);
      assertNotNull("Payload applied after deadline", applied);
      assertEquals(7, applied.value());
      assertNull(
          "Delay removed once payload was applied",
          f.ed.getComponent(target, Delay.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void delay_removeType_removesComponentAtDeadline() {
    final Fixture f = newFixture();
    try {
      final long start = 1_000_000_000L;
      final EntityId target = f.ed.createEntity();
      f.ed.setComponent(target, new Payload(99));

      final Set<EntityComponent> payload = new HashSet<>();
      payload.add(new Payload(99));
      f.ed.setComponent(target, Delay.duration(start, 50L, payload, Delay.REMOVE));

      f.system.update(simTimeAt(start + 100_000_000L));

      assertNull(
          "REMOVE-type Delay drops the target component at deadline",
          f.ed.getComponent(target, Payload.class));
      assertNull(f.ed.getComponent(target, Delay.class));
    } finally {
      f.shutdown();
    }
  }

  @Test
  public void delay_doesNotReadWallClock_simTimeStrictlyControlsDeadline() {
    // The whole point of P0-d / ADR-0007: Delay must not consult the wall clock.
    // We build the Delay at SimTime t=0, then tick the system with SimTime t=500ms.
    // Real wall-clock between the two calls is irrelevant; only sim-time matters.
    final Fixture f = newFixture();
    try {
      final EntityId target = f.ed.createEntity();
      final Set<EntityComponent> payload = new HashSet<>();
      payload.add(new Payload(1));

      // Built at sim-time t=0, deadline at t=100ms.
      f.ed.setComponent(target, Delay.duration(0L, 100L, payload, Delay.SET));

      // Tick at sim-time t=500ms — well past deadline regardless of wall-clock.
      f.system.update(simTimeAt(500_000_000L));

      assertNotNull(
          "Sim-time tick past deadline must apply payload",
          f.ed.getComponent(target, Payload.class));
    } finally {
      f.shutdown();
    }
  }

  /**
   * Builds + initializes a {@link DelaySystem} bound to a fresh
   * {@link DefaultEntityData}. Caller invokes {@link Fixture#shutdown()} once done.
   */
  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    final DelaySystem system = systems.register(DelaySystem.class, new DelaySystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed, system);
  }

  private static final class Fixture {
    private final GameSystemManager systems;
    private final DefaultEntityData ed;
    private final DelaySystem system;

    private Fixture(
        final GameSystemManager systems,
        final DefaultEntityData ed,
        final DelaySystem system) {
      this.systems = systems;
      this.ed = ed;
      this.system = system;
    }

    private void shutdown() {
      systems.stop();
      systems.terminate();
    }
  }

  /** Construct a {@link SimTime} whose {@code getTime()} returns the given nanos. */
  private static SimTime simTimeAt(final long simNanos) {
    final SimTime t = new SimTime();
    t.setCurrentTime(simNanos);
    t.update(0L); // commit the rebase so getTime() returns simNanos
    return t;
  }
}
