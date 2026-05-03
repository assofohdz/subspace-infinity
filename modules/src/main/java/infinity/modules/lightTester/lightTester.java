// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.modules.lightTester;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.event.EventBus;
import com.simsilica.mathd.Vec3d;
import infinity.events.arena.ShipEvent;
import infinity.modules.prizeTester.prizeTester;
import infinity.sim.AccessLevel;
import infinity.sim.AccountManager;
import infinity.sim.ArenaManager;
import infinity.sim.BaseGameModule;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandTriFunction;
import infinity.sim.GameEntities;
import infinity.sim.PhysicsManager;
import infinity.sim.TimeManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.ini4j.Ini;

/**
 * This is a test module for the light system. It creates 4 lights in the corners
 *
 * @author Asser
 */
public class lightTester extends BaseGameModule {

  private final Pattern lightCommand = Pattern.compile("\\~lightTester\\s(\\w+)");
  private EntityData ed;

  @SuppressWarnings("unused")
  private Ini settings;

  public lightTester(
      final ChatHostedPoster chp,
      final AccountManager am,
      final ArenaManager arenas,
      final TimeManager time,
      final PhysicsManager physics) {
    super(chp, am, arenas, time, physics);
  }

  @Override
  protected void initialize() {

    ed = getSystem(EntityData.class);

    settings = new Ini();
    try {
      InputStream is =
          prizeTester.class.getResourceAsStream(this.getClass().getSimpleName() + ".ini");
      settings = new Ini(is);
    } catch (final IOException ex) {
      java.util.logging.Logger.getLogger(prizeTester.class.getName()).log(Level.SEVERE, null, ex);
    }

    GameEntities.createLight(
        ed,
        EntityId.NULL_ID,
        getPhysicsManager().getPhysics(),
        getTimeManager().getTime(),
        new Vec3d(10, 0, 10));
    GameEntities.createLight(
        ed,
        EntityId.NULL_ID,
        getPhysicsManager().getPhysics(),
        getTimeManager().getTime(),
        new Vec3d(10, 0, -10));
    GameEntities.createLight(
        ed,
        EntityId.NULL_ID,
        getPhysicsManager().getPhysics(),
        getTimeManager().getTime(),
        new Vec3d(-10, 0, 10));
    GameEntities.createLight(
        ed,
        EntityId.NULL_ID,
        getPhysicsManager().getPhysics(),
        getTimeManager().getTime(),
        new Vec3d(-10, 0, -10));
  }

  @Override
  protected void terminate() {
    throw new UnsupportedOperationException(
        "Not supported yet."); // To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void start() {
    EventBus.addListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
    getChp()
        .registerPatternTriConsumer(
            lightCommand,
            "The command to make this arena1 do stuff is ~arena1 <command>, "
                + "where <command> is the command you want to execute",
            new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::messageHandler));
  }

  @Override
  public void stop() {
    EventBus.removeListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
  }

  @SuppressWarnings("PMD.UnusedFormalParameter") // CommandTriFunction signature
  private String messageHandler(
      EntityId id,
      EntityId id2,
      Matcher matcher) {
    throw new UnsupportedOperationException(
        "Not supported yet."); // To change body of generated methods, choose
    // Tools | Templates.
  }
}
