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
import infinity.Ship;
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
import infinity.sim.specs.RocketBuffSpec;
import infinity.sim.specs.ShipSpec;
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
 * <p>Per-call inputs flow through {@link ShipSpec} / {@link RocketBuffSpec}
 * parameter records (BACKLOG #2 — too many positional args). Production
 * server code threads {@code engineConfigSystem.get().shipRadius()} into
 * the spec; module / test callers can use {@code
 * EngineConfig.DEFAULTS.shipRadius()}.
 *
 * @see WeaponFactory
 * @see MapFactory
 */
public final class ShipFactory {

  private ShipFactory() {}

  /** Create a basic ship entity (no player-specific components). */
  public static EntityId createShip(final EntityData ed, final ShipSpec spec) {
    final EntityId result = ed.createEntity();

    ed.setComponent(result, new Parent(spec.owner()));
    ed.setComponent(result, new ShipType(Ship.getShip(spec.ship())));

    ed.setComponent(result, ShapeNames.createShip(spec.ship(), ed, spec.radius()));

    SpawnPosition sp = new SpawnPosition(spec.phys().getGrid(), spec.spawnLoc());
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

    ed.setComponent(result, new Meta(spec.createdTime()));
    return result;
  }

  /** Create a ship entity with player-specific components stamped on top of {@link #createShip}. */
  public static EntityId createPlayerShip(final EntityData ed, final ShipSpec spec) {

    final EntityId result = createShip(ed, spec);

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
   */
  public static EntityId createRocketBuff(final EntityData ed, final RocketBuffSpec spec) {
    final EntityId buff = ed.createEntity();
    ed.setComponents(
        buff,
        new RocketBuff(),
        new Parent(spec.ship()),
        new RocketSnapshot(spec.originalThrust(), spec.originalSpeed()),
        new Decay(
            spec.createdTime(),
            spec.createdTime()
                + TimeUnit.NANOSECONDS.convert(spec.activeTimeMs(), TimeUnit.MILLISECONDS)),
        new Meta(spec.createdTime()));
    return buff;
  }
}
