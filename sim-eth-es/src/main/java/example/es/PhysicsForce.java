package example.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.mathd.Vec3d;
import org.dyn4j.dynamics.Force;
import org.dyn4j.dynamics.Torque;
import org.dyn4j.geometry.Vector2;

/**
 * Represents the linear and angular velocity for an entity.
 *
 * @author Asser Fahrenholz
 */
public class PhysicsForce implements EntityComponent {

    private Force force;
    private Torque torque;
    private Vector2 velocity;

    public PhysicsForce() {
    }

    public PhysicsForce(Force force, Torque torque, Vector2 velocity) {
        this.force = force;
        this.torque = torque;
        this.velocity = velocity;
    }

    public Force getForce() {
        return force;
    }

    public Torque getTorque() {
        return torque;
    }
    
    public Vector2 getVelocity() {
        return velocity;
    }

    @Override
    public String toString() {
        return "PhysicsForce{" + "force=" + force + ", torque=" + torque + ", velocity=" + velocity + '}';
    }
}
