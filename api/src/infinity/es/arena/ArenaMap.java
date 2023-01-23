package infinity.es.arena;

import com.simsilica.es.EntityComponent;
import com.simsilica.mathd.Vec3d;

public class ArenaMap implements EntityComponent {

  private Vec3d min;
  private Vec3d max;

  public ArenaMap() {
    //For serialization
  }

  public ArenaMap(final Vec3d min, final Vec3d max) {
    this.min = min;
    this.max = max;
  }

  public Vec3d getMin() {
    return min;
  }

  public Vec3d getMax() {
    return max;
  }
}
