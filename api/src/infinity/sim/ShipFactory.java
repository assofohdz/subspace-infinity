// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

import com.jme3.math.ColorRGBA;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.Name;
import com.simsilica.es.common.Decay;
import com.simsilica.ext.mphys.Gravity;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.PhysicsSpace;
import infinity.Ship;
import infinity.config.EngineConfig;
import infinity.es.CollisionCategory;
import infinity.es.Frequency;
import infinity.es.Gold;
import infinity.es.Meta;
import infinity.es.Parent;
import infinity.es.PointLightComponent;
import infinity.es.ShapeNames;
import infinity.es.input.MovementInput;
import infinity.es.ship.CollidesWithLargeStatics;
import infinity.es.ship.Player;
import infinity.es.ship.ShipType;
import infinity.es.ship.actions.RocketBuff;
import infinity.es.ship.actions.RocketSnapshot;
import java.util.concurrent.TimeUnit;

/**
 * Factory methods for ship and ship-buff entities. Carved out of the legacy
 * {@code GameEntities} grab-bag (arch-review tier 3 finding #9) so module
 * authors and server systems can find ship-construction helpers under a
 * domain-named class instead of scrolling 35 mixed factory methods.
 *
 * <p>This factory composes structural pieces only (Parent, ShipType,
 * ShapeNames, SpawnPosition, Mass, Gravity, Gold, CollisionCategory,
 * CollidesWithLargeStatics, Meta). All tunable per-ship stats
 * (Energy/Health/Recharge/Thrust/Speed/Rotation movement triples,
 * drag/turn/bounce feel, radar range, weapon + inventory groups) are
 * projected by {@code ShipSpawnSystem} from the per-arena
 * {@code ShipConfig} per Pattern 4 (see
 * {@code .claude/rules/config-pattern.md}).
 *
 * <p>The "backward-compat overload" idiom — primary method takes new param,
 * no-arg form forwards via {@link EngineConfig#DEFAULTS} — is preserved at
 * the per-method level. Module callers use the no-arg form; production
 * server code threads {@code engineConfigSystem.get().*Radius()} through
 * the explicit-radius overload.
 *
 * @see WeaponFactory
 * @see MapFactory
 */
public final class ShipFactory {

  private ShipFactory() {}

  /**
   * Backward-compat overload — uses {@link EngineConfig#DEFAULTS} radius.
   * Module callers (and {@code AIEntities.createMobShip}) use this form;
   * production server code threads {@code engineConfigSystem.get().shipRadius()}
   * through the explicit-radius overload below.
   */
  public static EntityId createShip(
      final Vec3d spawnLoc,
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final byte ship) {
    return createShip(
        spawnLoc, ed, owner, phys, createdTime, ship, EngineConfig.DEFAULTS.shipRadius());
  }

  /** Ship with explicit collision radius — see backward-compat overload above. */
  public static EntityId createShip(
      final Vec3d spawnLoc,
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final byte ship,
      final double radius) {
    final EntityId result = ed.createEntity();

    ed.setComponent(result, new Parent(owner));
    ed.setComponent(result, new ShipType(Ship.getShip(ship)));

    ed.setComponent(result, ShapeNames.createShip(ship, ed, radius));

    SpawnPosition sp = new SpawnPosition(phys.getGrid(), spawnLoc);
    ed.setComponent(result, sp);

    Mass m = new Mass(1);
    ed.setComponent(result, m);

    Gravity g = Gravity.ZERO;
    ed.setComponent(result, g);

    ed.setComponent(result, new Gold(0));

    // All tunable per-ship stats (Energy/Health/Recharge/Thrust/Speed/Rotation
    // movement triples, drag/turn/bounce feel, radar range, and the
    // bomb/bullet/mine/burst/thor/repel weapon + inventory groups) are projected
    // by ShipSpawnSystem from the per-arena ShipConfig — see Pattern 4 in
    // .claude/rules/config-pattern.md and CONTEXT.md. createShip composes the
    // structural pieces only (Parent, ShipType, ShapeNames, SpawnPosition,
    // Mass, Gravity, Gold, CollisionCategory, CollidesWithLargeStatics, Meta);
    // the spawn system writes the tunable components on the next tick when
    // the ship enters its arena.

    ed.setComponent(
        result, new CollisionCategory(CollisionFilters.FILTER_CATEGORY_DYNAMIC_PLAYERS));

    // Opt in to the coarse large-static contact pass — ships are the only dynamic
    // bodies whose pairs ArenaMembershipSystem actually cares about. Other
    // dynamics (projectiles, sensor probes) deliberately stay opted out.
    ed.setComponent(result, new CollidesWithLargeStatics());

    ed.setComponent(result, new Meta(createdTime));
    return result;
  }

  /**
   * Backward-compat overload — uses {@link EngineConfig#DEFAULTS} radius.
   * Module callers use this form; production server code threads
   * {@code engineConfigSystem.get().shipRadius()} through the explicit-radius
   * overload below.
   */
  public static EntityId createPlayerShip(
      final Vec3d spawnLoc,
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final byte ship) {
    return createPlayerShip(
        spawnLoc, ed, owner, phys, createdTime, ship, EngineConfig.DEFAULTS.shipRadius());
  }

  /** Player ship with explicit collision radius — see backward-compat overload above. */
  public static EntityId createPlayerShip(
      final Vec3d spawnLoc,
      final EntityData ed,
      final EntityId owner,
      final PhysicsSpace<?, ?> phys,
      final long createdTime,
      final byte ship,
      final double radius) {

    final EntityId result = createShip(spawnLoc, ed, owner, phys, createdTime, ship, radius);

    ed.setComponent(result, new Player());
    ed.setComponent(result, new Name("player"));

    ed.setComponent(result, new Frequency(1));

    ed.setComponent(
        result,
        new PointLightComponent(
            new ColorRGBA(3.5f, 3.5f, 3.5f, 1.0f),
            CoreViewConstants.SHIPLIGHTRADIUS,
            CoreViewConstants.SHIPLIGHTOFFSET));

    byte flags = 0x0;
    ed.setComponent(result, new MovementInput(new Vec3d(), new Quatd(), flags));

    return result;
  }

  /**
   * Compose the buff entity that drives a rocket activation. Lifecycle is
   * owned by {@link Decay}: when the deadline passes, the canonical decay
   * reaper deletes this entity, and {@code RocketBuffSystem} reacts to
   * the removal by reverting the parent ship's {@code Thrust} / {@code Speed}
   * from the {@link RocketSnapshot} carried here.
   *
   * @param ship parent ship being buffed (revert target)
   * @param createdTime spawn time in ns (matches {@link com.simsilica.sim.SimTime#getTime})
   * @param activeTimeMs buff lifetime in ms
   * @param originalThrust ship's {@code Thrust} value before the buff
   *     (snapshotted by the caller; restored on buff expiry)
   * @param originalSpeed ship's {@code Speed} value before the buff
   */
  public static EntityId createRocketBuff(
      final EntityData ed,
      final EntityId ship,
      final long createdTime,
      final long activeTimeMs,
      final int originalThrust,
      final int originalSpeed) {
    final EntityId buff = ed.createEntity();
    ed.setComponents(
        buff,
        new RocketBuff(),
        new Parent(ship),
        new RocketSnapshot(originalThrust, originalSpeed),
        new Decay(
            createdTime,
            createdTime + TimeUnit.NANOSECONDS.convert(activeTimeMs, TimeUnit.MILLISECONDS)),
        new Meta(createdTime));
    return buff;
  }
}
