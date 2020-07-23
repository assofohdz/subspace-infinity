/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.systems;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.BinEntityManager;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.BinIndex;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.CollisionCategory;
import infinity.es.Parent;
import infinity.sim.CategoryFilter;
import java.util.logging.Logger;

/**
 *
 * @author AFahrenholz
 */
public class ContactSystem extends AbstractGameSystem implements ContactListener {

    private static final Logger log = Logger.getLogger(ContactSystem.class.getName());

    private EntityData ed;
    private MPhysSystem<MBlockShape> physics;
    private PhysicsSpace<EntityId, MBlockShape> space;
    private BinIndex binIndex;
    private BinEntityManager binEntityManager;
    EntitySet categoryFilters;

    @Override
    public void newContact(Contact contact) {
        RigidBody bodyOne = contact.body1;
        RigidBody bodyTwo = contact.body2;

        if (bodyOne != null && bodyTwo != null) {
            EntityId one = (EntityId) contact.body1.id;
            EntityId two = (EntityId) contact.body2.id;

            if (categoryFilters.containsId(two) && categoryFilters.containsId(one)) {
                CategoryFilter filterOne = categoryFilters.getEntity(one).get(CollisionCategory.class).getFilter();
                CategoryFilter filterTwo = categoryFilters.getEntity(two).get(CollisionCategory.class).getFilter();

                if (!filterTwo.isAllowed(filterOne)) {
                    contact.disable();
                    return;
                }
            }

            Parent parentOfOne = ed.getComponent(one, Parent.class);
            if (parentOfOne != null && parentOfOne.getParentEntity().compareTo(two) == 0) {
                //We have a parent on entity one and its equal to two
                contact.disable();
                return;
            }

            Parent parentOfTwo = ed.getComponent(two, Parent.class);
            if (parentOfTwo != null && parentOfTwo.getParentEntity().compareTo(one) == 0) {
                //We have a parent on entity two and its equal to one
                contact.disable();
                return;
            }
            
            log.info("Collision between: "+bodyOne+" and "+bodyTwo);
            
        } else {
            //This happens when a dynamic collides with a static body or the world
            if (bodyOne != null) {
                log.info("Collided: " +bodyOne.toString() + " with null");
            } else {
                log.info("Collided: " +bodyTwo.toString() + " with null");
            }
            
            contact.restitution = 1;
        }

    }

    @Override
    public void stop() {
        super.stop(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void update(SimTime time) {
        super.update(time); //To change body of generated methods, choose Tools | Templates.

        categoryFilters.applyChanges();
    }

    @Override
    public void start() {
        super.start(); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        if (ed == null) {
            throw new RuntimeException(getClass().getName() + " system requires an EntityData object.");
        }
        this.physics = (MPhysSystem<MBlockShape>) getSystem(MPhysSystem.class);
        if (physics == null) {
            throw new RuntimeException(getClass().getName() + " system requires the MPhysSystem system.");
        }

        this.space = physics.getPhysicsSpace();
        this.binIndex = space.getBinIndex();
        this.binEntityManager = physics.getBinEntityManager();

        categoryFilters = ed.getEntities(CollisionCategory.class);
    }

    @Override
    protected void terminate() {
        categoryFilters.release();
        categoryFilters = null;
    }
}
