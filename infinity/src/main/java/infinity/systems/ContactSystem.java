/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.systems;

import java.util.logging.Logger;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

import infinity.es.CollisionCategory;
import infinity.es.Parent;
import infinity.sim.CategoryFilter;

/**
 *
 * @author AFahrenholz
 */
public class ContactSystem extends AbstractGameSystem implements ContactListener<EntityId, MBlockShape> {

    private static final Logger log = Logger.getLogger(ContactSystem.class.getName());

    private EntityData ed;
    private MPhysSystem<?> physics;
    // private PhysicsSpace<EntityId, MBlockShape> space;
    // private BinIndex binIndex;
    // private BinEntityManager binEntityManager;
    EntitySet categoryFilters;

    @Override
    public void newContact(final Contact<EntityId, MBlockShape> contact) {
        final RigidBody<EntityId, MBlockShape> bodyOne = contact.body1;
        final RigidBody<EntityId, MBlockShape> bodyTwo = contact.body2;

        if (bodyOne != null && bodyTwo != null) {
            final EntityId one = contact.body1.id;
            final EntityId two = contact.body2.id;

            if (categoryFilters.containsId(two) && categoryFilters.containsId(one)) {
                final CategoryFilter filterOne = categoryFilters.getEntity(one).get(CollisionCategory.class)
                        .getFilter();
                final CategoryFilter filterTwo = categoryFilters.getEntity(two).get(CollisionCategory.class)
                        .getFilter();

                if (!filterTwo.isAllowed(filterOne)) {
                    contact.disable();
                    return;
                }
            }

            final Parent parentOfOne = ed.getComponent(one, Parent.class);
            if (parentOfOne != null && parentOfOne.getParentEntity().compareTo(two) == 0) {
                // We have a parent on entity one and its equal to two
                contact.disable();
                return;
            }

            final Parent parentOfTwo = ed.getComponent(two, Parent.class);
            if (parentOfTwo != null && parentOfTwo.getParentEntity().compareTo(one) == 0) {
                // We have a parent on entity two and its equal to one
                contact.disable();
                return;
            }

            log.info("Collision between: " + bodyOne + " and " + bodyTwo);

        } else {
            // This happens when a dynamic collides with a static body or the world
            if (bodyOne != null) {
                log.info("Collided: " + bodyOne.toString() + " with null");
            } else if (bodyTwo != null) {
                log.info("Collided: " + bodyTwo.toString() + " with null");
            }

            contact.restitution = 1;
        }

    }

    @Override
    public void stop() {
        super.stop(); // To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void update(final SimTime time) {
        super.update(time); // To change body of generated methods, choose Tools | Templates.

        categoryFilters.applyChanges();
    }

    @Override
    public void start() {
        super.start(); // To change body of generated methods, choose Tools | Templates.
    }

    @Override
    protected void initialize() {
        ed = getSystem(EntityData.class);
        if (ed == null) {
            throw new RuntimeException(getClass().getName() + " system requires an EntityData object.");
        }
        physics = getSystem(MPhysSystem.class);
        if (physics == null) {
            throw new RuntimeException(getClass().getName() + " system requires the MPhysSystem system.");
        }

        // space = physics.getPhysicsSpace();
        // binIndex = space.getBinIndex();
        // binEntityManager = physics.getBinEntityManager();

        categoryFilters = ed.getEntities(CollisionCategory.class);
    }

    @Override
    protected void terminate() {
        categoryFilters.release();
        categoryFilters = null;
    }
}
