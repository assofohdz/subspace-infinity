/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.es.filter.FieldFilter;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.TimeState;
import infinity.api.es.ActionType;
import infinity.api.es.ActionTypes;
import infinity.api.es.Parent;
import infinity.api.es.ship.Energy;
import infinity.api.es.ship.actions.Repel;
import infinity.api.sim.ModuleGameEntities;
import infinity.sim.SimpleBody;
import infinity.sim.SimplePhysics;
import java.util.Iterator;
import java.util.LinkedHashSet;
import org.dyn4j.collision.manifold.Manifold;
import org.dyn4j.collision.manifold.ManifoldPoint;
import org.dyn4j.collision.narrowphase.Penetration;
import org.dyn4j.dynamics.Body;
import org.dyn4j.dynamics.BodyFixture;
import org.dyn4j.dynamics.CollisionListener;
import org.dyn4j.dynamics.Force;
import org.dyn4j.dynamics.contact.ContactConstraint;
import org.dyn4j.geometry.Transform;
import org.dyn4j.geometry.Vector2;

/**
 * This state holds logic for the actions that players can take. This includes
 * repels, bricks, warps.
 *
 * @author Asser Fahrenholz
 */
public class ActionState extends AbstractGameSystem implements CollisionListener {

    private LinkedHashSet<Action> sessionActions = new LinkedHashSet<>();
    private EntityData ed;
    private SimplePhysics physics;

    private EntitySet repels;
    private SimTime time;
    private EntitySet repellers;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        physics = getSystem(SimplePhysics.class);
        if (physics == null) {
            throw new RuntimeException("GameSessionHostedService requires a SimplePhysics system.");
        }
        
        physics.addCollisionListener(this);

        FieldFilter repelFilter = new FieldFilter(ActionType.class, "type", ActionTypes.repel(ed).getType());

        repels = ed.getEntities(repelFilter, ActionType.class, Parent.class);

