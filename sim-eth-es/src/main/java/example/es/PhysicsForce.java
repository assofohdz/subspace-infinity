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

    public PhysicsForce() {
    }

    public PhysicsForce(Vec3d force) {
        this.force = new Force(force.x, force.y);
        this.torque = new Torque();
    }

    public PhysicsForce(Force force, Torque torque) {
        this.force = force;
        this.torque = torque;
    }

    public Force getForce() {
        return force;
    }

    public Torque getTorque() {
        return torque;
    }

    @Override
    public String toString() {
        return "PhysicsForce{" + "force=" + force + ", torque=" + torque + '}';
    }
}
