package example.sim;

import com.simsilica.es.*;
import com.simsilica.mathd.*;
import com.simsilica.sim.*;


/**
 * Creates a bunch of base entities in the environment.
 *
 * @author Paul Speed
 */
public class BasicEnvironment extends AbstractGameSystem {

    private EntityData ed;

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        if (ed == null) {
            throw new RuntimeException("SimplePhysics system requires an EntityData object.");
        }
    }

    @Override
    protected void terminate() {
    }

    @Override
    public void start() {

        // Create some built in objects
        double spacing = 256;
        double offset = -2 * spacing + spacing * 0.5;
        for (int x = 0; x < 4; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = 0; z < 4; z++) {
                    Vec3d pos = new Vec3d(offset + x * spacing, offset + y * spacing, offset + z * spacing);
                    GameEntities.createGravSphere(pos, 10, ed);
                }
            }
        }

        //GameEntities.createBountySpawner(new Vec3d(0, 0, 0), 10, ed);0
        
        //GameEntities.createExplosion2(new Vec3d(5,5,0), new Quatd().fromAngles(0, 0, Math.random()*360), ed);

        //GameEntities.createWormhole(new Vec3d(-10,-10,0), 5, 5, 5000, GravityWell.PULL, new Vec3d(10,-10,0), ed);
        
        //GameEntities.createOver5(new Vec3d(10,-10,0), 5, 5000, GravityWell.PUSH, ed);
        
        GameEntities.createTower(new Vec3d(5, 5, 0), ed);
        GameEntities.createTower(new Vec3d(5, 7, 0), ed);
        GameEntities.createTower(new Vec3d(5, 3.5, 0), ed);
        GameEntities.createTower(new Vec3d(6, 9, 0), ed);
        GameEntities.createTower(new Vec3d(4, 2, 0), ed);
        
        GameEntities.createMob(new Vec3d(-5, 5, 0), ed);
        GameEntities.createMob(new Vec3d(-10, -10, 0), ed);
        GameEntities.createMob(new Vec3d(-20, -30, 0), ed);
        GameEntities.createMob(new Vec3d(10, -5, 0), ed);
        GameEntities.createMob(new Vec3d(20, -20, 0), ed);
        GameEntities.createMob(new Vec3d(15, -10, 0), ed);
        
        GameEntities.createBase(new Vec3d(30, 10, 0), ed);
        
        /*
        GameEntities.createOver1(new Vec3d(10,10,0), ed);
        GameEntities.createOver1(new Vec3d(11,10,0), ed);
        GameEntities.createOver1(new Vec3d(12,10,0), ed);
        GameEntities.createOver1(new Vec3d(13,10,0), ed);
        GameEntities.createOver1(new Vec3d(10,9,0), ed);
        GameEntities.createOver1(new Vec3d(10,8,0), ed);
        GameEntities.createOver1(new Vec3d(10,7,0), ed);
        */
        /*
        for (int x = -4; x < 4; x++) {
            for (int y = -4; y < 4; y++) {
                Vec3d pos = new Vec3d(x, y, 0); 
                GameEntities.createBounty(pos, ed);

            }
        }
        */
        
        //GameEntities.createWormhole(new Vec3d(-10,10,0), 5, 5, 5000, GravityWell.PULL, new Vec3d(-512,700,0), ed);
        
    }

    @Override
    public void stop() {
        // For now at least, we won't be reciprocal, ie: we won't remove
        // all of the stuff we added.
    }

}
