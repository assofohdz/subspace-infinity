// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.systems;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.es.common.Decay;
import com.simsilica.sim.GameSystemManager;
import com.simsilica.sim.SimTime;
import com.simsilica.sim.common.DecaySystem;
import infinity.es.ChangeTarget;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Generic test fixture for the ADR 0001 canonical-writer drain pattern.
 *
 * <p>Pins the four-line state machine an arbitrary canonical writer
 * follows once the migration lands:
 *
 * <ol>
 *   <li><b>Apply-on-add (one-shot, no {@link Decay}).</b> Writer applies
 *       the delta to the target component and <em>destroys the Change
 *       entity itself</em> in the same tick.
 *   <li><b>Apply-on-add + reverse-on-remove (temporary, with
 *       {@link Decay}).</b> Writer applies the delta, leaves the Change
 *       entity alive, the central {@link DecaySystem} destroys it when
 *       the deadline passes, the writer reads the delta off the removed
 *       entity and reverses it on the target component.
 *   <li><b>Multi-source summing.</b> N Change entities targeting the
 *       same target in the same tick collapse to a single applied
 *       fold-sum — not a sequence of N {@code setComponent} calls.
 *   <li><b>No-op skip.</b> If the post-fold value equals the current
 *       target value, the writer does NOT call {@code setComponent} —
 *       observable via an external {@link EntitySet} listener that does
 *       not see a {@code changed} event.
 * </ol>
 *
 * <p>The harness is intentionally synthetic. {@link TestStat} +
 * {@link TestStatChange} + {@link TestStatSystem} are defined inside the
 * test class so the fixture doesn't depend on any specific aspect
 * (Energy, Speed, …) — that lets it land alongside ADR 0001's core
 * ABI, before the first aspect-pilot slice migrates any real ship-state
 * component. Real aspect tests will copy this shape with their own
 * concrete types.
 *
 * <p>Modelled on {@code CapBumpIntentDrainTest} and
 * {@code RocketBuffIntentDrainTest}'s harness shape — minimal
 * {@link GameSystemManager} with only {@link EntityData}, the writer
 * system under test, and (for the Decay case) {@link DecaySystem}. The
 * writer is registered <em>before</em> {@link DecaySystem} so that on
 * the tick a Decay-bound Change entity is reaped, the writer's drain
 * runs first (sees the add), the reaper runs second (destroys the
 * entity), and the writer sees the removal signal on the following
 * tick.
 */
public class CanonicalWriterDrainTest {

  // ---------------------------------------------------------------
  // Scenario 1 — One-shot (no Decay): apply, then writer destroys.
  // ---------------------------------------------------------------

