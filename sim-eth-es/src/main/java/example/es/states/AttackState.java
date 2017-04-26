package example.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.GameConstants;
import example.es.AttackType;
import example.es.AttackTypes;
import example.es.BodyPosition;
import example.es.Buff;
import example.es.HealthChange;
import example.es.HitPoints;
import example.sim.Body;
import example.sim.GameEntities;
import example.sim.SimplePhysics;
import org.dyn4j.geometry.Transform;
import org.dyn4j.geometry.Vector2;

/**
 * General app state that watches entities with a AttackType component and
 * HitPoints. It performs the attack if there is enough hitpoints and then
 * removes the attacktype component.
 * 
 * The attacker must have a physical body (BodyPosition)
 *
 * @author Asser Fahrenholz
 */
public class AttackState extends AbstractGameSystem {

    private EntityData ed;
    private EntitySet entities;
    private Object gameSystems;
    private SimplePhysics physics;

    @Override
    public void update(SimTime tpf) {
        entities.applyChanges();
        for (Entity e : entities) {
            AttackType at = e.get(AttackType.class); //The type of attack to perform
            HitPoints hp = e.get(HitPoints.class); //The current health of the attacker

            //Derive how much health it will take to perform the given attack
            //Perform the attack
            // Attacking body:
            Body shipBody = physics.getBody(e.getId());

            Transform shipTransform = shipBody.getTransform();

            //Bomb velocity:
            Vector2 attackVel = new Vector2(0, 1);
            attackVel.rotate(shipTransform.getRotation());
            attackVel.multiply(GameConstants.BULLETPROJECTILESPEED);
            //TODO: multiply by ship speed (account for direction)

            //Bomb position
            Vector2 attackPos = new Vector2(0, 1);
            attackPos.rotate(shipTransform.getRotation());
            attackPos.multiply(GameConstants.PROJECTILEOFFSET);
            attackPos.add(shipTransform.getTranslationX(), shipTransform.getTranslationY());

            Vec3d attackPosVec3d = new Vec3d(attackPos.x, attackPos.y, 0); //TODO: missing arena as z

            switch (at.getTypeName(ed)) {
                case AttackTypes.BOMB:
                    GameEntities.createBomb(attackPosVec3d, shipBody.orientation, shipTransform.getRotation(), attackVel, GameConstants.BULLETDECAY, ed);
                    break;
                case AttackTypes.BULLET:
                    GameEntities.createBullet(attackPosVec3d, shipBody.orientation, shipTransform.getRotation(), attackVel, GameConstants.BULLETDECAY, ed);
                    break;
            }

            //Create a buff/healthchange component because attacking takes health
            ed.setComponents(e.getId(), new Buff(e.getId(), tpf.getTime()), new HealthChange(0)); //TODO: change in health not set

            ed.removeComponent(e.getId(), AttackType.class);
        }
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        // Get the physics system... it's not available yet when onInitialize() is called.
        physics = getSystem(SimplePhysics.class);
        if (physics == null) {
            throw new RuntimeException("GameSessionHostedService requires a SimplePhysics system.");
        }

        entities = ed.getEntities(BodyPosition.class, HitPoints.class, AttackType.class);
    }

    @Override
    protected void terminate() {
        // Release the entity set we grabbed previously
        entities.release();
        entities = null;
    }

}
