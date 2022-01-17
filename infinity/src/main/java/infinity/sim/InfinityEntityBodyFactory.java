/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
import com.simsilica.mphys.StaticBody;

import infinity.es.ShapeNames;

/**
 *
 * @author AFahrenholz
 */
public class InfinityEntityBodyFactory extends EntityBodyFactory<MBlockShape> {

    EntityData ed;

    public InfinityEntityBodyFactory(final EntityData ed, final Vec3d defaultGravity,
            final ShapeFactory<MBlockShape> shapeFactory) {
        super(ed, defaultGravity, shapeFactory);
        this.ed = ed;
    }

    @Override
    protected StaticBody<EntityId, MBlockShape> createStaticBody(final EntityId id, final SpawnPosition pos,
            final ShapeInfo info, final Mass mass) {
        final StaticBody<EntityId, MBlockShape> result = super.createStaticBody(id, pos, info, mass);
        // Do whatever to the static body

        return result;// To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected RigidBody<EntityId, MBlockShape> createRigidBody(final EntityId id, final SpawnPosition pos,
            final ShapeInfo info, final Mass mass, final Gravity gravity) {
        final RigidBody<EntityId, MBlockShape> result = super.createRigidBody(id, pos, info, mass, gravity); // To
                                                                                                             // change
                                                                                                             // body
        // of generated
        // methods,
        // choose Tools |
        // Templates.

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

        return result;
    }

}
