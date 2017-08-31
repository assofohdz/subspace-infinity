package example.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.GameConstants;
import example.PhysicsConstants;
import example.es.Attack;
import example.es.AttackDirection;
import example.es.AttackVelocity;
import example.es.ProjectileType;
import example.es.ProjectileTypes;
import example.es.BodyPosition;
import example.es.Damage;
import example.es.GravityWell;
import example.es.HitPoints;
import example.es.PhysicsVelocity;
import example.es.Position;
import example.sim.SimpleBody;
import example.sim.GameEntities;
import example.sim.SimplePhysics;
import java.util.HashSet;
import org.dyn4j.geometry.Transform;
import org.dyn4j.geometry.Vector2;

/**
 * @author Asser Fahrenholz
 */
public class AttackProjectileState extends AbstractGameSystem {

    private EntityData ed;
    private EntitySet attacks;
    private Object gameSystems;
    private SimplePhysics physics;
    private SimTime time;
    private HealthState health;

    @Override
    public void update(SimTime tpf) {

        if (attacks.applyChanges()) {
            for (Entity e : attacks.getAddedEntities()) {
                Attack a = e.get(Attack.class);
                ProjectileType at = e.get(ProjectileType.class);
                Damage damage = e.get(Damage.class);
                        
                this.attack(a, at, damage);

                ed.removeEntity(e.getId()); //Attack has been processed, now remove it
            }
        }

        time = tpf;
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        // Get the physics system... it's not available yet when onInitialize() is called.
        physics = getSystem(SimplePhysics.class);
        if (physics == null) {
            throw new RuntimeException("GameSessionHostedService requires a SimplePhysics system.");
        }

        health = getSystem(HealthState.class);

        attacks = ed.getEntities(Attack.class, ProjectileType.class, Damage.class);
    }

    @Override
    protected void terminate() {
        attacks.release();
        attacks = null;
    }

    private void attack(Attack a, ProjectileType type, Damage damage) {
        EntityId eId = a.getOwner();
        SimTime localTime = time; //Copy incase someone else is writing to it
        Entity e = ed.getEntity(eId, HitPoints.class);

        Quatd orientation = new Quatd();
        Vec3d location;
        double rotation;
        Vector2 attackVel;

        if (e.get(HitPoints.class) != null) {
            // ship
            // TODO make more linear design
            HitPoints hp = e.get(HitPoints.class); //The current health of the attacker

            //Derive how much health it will take to perform the given attack
            //Perform the attack
            // Attacking body:
            SimpleBody shipBody = physics.getBody(e.getId());

            Transform shipTransform = shipBody.getTransform();

            //Bomb velocity:
            attackVel = this.getAttackVelocity(shipTransform.getRotation(), type, shipBody.getLinearVelocity());

            //Position
            Vector2 attackPos = this.getAttackPosition(shipTransform.getRotation(), shipTransform.getTranslation());

            //Convert to Vec3d because that's what the rest of sim-eth-es uses
            location = new Vec3d(attackPos.x, attackPos.y, 0); //TODO: missing arena as z
            orientation = shipBody.orientation;
            rotation = shipTransform.getRotation();
        } else {
            // towers
            e = ed.getEntity(eId, AttackVelocity.class, Position.class, AttackDirection.class);
            location = e.get(Position.class).getLocation().add(new Vec3d(0, 0, 0));

            Vector2 ori = e.get(AttackDirection.class).getMethod();
            System.out.println(ori.toString());
            orientation = orientation.fromAngles(ori.getAngleBetween(new Vector2(1, 0)), ori.getAngleBetween(new Vector2(0, 1)), 0);
            rotation = 0;
            // skal angive længde og ikke bare multiply...
            attackVel = ori.multiply(e.get(AttackVelocity.class).getVelocity());
        }
        EntityId projectile;
        switch (type.getTypeName(ed)) {
            case ProjectileTypes.BOMB:
                projectile = GameEntities.createBomb(location, orientation, rotation, attackVel, GameConstants.BULLETDECAY, ed);
                ed.setComponent(projectile, damage);
                break;
            case ProjectileTypes.BULLET:
                projectile = GameEntities.createBullet(location, orientation, rotation, attackVel, GameConstants.BULLETDECAY, ed);
                ed.setComponent(projectile, damage);
                break;
            case ProjectileTypes.GRAVITYBOMB:
                HashSet<EntityComponent> delayedComponents = new HashSet<>();
                delayedComponents.add(new GravityWell(5, GameConstants.GRAVBOMBWORMHOLEFORCE, GravityWell.PULL));             //Suck everything in
                delayedComponents.add(new PhysicsVelocity(new Vector2(0, 0))); //Freeze the bomb
                delayedComponents.add(ProjectileTypes.gravityBomb(ed));
                projectile = GameEntities.createDelayedBomb(location, orientation, rotation, attackVel, GameConstants.GRAVBOMBDECAY, GameConstants.GRAVBOMBDELAY, delayedComponents, ed);
                ed.setComponent(projectile, damage);
                break;
        }

        //Create a buff/healthchange component because attacking takes health
        // EntityId buffEntity = ed.createEntity();
        // ed.setComponents(buffEntity, new Buff(e.getId(), localTime.getTime()), new HealthChange(0)); //TODO: change in health not set
    }

    private Vector2 getAttackVelocity(double rotation, ProjectileType type, Vector2 linearVelocity) {
        Vector2 attackVel = new Vector2(0, 1);

        attackVel.rotate(rotation);

        attackVel.multiply(GameConstants.BASEPROJECTILESPEED);

        if (type.getTypeName(ed).equals(ProjectileTypes.BOMB)) {
            attackVel.multiply(GameConstants.BOMBPROJECTILESPEED);
            attackVel.add(linearVelocity); //Add ships velocity to account for direction of ship and rotation

        } else if (type.getTypeName(ed).equals(ProjectileTypes.BULLET)) {
            attackVel.multiply(GameConstants.BULLETPROJECTILESPEED);
            attackVel.add(linearVelocity); //Add ships velocity to account for direction of ship and rotation

        } else if (type.getTypeName(ed).equals(ProjectileTypes.GRAVITYBOMB)) {
            attackVel.multiply(GameConstants.GRAVBOMBPROJECTILESPEED);
            attackVel.add(linearVelocity); //Add ships velocity to account for direction of ship and rotation

        } else if (type.getTypeName(ed).equals(ProjectileTypes.MINE)) {
            attackVel.multiply(0); //A mine stands still
        }

        return attackVel;
    }

    private Vector2 getAttackPosition(double rotation, Vector2 translation) {
        Vector2 attackPos = new Vector2(0, 1);
        attackPos.rotate(rotation);
        attackPos.multiply(PhysicsConstants.PROJECTILEOFFSET);
        attackPos.add(translation.x, translation.y);
        return attackPos;
    }

}
