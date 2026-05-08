// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.modules.wangTester;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import infinity.es.GravityWell;
import infinity.sim.AccessLevel;
import infinity.sim.AccountManager;
import infinity.sim.ArenaManager;
import infinity.sim.BaseGameModule;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandTriFunction;
import infinity.sim.GameEntities;
import infinity.sim.PhysicsManager;
import infinity.sim.TimeManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A module for testing the wang functionality (for generating mazes)
 *
 * @author Asser
 */
public class wangTester extends BaseGameModule {

  private final Pattern prizeTesterCommand = Pattern.compile("\\~wangTester\\s(\\w+)");

  public wangTester(
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

    GameEntities.createSpawner(
        ed,
        EntityId.NULL_ID,
        getPhysicsManager().getPhysics(),
        getTimeManager().getTime(),
        new Vec3d(),
        5000,
        true,
        20);
    GameEntities.createWormhole(
        ed,
        EntityId.NULL_ID,
        getPhysicsManager().getPhysics(),
        getTimeManager().getTime(),
        new Vec3d(),
        5000,
        GravityWell.PULL,
        new Vec3d(100, 0, 100),
        10);
  }

  @Override
  protected void terminate() {}

  @Override
  public void start() {
    // EventBus.addListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
    //
    getChp()
        .registerPatternTriConsumer(
            prizeTesterCommand,
            "The command to make this wangTester do stuff is ~wangTester <command>, "
                + "where <command> is the command you want to execute",
            new CommandTriFunction<>(
                AccessLevel.PLAYER_LEVEL, this::messageHandler));

    // startGame();
  }

  @Override
  public void stop() {
    // EventBus.removeListener(this, ShipEvent.shipDestroyed,
    // ShipEvent.shipSpawned);
    // endGame();
  }

  /**
   * Handle the message events.
   *
   * @param id The entity id of the sender
   * @param s The message to handle
   */
  public String messageHandler(final EntityId id, EntityId id2, final Matcher m) {
    // log.info("Received command" + s);
    return "Received command";
  }
}
