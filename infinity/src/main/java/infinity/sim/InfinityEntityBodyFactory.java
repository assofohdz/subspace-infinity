/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

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

  HashMap<EntityId, RigidBody<EntityId, MBlockShape>> bodies = new HashMap<>();

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

    // Do whatever we want to the body depending on the ShapeInfo
    switch (info.getShapeName(ed)) {
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
    bodies.put(id, result);
    return result;
  }
}
