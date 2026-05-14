/*
 * $Id$
 *
 * Copyright (c) 2021, Simsilica, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.ai;

import com.simsilica.es.EntityData;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.mathd.Vec3d;
import com.simsilica.sim.SimTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sort of a temporary class to hold our brain config prefabs for this demo.
 *
 * @author Paul Speed
 */
public final class BrainConfigurations {
  static Logger log = LoggerFactory.getLogger(BrainConfigurations.class);

  // Configs should be reusable... let's make sure.
  private static final Map<String, BrainConfiguration> configs = new HashMap<>();
  private static BrainConfiguration defaultConfig;

  private BrainConfigurations() {
    // utility class
  }

  private static final String HOME = "home";
  private static final String CORN = "corn";
  private static final String LOG_GOAL_SUCCEEDED = "{} succeeded for:{}";
  private static final String LOG_GOAL_FAILED = "{} failed for:{}";
  private static final String LOG_SELECT_GOAL_FAILED = "selectGoal() failed goals:{}";
  private static final String LOG_CREATE_LOOP = "---------- Create loop:{}";
  private static final String LOG_GOAL_FAILED_WITH_ACTION = "{} failed for:{}  failed action:{}";
  private static final String LOG_BLOCKED_BY = "blocked by:{}";

  public static void initialize(EntityData ed) {
    configs.put("mob", createPerson(ed));
    defaultConfig = createDummy(ed);
  }

  public static BrainConfiguration getConfig(String name) {
    BrainConfiguration config = configs.get(name);
    return config != null ? config : defaultConfig;
  }

  public static BrainConfiguration createChicken(final EntityData ed) {
    BrainConfiguration config = new BrainConfiguration();
    config.setProperty(HOME, new Vec3d(-12.6, 64, 13.5));
    wireChickenGoalSelector(config);
    wireChickenDefaultStrategy(config);
    wireChickenWander(config);
    wireMobEat(config, ed, 0.5, "*yum*", "*BACAW*");
    wireChickenGo(config);
    wireChickenFlee(config);
    return config;
  }

  /** Chicken goal-selector: prefer nearby corn, else go home, else wander. */
  private static void wireChickenGoalSelector(final BrainConfiguration config) {
    config.setGoalSelector(
        (brain) -> {
          if (log.isInfoEnabled()) {
            log.info(LOG_SELECT_GOAL_FAILED, brain.getFailedGoals());
          }
          Actor actor = brain.getActor();
          Goal eat = pickNearestCornGoal(brain, actor);
          if (eat != null) {
            return eat;
          }
          Goal goHome = pickGoHomeGoal(brain, actor, 5.0, 2.0);
          if (goHome != null) {
            return goHome;
          }
          // Random note: see history for the path-finding / failed-goal
          // memory discussion that used to live here.
          return new Wander(10);
        });
  }

  /** Search for the nearest corn within reach; return an Eat goal or null. */
  private static Goal pickNearestCornGoal(final Brain brain, final Actor actor) {
    double min = Double.POSITIVE_INFINITY;
    SeenObject nearest = null;
    for (SeenObject obj : actor.search(CORN)) {
      // If it's too far above us then it doesn't matter
      if (obj.getPosition().y > actor.getPosition().y + 1) {
        continue;
      }
      double d = obj.getDistance();
      if (d < min) {
        nearest = obj;
        min = d;
      }
    }
    log.info("Closest food:{}  distance:{}", nearest, min);
    if (min < 1.5) {
      Goal eat = new Eat(nearest.getId());
      if (!brain.isFailedGoal(eat)) {
        return eat;
      }
    }
    return null;
  }

