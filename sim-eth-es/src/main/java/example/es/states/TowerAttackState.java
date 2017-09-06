package example.es.states;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import example.es.AttackDirection;
import example.es.AttackMethodType;
import example.es.AttackRate;
import example.es.Damage;
import example.es.Position;
import example.es.ProjectileType;
import example.es.Range;
import example.es.RotationSpeed;
import example.es.TowerRotationSpeed;
import example.es.TowerType;
import example.sim.CollisionFilters;
import example.sim.GameEntities;
import example.sim.SimplePhysics;
import java.util.HashMap;
import java.util.LinkedList;
import org.dyn4j.geometry.AABB;
import org.dyn4j.dynamics.DetectResult;
import org.dyn4j.geometry.Vector2;

/**
 * State
 *
 * @author Asser
 */
public class TowerAttackState extends AbstractGameSystem {

    private EntityData ed;
    private SimplePhysics simplePhysics;
    private EntitySet towerRange;
    private HashMap lastShoot = new HashMap<>();
    private EntitySet towerAttack;
    private EntitySet towerRotation;
    private HashMap<EntityId, LinkedList<DetectResult>> towerToTargets = new HashMap<>();
    private HashMap<EntityId, Double> towerToTargetRelativeRadians = new HashMap<>();

    @Override
    protected void initialize() {
        testQuatd();
        this.ed = getSystem(EntityData.class);
        this.simplePhysics = getSystem(SimplePhysics.class);

        // giver det mening at dele den op ?
        this.towerRange = ed.getEntities(TowerType.class, Position.class, Range.class);
        this.towerRotation = ed.getEntities(TowerType.class, Position.class, RotationSpeed.class);
        this.towerAttack = ed.getEntities(TowerType.class, Position.class, AttackMethodType.class, AttackRate.class, Damage.class, ProjectileType.class);
    }

    @Override
    protected void terminate() {
        //Release reader object
        towerRange.release();
        towerRange = null;
        
        towerAttack.release();
        towerAttack = null;
        
        towerRotation.release();
        towerRotation = null;
    }

