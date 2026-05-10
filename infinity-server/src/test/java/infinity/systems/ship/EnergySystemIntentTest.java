// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz
package infinity.systems.ship;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.base.DefaultEntityData;
import com.simsilica.sim.GameSystemManager;
import infinity.es.Buff;
import infinity.es.Dead;
import infinity.es.HealthChange;
import infinity.es.ship.Energy;
import infinity.es.ship.Health;
import org.junit.Test;

/**
 * RaM intent-drain pillar of the spawn-projection test harness — boots a
 * minimal {@link GameSystemManager} with {@link EntityData} +
 * {@link EnergySystem} and exercises the canonical
 * "intent-emission → drain → final state" round-trip that the
 * Replacement-as-Mutation rule formalizes (see
 * {@code .claude/rules/replacement-as-mutation.md}).
 *
 * <p>Companion to {@code SpawnerProjectionTest} (factory pillar) and
 * {@code ShipSpawnSystemTest} (spawn-projection pillar). Whereas those test
 * "config template → projection → component," this test pins the
 * orthogonal flow "intent component → canonical-writer drain → folded
 * component value" — the seam every RaM-migrated system relies on.
 *
 * <p>Concretely: each test creates a synthetic ship entity with a live
 * {@link Health} pool, emits one or more {@code (Buff, HealthChange)}
 * intent entities (the existing canonical shape that
 * {@code WeaponsDamageLogic.applySplashDamage} already routes through —
 * see {@code EnergySystem.damage}), advances one tick, then asserts:
 *
 * <ol>
 *   <li><b>Final {@link Health} value</b> equals previous-tick value plus
 *       the folded sum of intent deltas (clamped at the {@link Energy}
 *       cap when present).
 *   <li><b>Intent entities are reaped</b> by the canonical writer — i.e.
 *       removed from {@link EntityData} so {@code getComponent} returns
 *       {@code null}.
 *   <li><b>Death stamping</b> happens iff the folded delta drives Health
 *       to zero.
 * </ol>
 *
 * <p>This is the harness {@code WeaponsFireSystem}/{@code WeaponsImpactSystem}
 * splits will assert against: the RaM contract is "an intent emitter must
 * never write the canonical type directly; the writer system folds and
 * emits one replacement per affected entity per tick."
 */
public class EnergySystemIntentTest {

  // ──────────────────────────────────────────────────────────────────
  // Fixture helpers — keep tests focused on the assertion, not setup.
  // ──────────────────────────────────────────────────────────────────

  /**
   * Stand up a {@link GameSystemManager} with the minimum systems needed to
   * exercise the {@link EnergySystem} drain seam. Mirrors the
   * "register(Class, instance)" idiom the production {@code GameServer}
   * uses (rather than {@code addSystem}) so {@code AbstractGameSystem.getSystem}
   * lookups resolve at initialize time.
   */
  private static Fixture newFixture() {
    final GameSystemManager systems = new GameSystemManager();
    final DefaultEntityData ed = new DefaultEntityData();
    systems.register(EntityData.class, ed);
    systems.register(EnergySystem.class, new EnergySystem());
    systems.initialize();
    systems.start();
    return new Fixture(systems, ed);
  }

  /**
   * Create a ship entity with a live {@link Health} pool and an effective
   * {@link Energy} cap. No {@code Player} marker, no {@code BodyPosition}
   * — we want the death branch to short-circuit before it reaches the
   * (unregistered) {@code PrizeSystem} so the harness stays minimal.
   */
  private static EntityId newShip(final EntityData ed, final int health, final int cap) {
    final EntityId ship = ed.createEntity();
    ed.setComponent(ship, new Health(health));
    ed.setComponent(ship, new Energy(cap));
    return ship;
  }

  /**
   * Emit a {@code (Buff, HealthChange)} intent — the canonical pre-RaM
   * shape that {@code EnergySystem.damage} produces. Mirrors what the new
   * {@code WeaponsImpactSystem} (slice 1 of the RaM PRD) will emit per
   * victim. {@code startTime=0} means "apply on next tick" (any tick where
   * sim time has advanced past zero, which is every {@code update()} call
   * after start).
   */
  private static EntityId emitDamageIntent(
      final EntityData ed, final EntityId target, final int delta) {
    final EntityId intent = ed.createEntity();
    ed.setComponents(intent, new Buff(target, 0L), new HealthChange(delta));
    return intent;
  }

