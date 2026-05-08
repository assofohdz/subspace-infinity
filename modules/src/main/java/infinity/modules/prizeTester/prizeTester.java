// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.modules.prizeTester;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A module for testing the prize spawner.
 *
 * @author Asser
 */
public class prizeTester extends BaseGameModule {

  static Logger log = LoggerFactory.getLogger(prizeTester.class);
  private final Pattern prizeTesterCommand = Pattern.compile("\\~prizeTester\\s(\\w+)");

  public prizeTester(
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
        new Vec3d(0, 1, 0),
        1000,
        true,
        10);
  }

  @Override
  protected void terminate() {
  }

  @Override
  public void start() {
    // EventBus.addListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
    //
    getChp()
        .registerPatternTriConsumer(
            prizeTesterCommand,
            "The command to make this prizeTester do stuff is ~prizeTester <command>, "
                + "where <command> is the command you want to execute",
            new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::messageHandler));

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
   * @param m  The message to handle
   */
  public String messageHandler(final EntityId id, EntityId id2, final Matcher m) {
    log.info("Received command{}", m);
    return "Received command" + m;
  }
}