  @Test
  public void oneShotChange_appliesAndDestroysChangeEntity() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    final TestStatSystem writer = registerSystems(systems, ed, /* withDecay = */ false);
    try {
      final EntityId target = ed.createEntity();
      ed.setComponent(target, new TestStat(100));

      final EntityId change = emitChange(ed, target, 25);

      systems.update();

      assertEquals(
          "One-shot delta applied to target",
          125,
          ed.getComponent(target, TestStat.class).value());
      assertNull(
          "Writer destroyed the one-shot Change entity after applying",
          ed.getComponent(change, TestStatChange.class));
      assertNull(
          "Writer destroyed the ChangeTarget along with the holder entity",
          ed.getComponent(change, ChangeTarget.class));
      assertTrue(
          "Writer kept no tracking state for one-shot changes",
          writer.trackedCount() == 0);
    } finally {
      stop(systems);
    }
  }

  // ---------------------------------------------------------------
  // Scenario 2 — Temporary (with Decay): apply on add, reverse on
  // Decay-reaper removal. Two-tick scenario.
  // ---------------------------------------------------------------

  @Test
  public void temporaryChange_appliesOnAddThenReversesWhenDecayReaperFires() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    final TestStatSystem writer = registerSystems(systems, ed, /* withDecay = */ true);
    try {
      final EntityId target = ed.createEntity();
      ed.setComponent(target, new TestStat(100));

      // Decay(0, 0) — already dead on the first tick the reaper sees
      // it. Writer is registered BEFORE DecaySystem, so on tick 1 the
      // writer's drain sees the add (applies + tracks), then the
      // reaper destroys the entity. On tick 2 the writer sees the
      // removal signal and reverses the delta.
      final EntityId change = emitTemporaryChange(ed, target, 25, new Decay(0L, 0L));

      // Tick 1: writer applies (Energy += 25), reaper kills the entity.
      systems.update();
      assertEquals(
          "Tick 1: temporary delta applied",
          125,
          ed.getComponent(target, TestStat.class).value());
      assertNull(
          "Tick 1: DecaySystem reaped the temporary holder",
          ed.getComponent(change, TestStatChange.class));
      assertEquals(
          "Tick 1: writer tracking the live temporary's delta for reversal",
          1,
          writer.trackedCount());

      // Tick 2: writer sees the removed signal and reverses.
      systems.update();
      assertEquals(
          "Tick 2: reversal restored target to pre-buff value",
          100,
          ed.getComponent(target, TestStat.class).value());
      assertEquals(
          "Tick 2: writer cleared tracking after reversal",
          0,
          writer.trackedCount());
    } finally {
      stop(systems);
    }
  }

  // ---------------------------------------------------------------
  // Scenario 3 — Multi-source summing: N Change entities → fold-sum.
  // ---------------------------------------------------------------

  @Test
  public void multipleOneShotChanges_sameTickAccumulateAdditively() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed, /* withDecay = */ false);
    try {
      final EntityId target = ed.createEntity();
      ed.setComponent(target, new TestStat(100));

      emitChange(ed, target, 10);
      emitChange(ed, target, 20);
      emitChange(ed, target, -5);

      systems.update();

      assertEquals(
          "Three same-tick deltas (10 + 20 + -5) summed to +25",
          125,
          ed.getComponent(target, TestStat.class).value());
    } finally {
      stop(systems);
    }
  }

  // ---------------------------------------------------------------
  // Scenario 4 — No-op skip: post-fold == current → no setComponent.
  // ---------------------------------------------------------------

  @Test
  public void noOpDelta_doesNotFireChangedEventOnTarget() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    registerSystems(systems, ed, /* withDecay = */ false);
    try {
      final EntityId target = ed.createEntity();
      ed.setComponent(target, new TestStat(100));

      // External observer EntitySet — same channel a reactor system
      // would use to react to component changes. We drain its initial
      // "added" signal before the no-op tick so any later
      // changed/added entries are attributable to the writer.
      final EntitySet observer = ed.getEntities(TestStat.class);
      observer.applyChanges();
      assertEquals("Observer initialized with target", 1, observer.size());

      // Emit a delta-zero Change — post-fold value equals current.
      final EntityId change = emitChange(ed, target, 0);

      systems.update();
      observer.applyChanges();

      assertEquals(
          "Target value unchanged across the no-op tick",
          100,
          ed.getComponent(target, TestStat.class).value());
      assertTrue(
          "No-op fold did NOT fire a changed event on the target",
          observer.getChangedEntities().isEmpty());
      assertTrue(
          "No-op fold did NOT fire an added event on the target",
          observer.getAddedEntities().isEmpty());
      assertNull(
          "Writer still consumed the no-op Change entity (drain ran)",
          ed.getComponent(change, TestStatChange.class));

      observer.release();
    } finally {
      stop(systems);
    }
  }

  // ---------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------

  /**
   * Register the minimal system set: {@link EntityData}, the writer
   * under test, and (optionally) {@link DecaySystem}. Returns the
   * writer instance so the test can introspect its tracking state for
   * the temporary-Change scenario.
   */
  private static TestStatSystem registerSystems(
      final GameSystemManager systems,
      final DefaultEntityData ed,
      final boolean withDecay) {
    systems.register(EntityData.class, ed);
    final TestStatSystem writer =
        systems.register(TestStatSystem.class, new TestStatSystem());
    if (withDecay) {
      // Order matters: writer is registered BEFORE DecaySystem so the
      // writer drains the add signal on tick 1 BEFORE the reaper
      // destroys the entity; on tick 2 the writer sees the remove.
      systems.register(DecaySystem.class, new DecaySystem());
    }
    systems.initialize();
    systems.start();
    return writer;
  }

  private static void stop(final GameSystemManager systems) {
    systems.stop();
    systems.terminate();
  }

  /** Emit a one-shot Change (no {@link Decay}). */
  private static EntityId emitChange(
      final EntityData ed, final EntityId target, final int delta) {
    final EntityId change = ed.createEntity();
    ed.setComponents(change, new ChangeTarget(target, target), new TestStatChange(delta));
    return change;
  }

  /** Emit a temporary Change carrying a {@link Decay}. */
  private static EntityId emitTemporaryChange(
      final EntityData ed,
      final EntityId target,
      final int delta,
      final Decay decay) {
    final EntityId change = ed.createEntity();
    ed.setComponents(
        change,
        new ChangeTarget(target, target),
        new TestStatChange(delta),
        decay);
    return change;
  }

  // ---------------------------------------------------------------
  // Synthetic stat + change types — local to this test class.
  // ---------------------------------------------------------------

  /**
   * Synthetic Continuous-style component carrying a single
   * {@code int} field. Models any aspect's "current value" for the
   * purposes of exercising the four-line state machine.
   */
  public record TestStat(int value) implements EntityComponent {
    public TestStat() {
      this(0);
    }
  }

  /**
   * Synthetic {@code *Change} payload — additive delta. Pairs with
   * {@link ChangeTarget} on a transient holder entity, drained by
   * {@link TestStatSystem}.
   */
  public record TestStatChange(int delta) implements EntityComponent {
    public TestStatChange() {
      this(0);
    }
  }

  // ---------------------------------------------------------------
  // Synthetic canonical writer — drives the four-line state machine.
  // ---------------------------------------------------------------

  /**
   * Synthetic canonical writer for {@link TestStat}. Models the
   * minimum logic every ADR 0001 canonical writer needs:
   *
   * <ul>
   *   <li>One {@link EntitySet} on
   *       {@code (TestStatChange, ChangeTarget)} — the framework's
   *       component-type narrowing is the dispatch.
   *   <li>Per-tick fold by target — multi-source summing.
   *   <li>{@link Decay}-presence check at apply time to distinguish
   *       one-shot (destroy immediately) from temporary (track for
   *       reversal).
   *   <li>Skip-no-op writes — if post-fold equals current, no
   *       {@code setComponent} call fires.
   *   <li>Remove signal handling — reverse delta only if the entity
   *       was tracked as temporary; one-shots have been destroyed by
   *       this writer itself, so their later remove signal is
   *       discarded.
   * </ul>
   *
   * <p>{@link #trackedCount()} exposes the size of the
   * decay-bound-delta tracking map so tests can pin "writer cleared
   * tracking after reversal."
   */
  public static final class TestStatSystem extends BaseInfinitySystem {

    private EntityData ed;
    private EntitySet changes;
    /**
     * Per-Change-entity record of "what we applied where" — captured
     * at apply-time so we can reverse on Decay-reaper removal without
     * depending on the removed-entity component snapshot. Zay-ES
     * preserves components on a removed entity only for those still
     * present at the moment the entity was purged from the
     * {@link EntitySet}; since {@link com.simsilica.es.base.DefaultEntityData#removeEntity}
     * walks {@code handlers.keySet()} (a {@link java.util.HashMap}
     * with non-deterministic iteration order), either
     * {@link ChangeTarget} or {@link TestStatChange} can be the first
     * removed component — which means {@code removed.get(ChangeTarget.class)}
     * is unreliable in the {@code getRemovedEntities()} branch. Cache
     * the target alongside the delta and avoid the snapshot
     * dependency entirely.
     */
    private final Map<EntityId, TrackedApply> trackedApplied = new HashMap<>();

    /**
     * Pair captured at apply-time for a temporary Change entity: the
     * {@link ChangeTarget#target()} the writer mutated and the
     * delta it added (which must be subtracted on Decay-reaper
     * removal).
     */
    private record TrackedApply(EntityId target, int delta) {}

    @Override
    protected void initialize() {
      this.ed = requireSystem(EntityData.class);
      this.changes = ed.getEntities(TestStatChange.class, ChangeTarget.class);
    }

    @Override
    protected void terminate() {
      if (changes != null) {
        changes.release();
        changes = null;
      }
    }

    public int trackedCount() {
      return trackedApplied.size();
    }

    @Override
    public void update(final SimTime time) {
      changes.applyChanges();

      // Phase 1 — fold added deltas by target, classify holders as
      // one-shot vs temporary based on Decay-presence.
      final Map<EntityId, Integer> deltaByTarget = new HashMap<>();
      final List<EntityId> oneShotHolders = new ArrayList<>();
      for (final Entity added : changes.getAddedEntities()) {
        final ChangeTarget ct = added.get(ChangeTarget.class);
        final int delta = added.get(TestStatChange.class).delta();
        deltaByTarget.merge(ct.target(), delta, Integer::sum);
        final Decay decay = ed.getComponent(added.getId(), Decay.class);
        if (decay == null) {
          oneShotHolders.add(added.getId());
        } else {
          // Cache (target, delta) under the holder's id so we can
          // reverse on Decay-reaper removal without depending on
          // the removed-entity component snapshot — see
          // TrackedApply Javadoc.
          trackedApplied.put(added.getId(), new TrackedApply(ct.target(), delta));
        }
      }

      // Phase 2 — apply fold-sum per target, skip-no-op writes.
      for (final Map.Entry<EntityId, Integer> e : deltaByTarget.entrySet()) {
        applyDelta(e.getKey(), e.getValue());
      }

      // Phase 3 — destroy one-shot holders.
      for (final EntityId id : oneShotHolders) {
        ed.removeEntity(id);
      }

      // Phase 4 — reverse delta on Decay-reaper removals.
      //
      // We do NOT read the ChangeTarget off the removed Entity —
      // ed.removeEntity walks handlers.keySet() (HashMap, no fixed
      // order), so by the time the EntitySet's purge fires either
      // component may already be null in the snapshot. The
      // trackedApplied cache captured target + delta at apply-time
      // precisely to avoid this fragility.
      for (final Entity removed : changes.getRemovedEntities()) {
        final TrackedApply applied = trackedApplied.remove(removed.getId());
        if (applied == null) {
          // Writer destroyed it itself (one-shot); the remove signal
          // is the trailing echo. Nothing to do.
          continue;
        }
        applyDelta(applied.target(), -applied.delta());
      }
    }

    private void applyDelta(final EntityId target, final int delta) {
      final TestStat current = ed.getComponent(target, TestStat.class);
      if (current == null) {
        // Target has no TestStat — nothing to apply against.
        return;
      }
      final int next = current.value() + delta;
      // Rule #6 (RaM): skip no-op writes so reactors don't see a
      // spurious changed event.
      if (next == current.value()) {
        return;
      }
      ed.setComponent(target, new TestStat(next));
    }
  }
}