  /**
   * If we're more than {@code triggerDistance} from "home", return a Go-home
   * goal targeting within {@code arrivalRange} of home; else null.
   */
  private static Goal pickGoHomeGoal(
      final Brain brain, final Actor actor, final double triggerDistance, final double arrivalRange) {
    Vec3d home = brain.getProperty(HOME, null);
    if (home == null) {
      return null;
    }
    Vec3d v = home.subtract(actor.getPosition());
    if (log.isInfoEnabled()) {
      log.info("Distance to home:{}", v.length());
    }
    if (v.length() <= triggerDistance) {
      return null;
    }
    Goal goHome = new Go(home, arrivalRange);
    return brain.isFailedGoal(goHome) ? null : goHome;
  }

  /** Default chicken strategy: wander a random direction; flee fast-moving non-chicken objects. */
  private static void wireChickenDefaultStrategy(final BrainConfiguration config) {
    config.setDefaultStrategy(
        new Strategy<Goal>(
                (brain, goal) -> {
                  double angle = Math.random() * Math.PI * 2;
                  Vec3d dir = new Vec3d(0, 0, 0.5);
                  WalkDir walk = new WalkDir(angle, dir, 1.0, 0);
                  return new Sequence(new Say("/?/", 1, 0), walk);
                })
            .onMoved(BrainConfigurations::handleChickenMovedTrigger));
  }

  /** Chicken's onMoved trigger logic: ignore other chickens / corn; flee fast threats. */
  @SuppressWarnings("PMD.UnusedPrivateMethod") // referenced via BrainConfigurations::handleChickenMovedTrigger
  private static boolean handleChickenMovedTrigger(final Brain brain, final SeenObject obj) {
    if ("chicken".equals(obj.getType())) {
      return false;
    }
    if (CORN.equals(obj.getType())) {
      // TBD
      return false;
    }
    double speed = obj.getVelocity().lengthSq();
    if (speed > 1) {
      // If we are already fleeing then we're in a panic and wouldn't
      // notice a new danger... at least for now.
      if (!(brain.getCurrentGoal() instanceof Flee)) {
        brain.newGoal(new Flee(obj.getId()));
        return true;
      }
    }
    return false;
  }

  /** Chicken's Wander strategy: loop random walk + corn-touch promotion. */
  private static void wireChickenWander(final BrainConfiguration config) {
    config.setStrategy(
        Wander.class,
        buildLoopWanderStrategy("*bawk*")
            .onTouch(Collections.singletonList(CORN),
                BrainConfigurations::onCornTouchedPromoteToEat));
  }

  /** Chicken's Go strategy: walk to a target + corn-touch promotion. */
  private static void wireChickenGo(final BrainConfiguration config) {
    config.setStrategy(
        Go.class,
        buildGoStrategy(0.5)
            .onTouch(Collections.singletonList(CORN),
                BrainConfigurations::onCornTouchedPromoteToEat));
  }

  /** Chicken's Flee strategy: walk away from the pursuer with three BAWK steps. */
  private static void wireChickenFlee(final BrainConfiguration config) {
    config.setStrategy(
        Flee.class,
        new Strategy<Flee>(
            (brain, goal) -> {
              SeenObject pursuer = brain.getActor().look2(goal.getPursuer());
              if (pursuer == null) {
                return new Say("Phew!", 1);
              }
              Vec3d dir = brain.getActor().getPosition().subtract(pursuer.getPosition());
              dir.y = 0;
              dir.normalizeLocal();
              if (log.isInfoEnabled()) {
                log.info("Flee:{}   us:{}  dir:{}",
                    pursuer.getPosition(), brain.getActor().getPosition(), dir);
              }
              return new Sequence(
                  new Say("*BAWK!*", 1, 0),
                  new WalkDir(dir, new Vec3d(0, 0, 1), 1, 0),
                  new Say("*BAWK!*", 1, 0),
                  new WalkDir(dir, new Vec3d(0, 0, 1), 1, 0),
                  new Say("*BAAAWK!*", 1, 0),
                  new WalkDir(dir, new Vec3d(0, 0, 1), 1, 0));
            }));
  }

