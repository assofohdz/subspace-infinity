package example.sim;

import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import org.dyn4j.dynamics.Torque;
import org.dyn4j.geometry.Vector2;

/**
 * Uses rotation and a 3-axis thrust vector to supply specific velocity to a
 * body. We ignore the normal physics acceleration for now and just set the
 * velocity directly based on our accelerated thrust values.
 *
 * @author Paul Speed
 */
public class MobDriver implements ControlDriver {

    // Keep track of what the player has provided.
    private volatile Quaternion orientation = new Quaternion();
    private volatile Vector3f thrust = new Vector3f();

    private double pickup = 3;

    public void applyMovementState(Vector3f thrust) {
        this.thrust = thrust;
    }

    @Override
    public void update(double stepTime, SimpleBody body) {
        Vector3f vec = thrust;
        //Calculate angle between corrent velocity and desired velocity
        Vector2 currentVelocity = body.getLinearVelocity();
        Vector3f desiredForce = new Vector3f(thrust.x, thrust.y, 0);
        
        Vector3f currentVelocity3f = new Vector3f((float) currentVelocity.x, (float) currentVelocity.y, 0);
       
        //Radians betwen the desired force and current velocity
        //float angle = desiredForce.angleBetween(currentVelocity3f);
        //body.applyTorque(new Torque(angle < FastMath.PI ? 1 : -1));

        Vector2 linVel = new Vector2(vec.x, vec.y);
        body.applyForce(linVel);
        body.applyTorque(0.4);
    }
}
