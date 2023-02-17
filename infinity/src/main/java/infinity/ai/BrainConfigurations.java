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
public class BrainConfigurations {
  static Logger log = LoggerFactory.getLogger(BrainConfigurations.class);

  // Configs should be reusable... let's make sure.
  private static final Map<String, BrainConfiguration> configs = new HashMap<>();
  private static BrainConfiguration defaultConfig;

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

    config.setProperty("home", new Vec3d(-12.6, 64, 13.5));

    config.setGoalSelector(
        (brain) -> {
          log.info("selectGoal() failed goals:" + brain.getFailedGoals());

          Goal result = null;
          Actor actor = brain.getActor();

          double min = Double.POSITIVE_INFINITY;
          SeenObject nearest = null;
          for (SeenObject obj : actor.search("corn")) {
            // If it's too far above us then it doesn't matter
            if (obj.getPosition().y > actor.getPosition().y + 1) {
              continue;
            }

            double d = obj.getDistance();
            // log.info("   food... distance:" + d);
            if (d < min) {
              nearest = obj;
              min = d;
            }
          }
          log.info("Closest food:" + nearest + "  distance:" + min);
          if (min < 1.5) {
            result = new Eat(nearest.getId());
            if (!brain.isFailedGoal(result)) {
              return result;
            }
          }

          Vec3d home = brain.getProperty("home", null);
          if (home != null) {
            Vec3d v = home.subtract(actor.getPosition());
            log.info("Distance to home:" + v.length());
            if (v.length() > 5) {
              // Go back to within 2 meters of home
              // Note: without some concept of maximum time to goal, it's
              // possible for a single chicken to just keep pacing back
              // and forth outside the pen, neither getting close enough nor
              // getting 'blocked'.
              result = new Go(home, 2);
              if (!brain.isFailedGoal(result)) {
                return result;
              }
            }
          }

          // Random note: the fact that we currently don't do real line of
          // sight checks will make it even more apparent that a chicken will
          // get stuck trying to repeat a goal that it already tried.  It makes
          // me wonder if it's worth keeping track of the last handful of failed
          // goals to avoid trying them again over and over.
          // Even when we have real line of sight checks, the chicken might see
          // a kernel of corn through the fence that it cannot directly get to.
          // ...and chickens probably won't "path find".  So the intelligence
          // of the chicken may depend on how many failed goals it can remember.
          // Does it keep alternating between the two kernels on the other side
          // of the fence or is it smart enough to remember that both failed
          // before?  Why something failed may also be relevant... which is something
          // already mentioned elsewhere.  "Failed because I was blocked" is different
          // than "failed because something else ate the corn".  And "failed because
          // a mob blocks me" is different than "failed because a fence blocked me".

          // Just one goal for now
          return new Wander(10);
        });

    config.setDefaultStrategy(
        new Strategy<Goal>(
                (brain, goal) -> {
                  // Random angle
                  double angle = Math.random() * Math.PI * 2;
                  Vec3d dir = new Vec3d(0, 0, 0.5);
                  WalkDir walk = new WalkDir(angle, dir, 1.0, 0);

                  return new Sequence(new Say("/?/", 1, 0), walk);
                })
            .onMoved(
                (brain, obj) -> {
                  if ("chicken".equals(obj.getType())) {
                    // We don't care about other chickens
                    return false;
                  }
                  // log.info("objectMoved(" + obj + ")  velocity:" + obj.getVelocity() + "  speed:"
                  // + obj.getVelocity().length());

                  // If it's corn then see if it is a better goal than our current
                  // goal.
                  if ("corn".equals(obj.getType())) {
                    // TBD
                    return false;
                  }

                  // Else if it's a relatively fast moving object then we'll
                  // run away
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
                }));