  /** Shared trigger: when an active goal is touched by corn, set a new Eat goal. */
  @SuppressWarnings("PMD.UnusedPrivateMethod") // referenced via BrainConfigurations::onCornTouchedPromoteToEat
  private static boolean onCornTouchedPromoteToEat(final Brain brain, final TouchEvent event) {
    if (log.isInfoEnabled()) {
      log.info("touched by corn:{}  corn:{}", brain.getId(), event.getObject());
    }
    brain.newGoal(new Eat(event.getObject().getId()));
    return true;
  }

  /** Shared trigger: when an active goal is blocked, log + fail + halt the actor. */
  @SuppressWarnings("PMD.UnusedPrivateMethod") // referenced via BrainConfigurations::stopOnBlocked
  private static boolean stopOnBlocked(final Brain brain, final Object blocker) {
    log.info(LOG_BLOCKED_BY, blocker);
    brain.goalFailed();
    // Stop moving... really would be nice to be able to abort actions
    brain.getActor().move(new Vec3d());
    return true;
  }

  /** Shared LoopAction-based Wander strategy; failSay parameterises the onFailed bawk/ruff/Hmph reaction. */
  private static Strategy<TimedGoal> buildLoopWanderStrategy(final String failSay) {
    return new Strategy<TimedGoal>(
            (brain, goal) -> {
              log.info(LOG_CREATE_LOOP, goal);
              return new LoopAction<>(
                  goal,
                  (b, g) -> {
                    final double duration =
                        Math.min(goal.getTimeRemaining(), 2 + Math.random() * 3);
                    final double angle = Math.random() * Math.PI * 2;
                    final Vec3d dir = new Vec3d(0, 0, 0.5);
                    return new WalkDir(angle, dir, duration, 0);
                  });
            })
        .onDone((brain, goal) -> {
              log.info(LOG_GOAL_SUCCEEDED, goal, brain);
              return null;
            })
        .onFailed((brain, goal) -> {
              log.info(LOG_GOAL_FAILED, goal, brain);
              return new Say(failSay, 1);
            })
        .onBlocked(BrainConfigurations::stopOnBlocked);
  }

  /** Shared Go strategy; walkSpeed parameterises the WalkTo speed. */
  private static Strategy<Go> buildGoStrategy(final double walkSpeed) {
    return new Strategy<Go>(
            (brain, goal) -> {
              final Say say = new Say("?", 1);
              final WalkTo walk = new WalkTo(goal.getTarget(), walkSpeed, goal.getRange(), 5.0);
              return new Sequence(say, walk);
            })
        .onDone((brain, goal) -> {
              log.info(LOG_GOAL_SUCCEEDED, goal, brain);
              return null;
            })
        .onFailed((brain, goal) -> {
              log.info(LOG_GOAL_FAILED, goal, brain);
              return new Say("??", 1);
            })
        .onBlocked(BrainConfigurations::stopOnBlocked);
  }

  /** Shared goal-selector for non-chicken mobs: go-home if past triggerDistance, else wander. */
  private static void wireGenericGoalSelector(
      final BrainConfiguration config,
      final double triggerDistance,
      final double arrivalRange) {
    config.setGoalSelector(
        (brain) -> {
          if (log.isInfoEnabled()) {
            log.info(LOG_SELECT_GOAL_FAILED, brain.getFailedGoals());
          }
          final Goal goHome = pickGoHomeGoal(brain, brain.getActor(), triggerDistance, arrivalRange);
          return goHome != null ? goHome : new Wander(10);
        });
  }

  /** Shared default strategy for non-chicken mobs: random walk; ignore corn / TODO chase fast threats. */
  private static void wireGenericDefaultStrategyCornSkip(final BrainConfiguration config) {
    config.setDefaultStrategy(
        new Strategy<Goal>(
                (brain, goal) -> {
                  final double angle = Math.random() * Math.PI * 2;
                  final Vec3d dir = new Vec3d(0, 0, 0.5);
                  final WalkDir walk = new WalkDir(angle, dir, 1.0, 0);
                  return new Sequence(new Say("/?/", 1, 0), walk);
                })
            .onMoved(
                (brain, obj) -> {
                  if (CORN.equals(obj.getType())) {
                    return false;
                  }
                  // TODO chase fast-moving objects (filter by size to skip corn).
                  return false;
                }));
  }

