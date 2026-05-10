// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.sim;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.EntityBodyFactory;
import com.simsilica.ext.mphys.Gravity;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeFactory;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.RigidBody;
import infinity.es.ShapeNames;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * This class is a factory for creating entity bodies. It is used by the MPhysSystem to create the
 * bodies for entities that have the SpawnPosition and ShapeInfo components. It is also used by the
 * MPhysDebugState to create the debug shapes for entities that have the ShapeInfo component.
 *
 * @author AFahrenholz
 */
public class InfinityEntityBodyFactory
    extends EntityBodyFactory<MBlockShape> {

  EntityData ed;

  Map<EntityId, RigidBody<EntityId, MBlockShape>> bodies = new HashMap<>();

  public InfinityEntityBodyFactory(
      final EntityData ed,
      final Vec3d defaultGravity,
      final ShapeFactory<MBlockShape> shapeFactory) {
    super(ed, defaultGravity, shapeFactory);
    this.ed = ed;
  }

  // A method to allow other systems to get the body of an entity
  public RigidBody<EntityId, MBlockShape> getBody(EntityId id) {
    return bodies.get(id);
  }

  @Override
  protected RigidBody<EntityId, MBlockShape> createRigidBody(
      final EntityId id,
      final SpawnPosition pos,
      final ShapeInfo info,
      final Mass mass,
      final Gravity gravity) {
    final RigidBody<EntityId, MBlockShape> result =
        super.createRigidBody(id, pos, info, mass, gravity);
    applyShapeSpecificTuning(result, info.getShapeName(ed));
    bodies.put(id, result);
    return result;
  }

  /**
   * Per-shape body tuners. Three behaviour groups:
   * <ul>
   *   <li><b>Projectiles</b> (bullets, bombs, thor, burst): full linear
   *       damping + rotation lock to keep flight straight.
   *   <li><b>Ships</b>: half linear damping (gives the arcade-feel deceleration
   *       Subspace players expect).
   *   <li><b>Static bodies</b> (mines, warps, wormholes, map tiles): no
   *       damping but velocity zeroed at create time so they don't drift on
   *       initial spawn.
   * </ul>
   *
   * <p>Centralising the per-shape lambdas in this map drops the
   * {@code applyShapeSpecificTuning} CC from 26 → 2 (single map lookup +
   * null-check), and adding a new shape now means one map entry, not a new
   * switch case.
   */
  private static final Map<String, Consumer<RigidBody<EntityId, MBlockShape>>> SHAPE_TUNING =
      buildShapeTuningMap();

  private static Map<String, Consumer<RigidBody<EntityId, MBlockShape>>> buildShapeTuningMap() {
    final Consumer<RigidBody<EntityId, MBlockShape>> projectile =
        body -> {
          body.setLinearDamping(1);
          // Make sure our projectiles does not rotate
          body.setRotationalVelocity(new Vec3d(0, 0, 0));
        };
    final Consumer<RigidBody<EntityId, MBlockShape>> ship = body -> body.setLinearDamping(0.5);
    final Consumer<RigidBody<EntityId, MBlockShape>> staticBody =
        body -> {
          body.setLinearDamping(0);
          body.setLinearVelocity(new Vec3d(0, 0, 0));
        };

    final Map<String, Consumer<RigidBody<EntityId, MBlockShape>>> m = new HashMap<>();
    // Projectiles: zero rotation, full damping.
    m.put(ShapeNames.BULLETL1, projectile);
    m.put(ShapeNames.BULLETL2, projectile);
    m.put(ShapeNames.BULLETL3, projectile);
    m.put(ShapeNames.BULLETL4, projectile);
    m.put(ShapeNames.BOMBL1, projectile);
    m.put(ShapeNames.BOMBL2, projectile);
    m.put(ShapeNames.BOMBL3, projectile);
    m.put(ShapeNames.BOMBL4, projectile);
    m.put(ShapeNames.THOR, projectile);
    m.put(ShapeNames.BURST, projectile);
    // Ships: half damping for arcade-feel deceleration.
    m.put(ShapeNames.SHIP_WARBIRD, ship);
    m.put(ShapeNames.SHIP_JAVELIN, ship);
    m.put(ShapeNames.SHIP_SPIDER, ship);
    m.put(ShapeNames.SHIP_LEVI, ship);
    m.put(ShapeNames.SHIP_TERRIER, ship);
    m.put(ShapeNames.SHIP_LANCASTER, ship);
    m.put(ShapeNames.SHIP_WEASEL, ship);
    m.put(ShapeNames.SHIP_SHARK, ship);
    // Static bodies: no damping, zero velocity at spawn.
    m.put(ShapeNames.MINEL1, staticBody);
    m.put(ShapeNames.MINEL2, staticBody);
    m.put(ShapeNames.MINEL3, staticBody);
    m.put(ShapeNames.MINEL4, staticBody);
    m.put(ShapeNames.WARP, staticBody);
    m.put(ShapeNames.WORMHOLE, staticBody);
    m.put(ShapeNames.MAPTILE, staticBody);
    return m;
  }

  /**
   * Per-shape post-create body tweaks (damping, rotation lock, static-body
   * velocity zeroing). Looks up {@code shapeName} in {@link #SHAPE_TUNING};
   * unrecognised shapes no-op (matches the prior switch's {@code default:
   * break} behaviour).
   */
  private static void applyShapeSpecificTuning(
      final RigidBody<EntityId, MBlockShape> result, final String shapeName) {
    final Consumer<RigidBody<EntityId, MBlockShape>> tune = SHAPE_TUNING.get(shapeName);
    if (tune != null) {
      tune.accept(result);
    }
  }
}