    config.setStrategy(
        Wander.class,
        new Strategy<TimedGoal>(
                (brain, goal) -> {
                  log.info("---------- Create loop:" + goal);
                  LoopAction<TimedGoal> result =
                      new LoopAction<>(
                          goal,
                          (b, g) -> {
                            // Random duration between 2-5 seconds, not more than
                            // whatever time is remaining
                            double duration =
                                Math.min(goal.getTimeRemaining(), 2 + Math.random() * 3);

                            // Random angle
                            double angle = Math.random() * Math.PI * 2;
                            Vec3d dir = new Vec3d(0, 0, 0.5);

                            // Wander with no reassessment
                            return new WalkDir(angle, dir, duration, 0);
                          });
                  return result;
                })
            .onDone(
                (brain, goal) -> {
                  log.info(goal + " succeeded for:" + brain);
                  return null;
                })
            .onFailed(
                (brain, goal) -> {
                  log.info(goal + " failed for:" + brain);
                  return new Say("*bawk*", 1);
                })
            .onTouch(
                Collections.singletonList("corn"),
                (brain, event) -> {
                  log.info("touched by corn:" + brain.getId() + "  corn:" + event.getObject());
                  brain.newGoal(new Eat(event.getObject().getId()));
                  return true;
                })
            .onBlocked(
                (brain, blocker) -> {
                  log.info("blocked by:" + blocker);
                  brain.goalFailed();
                  // Stop moving... really would be nice to be able to abort actions
                  brain.getActor().move(new Vec3d());
                  return true;
                }));

    config.setStrategy(
        Eat.class,
        new Strategy<Eat>(
                (brain, goal) -> {

                  // Walk to the food
                  // For now we'll walk to where the food is... but
                  // we may want to actually follow the object as long
                  // as we can see it.
                  SeenObject food = brain.getActor().look(goal.getTarget());
                  // range has to at least be the chicken radius or it will
                  // never reach it... at least not until we automatically
                  // factor that in.
                  WalkTo walk = new WalkTo(food.getPosition(), 0.5, 0.35, 2.0);

                  // Eat the food... not really, we'll just delete it for now
                  Wait wait =
                      new Wait(1) {
                        protected boolean onStart(SimTime time, Brain brain) {

                          // Note: if another chicken eats it in the same update
                          // the only the entity data knows if the food was already
                          // eaten as the physics wouldn't have been updated yet.

                          // Does it still exist or did another chicken eat it
                          if (ed.getComponent(goal.getTarget(), ShapeInfo.class) == null) {
                            return false;
                          }

                          ed.removeEntity(goal.getTarget());
                          return true;
                        }
                      };

                  Say say = new Say("*yum*", 1);

                  return new Sequence(walk, wait, say);
                })
            .onDone(
                (brain, goal) -> {
                  log.info(goal + " succeeded for:" + brain);
                  return null;
                })
            .onFailed(
                (brain, goal) -> {
                  log.info(
                      goal + " failed for:" + brain + "  failed action:" + goal.getFailedAction());
                  return new Say("*BACAW*", 1);
                })
            .onBlocked(
                (brain, blocker) -> {
                  log.info("blocked by:" + blocker);
                  brain.goalFailed();
                  // Stop moving... really would be nice to be able to abort actions
                  brain.getActor().move(new Vec3d());
                  return true;
                }));

    config.setStrategy(
        Go.class,
        new Strategy<Go>(
                (brain, goal) -> {
                  Say say = new Say("?", 1);
                  WalkTo walk = new WalkTo(goal.getTarget(), 0.5, goal.getRange(), 5.0);
                  return new Sequence(say, walk);
                })
            .onDone(
                (brain, goal) -> {
                  log.info(goal + " succeeded for:" + brain);
                  return null;
                })
            .onFailed(
                (brain, goal) -> {
                  log.info(goal + " failed for:" + brain);
                  return new Say("??", 1);
                })
            .onTouch(
                Collections.singletonList("corn"),
                (brain, event) -> {
                  log.info("touched by corn:" + brain.getId() + "  corn:" + event.getObject());
                  brain.newGoal(new Eat(event.getObject().getId()));
                  return true;
                })
            .onBlocked(
                (brain, blocker) -> {
                  log.info("blocked by:" + blocker);
                  brain.goalFailed();
                  // Stop moving... really would be nice to be able to abort actions
                  brain.getActor().move(new Vec3d());
                  return true;
                }));