  /** Shared Eat strategy: walk to food at walkSpeed, eat (delete entity), say yumSay; failSay on fail. */
  private static void wireMobEat(
      final BrainConfiguration config,
      final EntityData ed,
      final double walkSpeed,
      final String yumSay,
      final String failSay) {
    config.setStrategy(
        Eat.class,
        new Strategy<Eat>(
                (brain, goal) -> {
                  final SeenObject food = brain.getActor().look(goal.getTarget());
                  final WalkTo walk = new WalkTo(food.getPosition(), walkSpeed, 0.35, 2.0);
                  final Wait wait =
                      new Wait(1) {
                        @Override
                        protected boolean onStart(final SimTime time, final Brain brain) {
                          // Did another mob eat it in the same update?
                          if (ed.getComponent(goal.getTarget(), ShapeInfo.class) == null) {
                            return false;
                          }
                          ed.removeEntity(goal.getTarget());
                          return true;
                        }
                      };
                  final Say say = new Say(yumSay, 1);
                  return new Sequence(walk, wait, say);
                })
            .onDone((brain, goal) -> {
                  log.info(LOG_GOAL_SUCCEEDED, goal, brain);
                  return null;
                })
            .onFailed((brain, goal) -> {
                  if (log.isInfoEnabled()) {
                    log.info(LOG_GOAL_FAILED_WITH_ACTION,
                        goal, brain, goal.getFailedAction());
                  }
                  return new Say(failSay, 1);
                })
            .onBlocked(BrainConfigurations::stopOnBlocked));
  }

  public static BrainConfiguration createDog(final EntityData ed) {
    final BrainConfiguration config = new BrainConfiguration();
    config.setProperty(HOME, new Vec3d(-17, 64, 19));
    wireGenericGoalSelector(config, 10.0, 5.0);
    wireGenericDefaultStrategyCornSkip(config);
    config.setStrategy(Wander.class, buildLoopWanderStrategy("*ruff*"));
    wireMobEat(config, ed, 0.5, "*yum*", "*grr*");
    config.setStrategy(Go.class, buildGoStrategy(0.5));
    return config;
  }

  public static BrainConfiguration createPerson(final EntityData ed) {
    final BrainConfiguration config = new BrainConfiguration();
    config.setProperty(HOME, new Vec3d(-15, 64, 26));
    wireGenericGoalSelector(config, 15.0, 5.0);
    wireGenericDefaultStrategyCornSkip(config);
    config.setStrategy(Wander.class, buildLoopWanderStrategy("Hmph!"));
    wireMobEat(config, ed, 1, "Yum!", "Ugh!");
    config.setStrategy(Go.class, buildGoStrategy(1));
    return config;
  }

  public static BrainConfiguration createDummy(final EntityData ed) {
    BrainConfiguration config = new BrainConfiguration();

    config.setGoalSelector(
        (brain) -> {
          return new Wander(10);
        });

    config.setStrategy(
        Wander.class,
        new Strategy<TimedGoal>(
            (brain, goal) -> new LoopAction<>(
                goal,
                (b, g) -> {
                  // Random duration between 2-5 seconds, not more than
                  // whatever time is remaining
                  double duration = Math.min(goal.getTimeRemaining(), 2 + Math.random() * 3);

                  // Random angle
                  double angle = Math.random() * Math.PI * 2;
                  Vec3d dir = new Vec3d(0, 0, 0.5);

                  return new WalkDir(angle, dir, duration, 0);
                })));

    return config;
  }
}
