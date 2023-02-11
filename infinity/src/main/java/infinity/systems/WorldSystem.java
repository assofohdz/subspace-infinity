package infinity.systems;

import com.simsilica.mathd.Vec3d;
import com.simsilica.mworld.World;
import com.simsilica.mworld.db.ColumnDb;
import com.simsilica.mworld.db.ColumnDbLeafDbAdapter;
import com.simsilica.mworld.db.LeafDb;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;
import infinity.server.DefaultColumnDb;
import infinity.sim.InfinityDefaultLeafWorld;
import java.io.File;

public class WorldSystem extends AbstractGameSystem {

  private DefaultColumnDb colDb;
  private World world;

  public WorldSystem(){
    colDb = new DefaultColumnDb(new File("world.db"));
    colDb.initialize();
    LeafDb leafDb2 = new ColumnDbLeafDbAdapter(colDb);
    // LeafDb leafDb = new LeafDbCache(new EmptyLeafDb());
    world = new InfinityDefaultLeafWorld(leafDb2, 10);
  }

  public ColumnDb getColumnDb() {
    return colDb;
  }

  @Override
  public void start() {}

  @Override
  public void update(SimTime time) {}

  @Override
  public void stop() {}

  @Override
  protected void initialize() {

  }

  public int setWorldCell(Vec3d pos, int cellType) {
    return world.setWorldCell(pos, cellType);
  }

  public World getWorld() {
    return world;
  }

  @Override
  protected void terminate() {
    colDb.terminate();
  }
}
