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
   * Per-shape post-create body tweaks (damping, rotation lock, static-body
   * velocity zeroing). Extracted from {@link #createRigidBody} so the
   * dispatcher itself stays readable; callers should not need to invoke this
   * directly.
   */
  private static void applyShapeSpecificTuning(
      final RigidBody<EntityId, MBlockShape> result, final String shapeName) {
    switch (shapeName) {
      // Remove dampening from projectiles
      case ShapeNames.BULLETL1:
      case ShapeNames.BULLETL2:
      case ShapeNames.BULLETL3:
      case ShapeNames.BULLETL4:
      case ShapeNames.BOMBL1:
      case ShapeNames.BOMBL2:
      case ShapeNames.BOMBL3:
      case ShapeNames.BOMBL4:
      case ShapeNames.THOR:
      case ShapeNames.BURST:
        result.setLinearDamping(1);
        // Make sure our projectiles does not rotate
        result.setRotationalVelocity(new Vec3d(0, 0, 0));
        break;
      case ShapeNames.SHIP_WARBIRD:
      case ShapeNames.SHIP_JAVELIN:
      case ShapeNames.SHIP_SPIDER:
      case ShapeNames.SHIP_LEVI:
      case ShapeNames.SHIP_TERRIER:
      case ShapeNames.SHIP_LANCASTER:
      case ShapeNames.SHIP_WEASEL:
      case ShapeNames.SHIP_SHARK:
        result.setLinearDamping(0.5);
        break;
        // Static bodies:
      case ShapeNames.MINEL1:
      case ShapeNames.MINEL2:
      case ShapeNames.MINEL3:
      case ShapeNames.MINEL4:
      case ShapeNames.WARP:
      case ShapeNames.WORMHOLE:
      case ShapeNames.MAPTILE:
        result.setLinearDamping(0);
        result.setLinearVelocity(new Vec3d(0, 0, 0));
        break;
      default:
        break;
    }
  }
}