    @Override
    public void update(SimTime tpf) {

        //Find those in range for each tower:
        //TODO: Add a wait time for checking the available targets (so not every update, but perhaps every 100 ms or so)
        towerRange.applyChanges();
        for (Entity e : towerRange) {
            Vector2 location = new Vector2(e.get(Position.class).getLocation().x, e.get(Position.class).getLocation().y);
            double range = e.get(Range.class).getRange();

            LinkedList<DetectResult> result = new LinkedList<>();
            simplePhysics.getWorld().detect(new AABB(location, range), CollisionFilters.FILTER_CATEGORY_SENSOR_TOWERS, true, true, result);
            if (!result.isEmpty()) {
                towerToTargets.put(e.getId(), result);
            }
            else {
                towerToTargets.remove(e.getId());
            }
        }
        
        //Rotate towards the target(s)
        towerRotation.applyChanges();
        for (Entity e : towerRotation) {
            //Check if tower has target(s):
            if (towerToTargets.containsKey(e.getId())) { // TODO itterate over this and get entity from towerRotation instead
                
                Vec3d towerLocation = e.get(Position.class).getLocation();
                Vector2 towerLocation2d = new Vector2(towerLocation.x, towerLocation.y);
                //Find random target and get the direction
                //TODO: Should probably keep track of current target, so as not to switch targets all the time
                //TODO: Add a target selection strategy here:
                Vector2 mobLocation = random(towerToTargets.get(e.getId())).subtract(towerLocation2d).getNormalized();
             
                // Turn the tower !
                // - how fast
                double radSec = e.get(RotationSpeed.class).getRadSec();
                // - how much
                double mobRad = getRelativeDirection(mobLocation);
                towerToTargetRelativeRadians.put(e.getId(), mobRad);
                
                // set Attackdirection on entity, so shoots can get it (since precicion is +- 0.2 rad)
                ed.setComponent(e.getId(), new AttackDirection(mobLocation)); 
                
                // - get current tower-angel
                double towerRad = getAngles(e.get(Position.class).getFacing());

                // - get new tower-angel
                double newTowerRad = getTowerRotation(mobRad, towerRad, radSec * tpf.getTpf());
                
                // set rotation
                ed.setComponent(e.getId(), new Position(towerLocation, setQuatdRotationZ(newTowerRad), 0));
              

            }
        }

        //Attack the target(s)
        towerAttack.applyChanges();
        for (Entity e : towerAttack.getAddedEntities()) {
            lastShoot.put(e.getId().getId(), tpf.getTime());
        }
        for (Entity e : towerAttack) {
            double temp = getAngles(e.get(Position.class).getFacing()); // current tower angle
            
            //TODO itterate on this instead
            if (towerToTargets.containsKey(e.getId()) //The tower that can attack has a target
                    && Math.abs(towerToTargetRelativeRadians.get(e.getId()) - temp) < 0.2 //It also has an acceptable direction
                    && e.get(AttackRate.class).getRate() < tpf.getTime() - (long) lastShoot.get(e.getId().getId())) { //And can fire
                //Get information on the amount of damage the tower does
                Damage damage = e.get(Damage.class);
                // Fire !!!!!!!!
                EntityId attackEntity = GameEntities.createAttack(e.getId(), e.get(ProjectileType.class).getTypeName(ed), ed);
                
                //Se the amount of damage the attack should do
                ed.setComponent(attackEntity, new Damage(damage.getDamage()));
                lastShoot.put(e.getId().getId(), tpf.getTime());
            }
        }
        /*
        for (Entity e : towerRange) {

            Vector2 location = new Vector2(e.get(Position.class).getLocation().x, e.get(Position.class).getLocation().y);
            double range = e.get(Range.class).getRange();

            LinkedList<DetectResult> result = new LinkedList<>();
            boolean activate = simplePhysics.getWorld().detect(new AABB(location, range), CollisionFilters.FILTER_CATEGORY_SENSOR_TOWER, true, true, result);

            if (activate) {
                // Find Target  
                Vector2 dir = setAttackDirection(e, result);

                // Turn the tower !
                // - how fast
                double radSec = e.get(TowerRotationSpeed.class).getRadSec();
                // - how much
                double mobRad = getRelativeDirection(dir);

                Quatd face = e.get(Position.class).getFacing();
                double wRad = getAngles(face);

                double rad = getTowerRotation(mobRad, wRad, radSec * tpf.getTpf());
                System.out.println("face: " + face.toString());

                System.out.println("mobCoord: " + dir + "  mobRad: " + mobRad + " towerRad:" + wRad + " moveRad: " + rad);

                // set rotation
                Vec3d loc = e.get(Position.class).getLocation();
                ed.setComponent(e.getId(), new Position(loc, setQuatdRotationZ(rad), 0));

                // Check Fire Rate
                if (e.get(AttackRate.class).getRate() < tpf.getTime() - (long) lastShoot.get(e.getId().getId())) {
                    // Check rotation

                    double temp = getAngles(e.get(Position.class).getFacing());

                    System.out.println("mob " + dir.x + " " + dir.y);
                    System.out.println("diff " + Math.abs(mobRad - temp) + "  " + mobRad + "  " + temp);
                    if (Math.abs(mobRad - temp) < 0.2) {
                        // Fire !!!!!!!!
                        GameEntities.createAttack(e.getId(), e.get(ProjectileType.class).getTypeName(ed), ed);
                        lastShoot.put(e.getId().getId(), tpf.getTime());
                    }
                }
            }
        }
         */
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    /**
     * Set the attack diriection depending on AttackMethodType.class
     *
     * @param e Attacker
     * @param possibilities Targets
     */
    private Vector2 setAttackDirection(Entity e, LinkedList<DetectResult> possibilities) {
        Vector2 dir;
        // get target position
        switch (e.get(AttackMethodType.class).getTypeName(ed)) {
            case "player":
                dir = attackerDirection(e); // set the direction of the player ...
                break;
            case "random":
                dir = random(possibilities);
                break;
            default:
                dir = new Vector2(0, 0);
                break;
        }

        // direction from attacker
        dir = dir.subtract(e.get(Position.class).getLocation().x, e.get(Position.class).getLocation().y);

        dir = dir.getNormalized();
        ed.setComponent(e.getId(), new AttackDirection(dir));
        return dir;
    }

    /**
     * Select random target from passibilities
     *
     * @param possibilities
     * @return
     */
    private Vector2 random(LinkedList<DetectResult> possibilities) {
        int r = (int) (Math.random() * possibilities.size());
        return possibilities.get(r).getBody().getWorldCenter(); //TODO skal sammenholdes med tower placering
    }

    /**
     * Attack in the direction of the attacker
     *
     * @param id
     * @return
     */
    private Vector2 attackerDirection(Entity id) {
        // TODO create a set with attacers direction...
        return new Vector2(0, 0);

    }

    /**
     *
     * @param rad radians (from (1x,0y))
     * @return quatd rotader rad around z axis
     */
    private Quatd setQuatdRotationZ(double rad) {
        // fix range [0, 2Pi]
        rad = (rad % (Math.PI * 2) + (Math.PI * 2)) % (Math.PI * 2);

        // calc w [1,0,-1]
        double w;
        double z;
        if (Math.PI / 2 < rad && rad < Math.PI * 6 / 4) {
            // 2. & 3. kvadrant
            w = 1 - (rad - Math.PI / 2) / Math.PI;
            z = (rad - Math.PI / 2) / Math.PI;
        } else {
            if (rad > Math.PI) {
                // 4. kvad.
                w = -(rad - Math.PI * 3 / 2) / (Math.PI / 2) * 0.5;
                z = 1 - (rad - Math.PI * 3 / 2) / (Math.PI / 2) * 0.5;
            } else {
                // 1. kvad.
                w = -rad / (Math.PI / 2) * 0.5 - 0.5;
                z = 0.5 - (rad / (Math.PI / 2) * 0.5);
            }
        }

        return new Quatd(0, 0, z, w);
    }

    private double getAngles(Quatd q) {

        double out;
        if (q.w > 0) {
            out = q.z * Math.PI + Math.PI / 2;
        } else {
            out = Math.PI * 2 - (q.z * Math.PI + Math.PI * 3 / 2) % (Math.PI * 2);
        }
        // System.out.println("getRad: " +q.z + " "+ q.w + "  out: "+out);
        return out;
    }

    /**
     *
     * @param dir position of mob, relative to tower
     * @return counter-clockwise angel [rad] from (1,0)
     */
    private double getRelativeDirection(Vector2 dir) {
        // smallest angel:  cos(A) = a*b / (|a|*|b|)
        // a=dir, b=(1,0)
        dir = dir.getNormalized();
        // |a|*|b| = 1
        // a*b = a.x

        double mobRad;
        if (dir.y >= 0) {
            // the counter-clockwise angel
            mobRad = Math.acos(dir.x);
        } else {
            // the clockwise angel
            mobRad = Math.PI * 2 - Math.acos(dir.x);
        }
        return mobRad;
    }

    private double getTowerRotation(double mobRad, double towerRad, double maxTurn) {
        double turn;
        if (mobRad > towerRad) {
            if (mobRad - towerRad > Math.PI) {
                turn = towerRad - maxTurn / 1;
            } else {
                turn = towerRad + maxTurn / 1;
            }
        } else {
            if (mobRad - towerRad > Math.PI) {
                turn = towerRad + maxTurn / 1;
            } else {
                turn = towerRad - maxTurn / 1;
            }
        }

        return turn;
    }

    private void testQuatd() {

        System.out.println("TEST: 0rad -> " + setQuatdRotationZ(0).toString()); // 0,0, 0.5 , -0.5
        System.out.println("TEST: Pi/4 rad -> " + setQuatdRotationZ(Math.PI / 4).toString()); // 0,0, 0.25 , -0.75
        System.out.println("TEST: Pi/2 0rad -> " + setQuatdRotationZ(Math.PI / 2).toString()); // 0,0,0,-1
        System.out.println("TEST: Pi/4*3 0rad -> " + setQuatdRotationZ(Math.PI / 4 * 3).toString()); // 0,0, 0.25 , 0.75
        System.out.println("TEST: Pi 0rad -> " + setQuatdRotationZ(Math.PI).toString()); // 0,0, 0 , (1, -1)
        System.out.println("TEST: Pi/4*5 0rad -> " + setQuatdRotationZ(Math.PI / 4 * 5).toString()); // 0,0, 0.25 , -0.75 !!!!
        System.out.println("TEST: Pi*1.5 0rad -> " + setQuatdRotationZ(Math.PI * 1.5).toString()); // 0,0, 0.5, -0.5  !!!! 
        System.out.println("TEST: Pi/4*7 0rad -> " + setQuatdRotationZ(Math.PI / 4 * 7).toString()); // 0,0, 0.75, -0.25 !!!!

        Quatd q = new Quatd(0, 0, 0, 0);
        System.out.println(q.toString());
        System.out.println("TEST: base  -> " + getAngles(new Quatd(0, 0, 0, 0)));
        System.out.println("TEST: 0rad  -> " + getAngles(new Quatd(0, 0, 1, 0)));
        System.out.println("TEST: Pi/4 rad -> " + getAngles(new Quatd(0, 0, 0.75, 0.25)));  // 0.78
        System.out.println("TEST: Pi/2 0rad -> " + getAngles(new Quatd(0, 0, 0.5, 0.5)));  // 1.57
        System.out.println("TEST: Pi/4*3 0rad -> " + getAngles(new Quatd(0, 0, 0.25, 0.75))); // 2.35
        System.out.println("TEST: Pi 0rad -> " + getAngles(new Quatd(0, 0, 0, 1)));  // 3.14
        System.out.println("TEST: Pi 0rad -> " + getAngles(new Quatd(0, 0, 0, -1)));  // 3.14
        System.out.println("TEST: Pi/4*5 0rad -> " + getAngles(new Quatd(0, 0, 0.25, -0.75)));  // 3.92
        System.out.println("TEST: Pi*1.5 0rad -> " + getAngles(new Quatd(0, 0, 0.5, -0.5)));  // 4.71
        System.out.println("TEST: Pi/4*7 0rad -> " + getAngles(new Quatd(0, 0, 0.75, -0.25)));  // 5.49

        System.out.println("TEST: 1,0 -> " + getRelativeDirection(new Vector2(1, 0)));
        System.out.println("TEST: 1,1 -> " + getRelativeDirection(new Vector2(1, 1)));
        System.out.println("TEST: 0,1 -> " + getRelativeDirection(new Vector2(0, 1)));
        System.out.println("TEST: -1,1 -> " + getRelativeDirection(new Vector2(-1, 1)));
        System.out.println("TEST: -1,0 -> " + getRelativeDirection(new Vector2(-1, 0)));
        System.out.println("TEST: -1,-1 -> " + getRelativeDirection(new Vector2(-1, -1)));
        System.out.println("TEST: 0,-1 -> " + getRelativeDirection(new Vector2(0, -1)));
        System.out.println("TEST: 1,-1 -> " + getRelativeDirection(new Vector2(1, -1)));

    }

}
