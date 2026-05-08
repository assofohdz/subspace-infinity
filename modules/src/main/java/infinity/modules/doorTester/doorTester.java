// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.modules.doorTester;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.sim.SimTime;
import infinity.sim.AccessLevel;
import infinity.sim.AccountManager;
import infinity.sim.ArenaManager;
import infinity.sim.BaseGameModule;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandTriFunction;
import infinity.sim.PhysicsManager;
import infinity.sim.TimeManager;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Test module for door-related entities.
 *
 * @author AFahrenholz
 */
public class doorTester extends BaseGameModule {

  private final Pattern basicCommand = Pattern.compile("\\~basictest\\s(\\w+)");
  private final Set<EntityId> createdEntities = new HashSet<>();
  private EntityData ed;

  public doorTester(
      final ChatHostedPoster chp,
      final AccountManager am,
      final ArenaManager arenas,
      final TimeManager time,
      final PhysicsManager physics) {
    super(chp, am, arenas, time, physics);
  }

  @Override
  protected void initialize() {
    ed = getSystem(EntityData.class, true);



  }

  @Override
  protected void terminate() {
    createdEntities.forEach(
        (id) -> {
          ed.removeEntity(id);
        });
  }

  @Override
  public void stop() {
    super.stop(); // To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void update(final SimTime time) {
    super.update(time); // To change body of generated methods, choose Tools | Templates.
  }

  @Override
  public void start() {
    super.start();

    // EventBus.addListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
    getChp()
        .registerPatternTriConsumer(
            basicCommand,
            "The command to make this basic tester do stuff is ~basic <command>, "
                + "where <command> is the command you want to execute",
            new CommandTriFunction<>(AccessLevel.PLAYER_LEVEL, this::messageHandler));
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
