package infinity.es.ship.weapons;

import com.simsilica.es.EntityComponent;

/**
 * A component that indicates that an entity can bounce a certain number of times. This is used
 * on bullets and bombs.
 */
public class Bounce implements EntityComponent {

  private final int bounces;

  public Bounce(final int bounces) {
    this.bounces = bounces;
  }

  public int getBounces() {
    return bounces;
  }

  public Bounce decreaseBounces() {
    return new Bounce(bounces - 1);
  }
}