  // ──────────────────────────────────────────────────────────────────
  // Tests — the contract weapons-split will rely on.
  // ──────────────────────────────────────────────────────────────────

  /**
   * RaM rule 2 (intent emitters never replace) + rule 4 (replacements
   * flush at the tick boundary): emitting an intent on its own does
   * <i>not</i> mutate the target component. The drain happens at
   * {@code GameSystemManager.update()}, not at intent-creation time.
   */
  @Test
  public void emission_alone_doesNotMutateHealthBeforeTick() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, /* health */ 1000, /* cap */ 1000);
      emitDamageIntent(f.ed, ship, -50);

      // No tick yet — Health must be unchanged. This pins the rule that
      // "emission != mutation" — only the canonical writer's drain mutates.
      assertEquals(
          "Health must NOT change at intent-emit time (RaM rule 2)",
          1000,
          f.ed.getComponent(ship, Health.class).getHealth());
    } finally {
      f.shutdown();
    }
  }

  /**
   * Single intent → single replacement. The base-case round-trip every
   * weapons-split unit test will reuse: emit damage of N → tick → Health
   * dropped by N, intent entity reaped.
   */
  @Test
  public void singleIntent_reducesHealthByDelta_andReapsIntent() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, /* health */ 1000, /* cap */ 1000);
      final EntityId intent = emitDamageIntent(f.ed, ship, -30);

      f.systems.update();

      assertEquals(
          "Health = previous - delta after canonical-writer drain",
          970,
          f.ed.getComponent(ship, Health.class).getHealth());
      assertNull(
          "Intent entity must be reaped after drain (no leak)",
          f.ed.getComponent(intent, HealthChange.class));
      assertNull(
          "Intent's Buff must also be reaped",
          f.ed.getComponent(intent, Buff.class));
    } finally {
      f.shutdown();
    }
  }

  /**
   * RaM rule 3 (writers fold previous-tick value + intents): three damage
   * intents on the same target in the same tick collapse to one Health
   * write equal to {@code previous + sum(deltas)}. Same-tick collisions
   * are the central RaM determinism property.
   */
  @Test
  public void multipleIntents_sameTarget_sameTick_foldToSumOfDeltas() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, /* health */ 1000, /* cap */ 1000);
      final EntityId i1 = emitDamageIntent(f.ed, ship, -10);
      final EntityId i2 = emitDamageIntent(f.ed, ship, -25);
      final EntityId i3 = emitDamageIntent(f.ed, ship, -5);

      f.systems.update();

      // Three intents -> one folded replacement: 1000 - (10+25+5) = 960.
      assertEquals(
          "Folded sum of deltas applied as one replacement",
          960,
          f.ed.getComponent(ship, Health.class).getHealth());

      // All three intent entities must be reaped (no leak under multi-emit).
      assertNull("intent 1 reaped", f.ed.getComponent(i1, HealthChange.class));
      assertNull("intent 2 reaped", f.ed.getComponent(i2, HealthChange.class));
      assertNull("intent 3 reaped", f.ed.getComponent(i3, HealthChange.class));
    } finally {
      f.shutdown();
    }
  }

  /**
   * RaM rule 7 (stable ordering) — but more importantly, intents on
   * different targets must <i>not</i> cross-talk. Two ships, one intent
   * each, single tick → each ship's Health drops by its own delta.
   * weapons-split's splash path applies one intent per victim; this is
   * the test that pins the per-target isolation.
   */
  @Test
  public void multipleIntents_differentTargets_foldIndependently() {
    final Fixture f = newFixture();
    try {
      final EntityId shipA = newShip(f.ed, /* health */ 1000, /* cap */ 1000);
      final EntityId shipB = newShip(f.ed, /* health */ 500, /* cap */ 800);
      emitDamageIntent(f.ed, shipA, -100);
      emitDamageIntent(f.ed, shipB, -75);

      f.systems.update();

      assertEquals(
          "ship A folds only its own intent",
          900,
          f.ed.getComponent(shipA, Health.class).getHealth());
      assertEquals(
          "ship B folds only its own intent",
          425,
          f.ed.getComponent(shipB, Health.class).getHealth());
    } finally {
      f.shutdown();
    }
  }

  /**
   * Cap clamp on positive deltas — emitting a HealthChange that would
   * overshoot {@link Energy} clamps Health at the cap (not the
   * pre-clamp arithmetic sum). Mirrors the QUICKCHARGE prize / regen
   * intent flow in the RaM PRD slice 3 backlog.
   */
  @Test
  public void positiveIntent_clampsAtEnergyCap() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, /* health */ 900, /* cap */ 1000);
      emitDamageIntent(f.ed, ship, +500);

      f.systems.update();

      assertEquals(
          "Positive delta clamps at the Energy cap (not 1400)",
          1000,
          f.ed.getComponent(ship, Health.class).getHealth());
    } finally {
      f.shutdown();
    }
  }

  /**
   * Death branch — folded delta that drives Health to ≤ 0 stamps a
   * {@link Dead} component. weapons-split's WeaponsImpactSystem will rely
   * on this for the kill-feed reactor flow (rule 5 — reactors run after
   * flush and observe {@code Dead}).
   *
   * <p>No {@code Player} marker is set on the ship, so the death branch
   * short-circuits before the (unregistered) {@code PrizeSystem} lookup.
   */
  @Test
  public void intentDrivingHealthToZero_stampsDead() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, /* health */ 100, /* cap */ 1000);
      emitDamageIntent(f.ed, ship, -150);

      f.systems.update();

      assertEquals(
          "Health goes negative when no floor (uncapped negatives — Dead is the gate)",
          -50,
          f.ed.getComponent(ship, Health.class).getHealth());
      assertNotNull(
          "Dead component stamped when Health ≤ 0",
          f.ed.getComponent(ship, Dead.class));
    } finally {
      f.shutdown();
    }
  }

  /**
   * Idempotent death — a second damaging tick on an already-dead ship
   * does not re-stamp {@link Dead} (which would update the timestamp).
   * The pre-existing guard in {@code EnergySystem.handleDeath} relies on
   * "once dead, the marker stays put"; weapons-split must trust this so
   * a multi-hit kill-shot doesn't churn the {@code Dead} component.
   */
  @Test
  public void deadShip_doesNotReStampDeadOnFurtherDamage() {
    final Fixture f = newFixture();
    try {
      final EntityId ship = newShip(f.ed, /* health */ 100, /* cap */ 1000);
      emitDamageIntent(f.ed, ship, -150);
      f.systems.update();

      final long firstDeath = f.ed.getComponent(ship, Dead.class).getTime();

      // Tick 2: more damage on the corpse.
      emitDamageIntent(f.ed, ship, -50);
      f.systems.update();

      assertEquals(
          "Dead.time unchanged — guard prevents re-stamping",
          firstDeath,
          f.ed.getComponent(ship, Dead.class).getTime());
    } finally {
      f.shutdown();
    }
  }

  /**
   * Drain robustness — an intent for a target with no live {@link Health}
   * pool still drains the intent entity (no leak). This pins that an
   * over-eager weapons-split emitter (e.g. firing at a despawned ship)
   * doesn't leak intent entities, even if the projection is a no-op.
   *
   * <p>weapons-split note — when {@code DamageSource} (currently in
   * weapons-split's slice-1 surface) lands as a sibling on the intent,
   * it should be reaped alongside the canonical {@code (Buff, HealthChange)}
   * pair by the same {@code ed.removeEntity} call. Add a
   * {@code intentWithDamageSourceSibling_reapsTogether} test in this file
   * once {@code infinity.es.DamageSource} is on disk; the fixture
   * helpers ({@link #newFixture()}, {@link #newShip(EntityData, int, int)})
   * are reusable.
   */
  @Test
  public void intentForMissingTarget_isStillReaped() {
    final Fixture f = newFixture();
    try {
      final EntityId orphan = f.ed.createEntity();   // no Health component
      final EntityId intent = emitDamageIntent(f.ed, orphan, -25);

      f.systems.update();

      assertNull(
          "Intent reaped even when target has no Health pool",
          f.ed.getComponent(intent, HealthChange.class));
    } finally {
      f.shutdown();
    }
  }

  // ──────────────────────────────────────────────────────────────────
  // Tiny holder so the tests can shut down the manager from finally.
  // ──────────────────────────────────────────────────────────────────

  private static final class Fixture {
    private final GameSystemManager systems;
    private final DefaultEntityData ed;

    private Fixture(final GameSystemManager systems, final DefaultEntityData ed) {
      this.systems = systems;
      this.ed = ed;
    }

    private void shutdown() {
      systems.stop();
      systems.terminate();
    }
  }
}
