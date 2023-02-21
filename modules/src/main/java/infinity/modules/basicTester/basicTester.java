/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package infinity.modules.basicTester;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.SimTime;
import infinity.sim.AccessLevel;
import infinity.sim.AccountManager;
import infinity.sim.AdaptiveLoader;
import infinity.sim.ArenaManager;
import infinity.sim.BaseGameModule;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandTriFunction;
import infinity.sim.GameEntities;
import infinity.sim.PhysicsManager;
import infinity.sim.TimeManager;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author AFahrenholz
 */
public class basicTester extends BaseGameModule {

  private final Pattern basicCommand = Pattern.compile("\\~basictest\\s(\\w+)");
  private final HashSet<EntityId> createdEntities = new HashSet<>();
  private EntityData ed;

  public basicTester(
      final ChatHostedPoster chp,
      final AccountManager am,
      final AdaptiveLoader loader,
      final ArenaManager arenas,
      final TimeManager time,
      final PhysicsManager physics) {
    super(chp, am, loader, arenas, time, physics);
  }

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class, true);

    // Test the smallest asteroids
    createdEntities.add(
        GameEntities.createAsteroidSmall(
            ed,
            EntityId.NULL_ID,
            getPhysicsManager().getPhysics(),
            getTimeManager().getTime(),
            new Vec3d(10, 0, 10),
            1));
    createdEntities.add(
        GameEntities.createAsteroidSmall(
            ed,
            EntityId.NULL_ID,
            getPhysicsManager().getPhysics(),
            getTimeManager().getTime(),
            new Vec3d(10, 0, -10),
            1));
    createdEntities.add(
        GameEntities.createAsteroidSmall(
            ed,
            EntityId.NULL_ID,
            getPhysicsManager().getPhysics(),
            getTimeManager().getTime(),
            new Vec3d(-10, 0, 10),
            1));
    createdEntities.add(
        GameEntities.createAsteroidSmall(
            ed,
            EntityId.NULL_ID,
            getPhysicsManager().getPhysics(),
            getTimeManager().getTime(),
            new Vec3d(-10, 0, -10),
            1));

    // Test the medium asteroids
    createdEntities.add(
        GameEntities.createAsteroidMedium(
            ed,
            EntityId.NULL_ID,
            getPhysicsManager().getPhysics(),
            getTimeManager().getTime(),
            new Vec3d(20, 0, 20),
            1));
    createdEntities.add(
        GameEntities.createAsteroidMedium(
            ed,
            EntityId.NULL_ID,
            getPhysicsManager().getPhysics(),
            getTimeManager().getTime(),
            new Vec3d(20, 0, -20),
            1));
    createdEntities.add(
        GameEntities.createAsteroidMedium(
            ed,
            EntityId.NULL_ID,
            getPhysicsManager().getPhysics(),
            getTimeManager().getTime(),
            new Vec3d(-20, 0, 20),
            1));
    createdEntities.add(
        GameEntities.createAsteroidMedium(
            ed,
            EntityId.NULL_ID,
            getPhysicsManager().getPhysics(),
            getTimeManager().getTime(),
            new Vec3d(-20, 0, -20),
            1));
  }

  @Override
  protected void terminate() {
    createdEntities.forEach(id -> ed.removeEntity(id));
  }

  @Override
  public void stop() {
    // Auto-generated method stub
  }

  @Override
  public void update(final SimTime time) {
    // Auto-generated method stub
  }

  @Override
  public void start() {

    // EventBus.addListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
    getChp()
        .registerPatternTriConsumer(
            basicCommand,
            "The command to make this basic tester do stuff is ~basic <command>, where <command> is the command you want to execute",
            new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::messageHandler));
  }

  private String messageHandler(
      EntityId id,
      EntityId id2,
      Matcher matcher) {
    throw new UnsupportedOperationException(
        "Not supported yet."); // To change body of generated methods, choose
    // Tools | Templates.
  }
}