    config.setStrategy(
        Flee.class,
        new Strategy<Flee>(
            (brain, goal) -> {
              // For now, stupid fleeing... we really would like to more
              // dynamically flee
              SeenObject pursuer = brain.getActor().look2(goal.getPursuer());
              if (pursuer == null) {
                // The pursuer is already out of sight range
                return new Say("Phew!", 1);
              }
              Vec3d dir = brain.getActor().getPosition().subtract(pursuer.getPosition());
              dir.y = 0;
              dir.normalizeLocal();

              // If LoopAction was more flexible then we could have had
              // a reasonable flee loop... but it's currently TimedGoal specific.

              log.info(
                  "Flee:"
                      + pursuer.getPosition()
                      + "   us:"
                      + brain.getActor().getPosition()
                      + "  dir:"
                      + dir);
              return new Sequence(
                  new Say("*BAWK!*", 1, 0),
                  new WalkDir(dir, new Vec3d(0, 0, 1), 1, 0),
                  new Say("*BAWK!*", 1, 0),
                  new WalkDir(dir, new Vec3d(0, 0, 1), 1, 0),
                  new Say("*BAAAWK!*", 1, 0),
                  new WalkDir(dir, new Vec3d(0, 0, 1), 1, 0));
            }));
    return config;
  }

  public static BrainConfiguration createDog(final EntityData ed) {
    BrainConfiguration config = new BrainConfiguration();

    config.setProperty("home", new Vec3d(-17, 64, 19));

    config.setGoalSelector(
        (brain) -> {
          log.info("selectGoal() failed goals:" + brain.getFailedGoals());

          Goal result = null;
          Actor actor = brain.getActor();

          Vec3d home = brain.getProperty("home", null); // new Vec3d(-17, 64, 19);
          if (home != null) {
            Vec3d v = home.subtract(actor.getPosition());
            log.info("Distance to home:" + v.length());
            if (v.length() > 10) {
              // Go back to within 5 meters of home
              result = new Go(home, 5);
              if (!brain.isFailedGoal(result)) {
                return result;
              }
            }
          }

          // Just one goal for now
          return new Wander(10);
        });

    config.setDefaultStrategy(
        new Strategy<Goal>(
                (brain, goal) -> {
                  // Random angle
                  double angle = Math.random() * Math.PI * 2;
                  Vec3d dir = new Vec3d(0, 0, 0.5);
                  WalkDir walk = new WalkDir(angle, dir, 1.0, 0);

                  return new Sequence(new Say("/?/", 1, 0), walk);
                })
            .onMoved(
                (brain, obj) -> {
                  // log.info("objectMoved(" + obj + ")  velocity:" + obj.getVelocity() + "  speed:"
                  // + obj.getVelocity().length());

                  if ("corn".equals(obj.getType())) {
                    // We don't care about moving corn
                    return false;
                  }

                  // Else if it's a relatively fast moving object then we'll
                  // chase it... probably we could filter on size, too, to automatically
                  // miss things like corn.
                  double speed = obj.getVelocity().lengthSq();

                  // Need to implement chasing

                  return false;
                }));

    config.setStrategy(
        Wander.class,
        new Strategy<TimedGoal>(
                (brain, goal) -> {
                  log.info("---------- Create loop:" + goal);
                  LoopAction<TimedGoal> result =
                      new LoopAction<>(
                          goal,
                          (b, g) -> {
                            // Random duration between 2-5 seconds, not more than
                            // whatever time is remaining
                            double duration =
                                Math.min(goal.getTimeRemaining(), 2 + Math.random() * 3);

                            // Random angle
                            double angle = Math.random() * Math.PI * 2;
                            Vec3d dir = new Vec3d(0, 0, 0.5);

                            return new WalkDir(angle, dir, duration, 0);
                          });
                  return result;
                })
            .onDone(
                (brain, goal) -> {
                  log.info(goal + " succeeded for:" + brain);
                  return null;
                })
            .onFailed(
                (brain, goal) -> {
                  log.info(goal + " failed for:" + brain);
                  return new Say("*ruff*", 1);
                })
            .onBlocked(
                (brain, blocker) -> {
                  log.info("blocked by:" + blocker);
                  brain.goalFailed();
                  // Stop moving... really would be nice to be able to abort actions
                  brain.getActor().move(new Vec3d());
                  return true;
                }));

    config.setStrategy(
        Eat.class,
        new Strategy<Eat>(
                (brain, goal) -> {

                  // Walk to the food
                  // For now we'll walk to where the food is... but
                  // we may want to actually follow the object as long
                  // as we can see it.
                  SeenObject food = brain.getActor().look(goal.getTarget());
                  // range has to at least be the chicken radius or it will
                  // never reach it... at least not until we automatically
                  // factor that in.
                  WalkTo walk = new WalkTo(food.getPosition(), 0.5, 0.35, 2.0);
                  // return new WalkTo(new Vec3d(0, 64, 0), 0.2);

                  // Eat the food... not really, we'll just delete it for now
                  Wait wait =
                      new Wait(1) {
                        protected boolean onStart(SimTime time, Brain brain) {

                          // Note: if another chicken eats it in the same update
                          // the only the entity data knows if the food was already
                          // eaten as the physics wouldn't have been updated yet.

                          // Does it still exist or did another chicken eat it
                          if (ed.getComponent(goal.getTarget(), ShapeInfo.class) == null) {
                            return false;
                          }

                          ed.removeEntity(goal.getTarget());
                          return true;
                        }
                      };

                  Say say = new Say("*yum*", 1);

                  return new Sequence(walk, wait, say);
                })
            .onDone(
                (brain, goal) -> {
                  log.info(goal + " succeeded for:" + brain);
                  return null;
                })
            .onFailed(
                (brain, goal) -> {
                  log.info(
                      goal + " failed for:" + brain + "  failed action:" + goal.getFailedAction());
                  return new Say("*grr*", 1);
                })
            .onBlocked(
                (brain, blocker) -> {
                  log.info("blocked by:" + blocker);
                  brain.goalFailed();
                  // Stop moving... really would be nice to be able to abort actions
                  brain.getActor().move(new Vec3d());
                  return true;
                }));

    config.setStrategy(
        Go.class,
        new Strategy<Go>(
                (brain, goal) -> {
                  Say say = new Say("?", 1);
                  WalkTo walk = new WalkTo(goal.getTarget(), 0.5, goal.getRange(), 5.0);
                  return new Sequence(say, walk);
                })
            .onDone(
                (brain, goal) -> {
                  log.info(goal + " succeeded for:" + brain);
                  return null;
                })
            .onFailed(
                (brain, goal) -> {
                  log.info(goal + " failed for:" + brain);
                  return new Say("??", 1);
                })
            .onBlocked(
                (brain, blocker) -> {
                  log.info("blocked by:" + blocker);
                  brain.goalFailed();
                  // Stop moving... really would be nice to be able to abort actions
                  brain.getActor().move(new Vec3d());
                  return true;
                }));

    return config;
  }

  public static BrainConfiguration createPerson(final EntityData ed) {
    BrainConfiguration config = new BrainConfiguration();

    config.setProperty("home", new Vec3d(-15, 64, 26));

    config.setGoalSelector(
        (brain) -> {
          log.info("selectGoal() failed goals:" + brain.getFailedGoals());

          Goal result = null;
          Actor actor = brain.getActor();

          Vec3d home = brain.getProperty("home", null); // new Vec3d(-17, 64, 19);
          if (home != null) {
            Vec3d v = home.subtract(actor.getPosition());
            log.info("Distance to home:" + v.length());
            if (v.length() > 15) {
              // Go back to within 5 meters of home
              result = new Go(home, 5);
              if (!brain.isFailedGoal(result)) {
                return result;
              }
            }
          }

          // Just one goal for now
          return new Wander(10);
        });

    config.setDefaultStrategy(
        new Strategy<Goal>(
                (brain, goal) -> {
                  // Random angle
                  double angle = Math.random() * Math.PI * 2;
                  Vec3d dir = new Vec3d(0, 0, 0.5);
                  WalkDir walk = new WalkDir(angle, dir, 1.0, 0);

                  return new Sequence(new Say("/?/", 1, 0), walk);
                })
            .onMoved(
                (brain, obj) -> {
                  // log.info("objectMoved(" + obj + ")  velocity:" + obj.getVelocity() + "  speed:"
                  // + obj.getVelocity().length());

                  if ("corn".equals(obj.getType())) {
                    // We don't care about moving corn
                    return false;
                  }

                  // Else if it's a relatively fast moving object then we'll
                  // chase it... probably we could filter on size, too, to automatically
                  // miss things like corn.
                  double speed = obj.getVelocity().lengthSq();

                  // Need to implement chasing

                  return false;
                }));

    config.setStrategy(
        Wander.class,
        new Strategy<TimedGoal>(
                (brain, goal) -> {
                  log.info("---------- Create loop:" + goal);
                  LoopAction<TimedGoal> result =
                      new LoopAction<>(
                          goal,
                          (b, g) -> {
                            // Random duration between 2-5 seconds, not more than
                            // whatever time is remaining
                            double duration =
                                Math.min(goal.getTimeRemaining(), 2 + Math.random() * 3);

                            // Random angle
                            double angle = Math.random() * Math.PI * 2;
                            Vec3d dir = new Vec3d(0, 0, 0.5);

                            return new WalkDir(angle, dir, duration, 0);
                          });
                  return result;
                })
            .onDone(
                (brain, goal) -> {
                  log.info(goal + " succeeded for:" + brain);
                  return null;
                })
            .onFailed(
                (brain, goal) -> {
                  log.info(goal + " failed for:" + brain);
                  return new Say("Hmph!", 1);
                })
            .onBlocked(
                (brain, blocker) -> {
                  log.info("blocked by:" + blocker);
                  brain.goalFailed();
                  // Stop moving... really would be nice to be able to abort actions
                  brain.getActor().move(new Vec3d());
                  return true;
                }));

    config.setStrategy(
        Eat.class,
        new Strategy<Eat>(
                (brain, goal) -> {

                  // Walk to the food
                  // For now we'll walk to where the food is... but
                  // we may want to actually follow the object as long
                  // as we can see it.
                  SeenObject food = brain.getActor().look(goal.getTarget());
                  // range has to at least be the chicken radius or it will
                  // never reach it... at least not until we automatically
                  // factor that in.
                  WalkTo walk = new WalkTo(food.getPosition(), 1, 0.35, 2.0);
                  // return new WalkTo(new Vec3d(0, 64, 0), 0.2);

                  // Eat the food... not really, we'll just delete it for now
                  Wait wait =
                      new Wait(1) {
                        protected boolean onStart(SimTime time, Brain brain) {

                          // Note: if another chicken eats it in the same update
                          // the only the entity data knows if the food was already
                          // eaten as the physics wouldn't have been updated yet.

                          // Does it still exist or did another chicken eat it
                          if (ed.getComponent(goal.getTarget(), ShapeInfo.class) == null) {
                            return false;
                          }

                          ed.removeEntity(goal.getTarget());
                          return true;
                        }
                      };

                  Say say = new Say("Yum!", 1);

                  return new Sequence(walk, wait, say);
                })
            .onDone(
                (brain, goal) -> {
                  log.info(goal + " succeeded for:" + brain);
                  return null;
                })
            .onFailed(
                (brain, goal) -> {
                  log.info(
                      goal + " failed for:" + brain + "  failed action:" + goal.getFailedAction());
                  return new Say("Ugh!", 1);
                })
            .onBlocked(
                (brain, blocker) -> {
                  log.info("blocked by:" + blocker);
                  brain.goalFailed();
                  // Stop moving... really would be nice to be able to abort actions
                  brain.getActor().move(new Vec3d());
                  return true;
                }));

    config.setStrategy(
        Go.class,
        new Strategy<Go>(
                (brain, goal) -> {
                  Say say = new Say("?", 1);
                  WalkTo walk = new WalkTo(goal.getTarget(), 1, goal.getRange(), 5.0);
                  return new Sequence(say, walk);
                })
            .onDone(
                (brain, goal) -> {
                  log.info(goal + " succeeded for:" + brain);
                  return null;
                })
            .onFailed(
                (brain, goal) -> {
                  log.info(goal + " failed for:" + brain);
                  return new Say("??", 1);
                })
            .onBlocked(
                (brain, blocker) -> {
                  log.info("blocked by:" + blocker);
                  brain.goalFailed();
                  // Stop moving... really would be nice to be able to abort actions
                  brain.getActor().move(new Vec3d());
                  return true;
                }));

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
            (brain, goal) -> {
              LoopAction<TimedGoal> result =
                  new LoopAction<>(
                      goal,
                      (b, g) -> {
                        // Random duration between 2-5 seconds, not more than
                        // whatever time is remaining
                        double duration = Math.min(goal.getTimeRemaining(), 2 + Math.random() * 3);

                        // Random angle
                        double angle = Math.random() * Math.PI * 2;
                        Vec3d dir = new Vec3d(0, 0, 0.5);

                        return new WalkDir(angle, dir, duration, 0);
                      });
              return result;
            }));

    return config;
  }
}
