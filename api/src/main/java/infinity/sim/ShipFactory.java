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
import infinity.sim.specs.RocketBuffArgs;
import infinity.sim.specs.ShipArgs;
import java.util.concurrent.TimeUnit;

/** Factory methods for ship + ship-buff entities; structural composition only — tunable per-ship stats projected by {@code ShipSpawnSystem}. @see WeaponFactory @see MapFactory */
public final class ShipFactory {

  private ShipFactory() {}

  /** Create a basic ship entity (no player-specific components). */
  public static EntityId createShip(final EntityData ed, final ShipArgs spec) {
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

    // Tunable per-ship stats are projected by ShipSpawnSystem from ShipConfig.
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
  public static EntityId createPlayerShip(final EntityData ed, final ShipArgs spec) {

    final EntityId result = createShip(ed, spec);

    ed.setComponent(result, new Player());
    ed.setComponent(result, new Name("player"));

    // Seed freq=0 — default team for human players; freq-aware paths (chat
    // `=N`, flag-touch via FrequencySystem) re-stamp via FrequencyChange.
    ed.setComponent(result, new Frequency(0));

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

  /** Buff entity driving a rocket activation; {@link Decay} owns lifetime, {@code RocketBuffSystem} reverts on removal via {@link RocketSnapshot}. */
  public static EntityId createRocketBuff(final EntityData ed, final RocketBuffArgs spec) {
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
