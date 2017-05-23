package example.es;

import com.simsilica.es.EntityComponent;
import com.simsilica.es.EntityId;
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
    private EntityId target;

    public PhysicsForce() {
    }

    public PhysicsForce(EntityId target, Force force, Torque torque) {
        this.force = force;
        this.torque = torque;
        this.target = target;
    }

    public EntityId getTarget() {
        return target;
    }

    public Force getForce() {
        return force;
    }

    public Torque getTorque() {
        return torque;
    }

    @Override
    public String toString() {
        return "PhysicsForce{" + "force=" + force + ", torque=" + torque + ", target=" + target + '}';
    }
}
