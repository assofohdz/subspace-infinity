package infinity.systems;

import com.simsilica.bpos.BodyPosition;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.SpawnPosition;
import com.simsilica.mathd.Vec3d;
import com.simsilica.mphys.AbstractBody;
import com.simsilica.mphys.Contact;
import com.simsilica.mphys.ContactListener;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.es.GravityWell;
import java.util.HashSet;

/**
 * A system to handle gravity wells.
 *
 * @author AFahrenholz
 */
public class GravitySystem extends AbstractGameSystem implements ContactListener {

  private SimTime time;

  private EntityData ed;
  private EntitySet gravityWells;
  // A set to map from the pulling gravity wells to a pushing gravity well
  private ContactSystem contactSystem;

  protected void initialize() {
    this.ed = getSystem(EntityData.class);

    this.contactSystem = getSystem(ContactSystem.class);

    this.contactSystem.addListener(this);

    gravityWells = ed.getEntities(GravityWell.class, BodyPosition.class);
  }

  protected void terminate() {
    gravityWells.release();
    gravityWells = null;
  }

  @Override
  public void start() {
    // TODO Auto-generated method stub
  }

  @Override
  public void stop() {
    // TODO Auto-generated method stub
  }

  @Override
  public void update(SimTime tpf) {
    time = tpf;
  }

  @Override
  public void newContact(Contact contact) {

    RigidBody body1 = contact.body1;
    AbstractBody body2 = contact.body2;

    // Check if body2 is null (if so we are colliding a body with the world and dont want to do
    // anything here)
    if (body2 == null) {
      return;
    }

    EntityId one = (EntityId) body1.id;
    EntityId two = (EntityId) body2.id;

    // get GravityWell from body2
    GravityWell gw = ed.getComponent(two, GravityWell.class);

    // Check if GravityWell is null, if so, this isn't a contact for us to handle
    if (gw == null) {
      return;
    }

    //For the ship we want the latest positiom
    Vec3d bodyLocation = ed.getComponent(one, BodyPosition.class).getLastLocation();
    //For the wormhole we want the spawn position because it is a static object
    Vec3d wormholeLocation = ed.getComponent(two, SpawnPosition.class).getLocation();

    Vec3d difference = wormholeLocation.subtract(bodyLocation);
    Vec3d gravity = difference.normalize().multLocal(time.getTpf());
    double distance = difference.length();

    double wormholeGravity = gw.getForce();
    double gravityDistance = gw.getDistance();

    switch (gw.getGravityType()) {
        // Note 03-02-2023: I dont understand this math right now
      case GravityWell.PULL:
        gravity.multLocal(Math.abs(wormholeGravity));
        break;
      case GravityWell.PUSH:
        gravity.multLocal(1 * Math.abs(wormholeGravity));
        break;
      default:
        break;
    }

    gravity.multLocal(gravityDistance / distance);
    //Zero out the y-force
    gravity.y = 0;

    // Apply the gravity to the body
    body1.addForce(gravity);
    contact.disable();
  }
}