        //Repellers have a count of Repels
        repellers = ed.getEntities(Repel.class);

    }

    @Override
    protected void terminate() {
        repels.release();
        repels = null;

        repellers.release();
        repellers = null;
        
        physics.removeCollisionListener(this);
    }

    @Override
    public void update(SimTime tpf) {
        repels.applyChanges();
        repellers.applyChanges();

        time = tpf;
        /*
        Default pattern to let multiple sessions call methods and then process them one by one
         */
        Iterator<Action> iterator = sessionActions.iterator();
        while (iterator.hasNext()) {
            Action a = iterator.next();

            this.performAction(a.getOwner(), a.getAction());

            iterator.remove();
        }
    }

    public void sessionRepel(EntityId shipEntity, String actionType) {
        sessionActions.add(new Action(shipEntity, ActionType.create(actionType, ed)));
    }

    private void entityRepel(EntityId requestor) {
        Entity entity = repellers.getEntity(requestor);
        Repel repel = entity.get(Repel.class);

        //Check authorization
        if (!repellers.containsId(requestor) || repel.getCount() < 1) {
            return;
        }

        //Get action info
        ActionInfo info = this.getActionInfo(requestor);

        //Perform action
        this.performRepel(info, requestor);

        //Set new repel count
        ed.setComponent(requestor, repel.decrement(1));
    }

    private void performRepel(ActionInfo info, EntityId requestor) {
        ModuleGameEntities.createRepel(requestor, info.getLocation(), ed, time.getTime());

        //Create a collision shape, detect collisions, apply outward forces
        //Steal from the pushing wormhole calculations
        //throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean collision(Body body1, BodyFixture fixture1, Body body2, BodyFixture fixture2) {
        return true;
    }

    @Override
    public boolean collision(Body body1, BodyFixture fixture1, Body body2, BodyFixture fixture2, Penetration penetration) {

        return true;
    }

    @Override
    public boolean collision(Body body1, BodyFixture fixture1, Body body2, BodyFixture fixture2, Manifold manifold) {

        //Calculate forces of the repel on anyone in the area
        //Create a collision shape, detect collisions, apply outward forces
        //Steal from the pushing wormhole calculations
        //
        EntityId one = (EntityId) body1.getUserData();
        EntityId two = (EntityId) body2.getUserData();

        //Case if one is a repel and two is a body that needs to be pushed
        if (repels.getEntity(one) != null) {
            Entity eOne = repels.getEntity(one);
            Repel repel = eOne.get(Repel.class);
            Parent parentOfOne = eOne.get(Parent.class);

            //The creator of the repel is not impacted
            if (parentOfOne.getParentEntity().equals(two)) {
                return false;
            }

            createRepelForce(one, body1, two, body2, manifold.getPoints().get(0), time.getTpf());

        }

        //Case if two is a repel
        if (repels.getEntity(two) != null) {
            Entity eTwo = repels.getEntity(two);
            Repel twoRepel = eTwo.get(Repel.class);
            Parent parentOfTwo = eTwo.get(Parent.class);

            //The creator of the repel is not impacted
            if (parentOfTwo.getParentEntity().equals(one)) {
                return false;
            }
            createRepelForce(two, body2, one, body1, manifold.getPoints().get(0), time.getTpf());
        }

        //We keep impacting the other bodies for as long as a repel is active
        return true;
    }

    @Override
    public boolean collision(ContactConstraint contactConstraint) {
        return true;
    }

    private class ActionInfo {

        private final Vec3d location;
        private final Quatd orientation;
        private final double rotation;
        private final Vector2 attackVel;

        public ActionInfo(Vec3d location, Quatd orientation, double rotation, Vector2 attackVel) {
            this.location = location;
            this.orientation = orientation;
            this.rotation = rotation;
            this.attackVel = attackVel;
        }

        public Vec3d getLocation() {
            return location;
        }

        public Quatd getOrientation() {
            return orientation;
        }

        public double getRotation() {
            return rotation;
        }

        public Vector2 getAttackVel() {
            return attackVel;
        }
    }

    private class Action {

        ActionType action;
        EntityId owner;

        public ActionType getAction() {
            return action;
        }

        public EntityId getOwner() {
            return owner;
        }

        public Action(EntityId owner, ActionType action) {
            this.action = action;
            this.owner = owner;
        }
    }

    /**
     * Performs an action for an entity
     *
     * @param requestor the requesting entity
     * @param type the type of action to perform
     */
    private void performAction(EntityId requestor, ActionType type) {
        switch (type.getTypeName(ed)) {
            case ActionTypes.REPEL:
                this.entityRepel(requestor);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported action " + type);
        }
    }

    /**
     * Performs an attack
     *
     * @param requestor requesting entity
     */
    private ActionInfo getActionInfo(EntityId attacker) {
        EntityId eId = attacker;
        SimTime localTime = time; //Copy incase someone else is writing to it

        Quatd orientation = new Quatd();
        Vec3d location;

        //if (e.get(HitPoints.class) != null) {
        double rotation;
        Vector2 actionVelocity;
        // Attacking body:
        SimpleBody shipBody = physics.getBody(attacker);
        Transform shipTransform = shipBody.getTransform();

        //Velocity
        actionVelocity = shipBody.getLinearVelocity();

        //Position
        Vector2 attackPos = shipTransform.getTranslation();

        //Convert to Vec3d because that's what the rest of sim-eth-es uses
        location = new Vec3d(attackPos.x, attackPos.y, 0);
        orientation = shipBody.orientation;
        rotation = shipTransform.getRotationAngle();

        return new ActionInfo(location, orientation, rotation, actionVelocity);
    }

    /**
     * Creates a force on a given entity
     *
     * @param wormholeEntityId the wormhole entity that exerts a force
     * @param bodyEntityId the body entity impacted by the force
     * @param wormholeBody the wormhole body
     * @param body the impacted body
     * @param mp the manifold
     * @param tpf the time per frame
     */
    private void createRepelForce(EntityId repellerId, Body repellerBody, EntityId bodyEntityId, Body body, ManifoldPoint mp, double tpf) {
        Vec3d repelLocation = new Vec3d(repellerBody.getTransform().getTranslationX(), repellerBody.getTransform().getTranslationY(), 0);
        Vec3d impactedBodyLocation = new Vec3d(body.getTransform().getTranslation().x, body.getTransform().getTranslation().y, 0);

        Repel repel = ed.getComponent(repellerId, Repel.class);

        //start applying gravity to other entity
        Force force = getRepelForceOnBody(tpf, repel, repelLocation, impactedBodyLocation);

        ModuleGameEntities.createForce(bodyEntityId, force, mp.getPoint(), ed, time.getTime());
    }

    private Force getRepelForceOnBody(double tpf, Repel repel, Vec3d repelLocation, Vec3d bodyLocation) {
        Force result = new Force(0, 0);

        return result;
    }

}
