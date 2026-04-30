/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author AFahrenholz
 */
public class doorTester extends BaseGameModule {

  private final Pattern basicCommand = Pattern.compile("\\~basictest\\s(\\w+)");
  private final HashSet<EntityId> createdEntities = new HashSet<>();
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
