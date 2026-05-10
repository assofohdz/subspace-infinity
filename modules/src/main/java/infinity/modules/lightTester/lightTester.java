// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.modules.lightTester;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.event.EventBus;
import com.simsilica.mathd.Vec3d;
import infinity.events.arena.ShipEvent;
import infinity.sim.AccessLevel;
import infinity.sim.AccountManager;
import infinity.sim.ArenaManager;
import infinity.sim.BaseGameModule;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandTriFunction;
import infinity.sim.MapFactory;
import infinity.sim.PhysicsManager;
import infinity.sim.TimeManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This is a test module for the light system. It creates 4 lights in the corners
 *
 * @author Asser
 */
public class lightTester extends BaseGameModule {

  private final Pattern lightCommand = Pattern.compile("\\~lightTester\\s(\\w+)");

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

    final EntityData ed = getSystem(EntityData.class);

    MapFactory.createLight(
        ed,
        EntityId.NULL_ID,
        getPhysicsManager().getPhysics(),
        getTimeManager().getTime(),
        new Vec3d(10, 0, 10));
    MapFactory.createLight(
        ed,
        EntityId.NULL_ID,
        getPhysicsManager().getPhysics(),
        getTimeManager().getTime(),
        new Vec3d(10, 0, -10));
    MapFactory.createLight(
        ed,
        EntityId.NULL_ID,
        getPhysicsManager().getPhysics(),
        getTimeManager().getTime(),
        new Vec3d(-10, 0, 10));
    MapFactory.createLight(
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
