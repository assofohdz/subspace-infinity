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

package infinity.modules.basicTester;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.SimTime;
import infinity.sim.AccessLevel;
import infinity.sim.AccountManager;
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
      final ArenaManager arenas,
      final TimeManager time,
      final PhysicsManager physics) {
    super(chp, am, arenas, time, physics);
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
