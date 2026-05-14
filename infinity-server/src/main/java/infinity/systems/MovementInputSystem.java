/*
 * $Id$
 *
 * Copyright (c) 2020, Simsilica, LLC
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

package infinity.systems;

import com.google.common.base.Function;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.mphys.UprightDriver;
import com.simsilica.sim.SimTime;
import infinity.es.input.CharacterInput;
import infinity.es.input.MovementInput;
import infinity.settings.EngineConfigSystem;
import infinity.sim.internal.PlayerDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages control drivers for entities with {@link MovementInput} / {@link CharacterInput}; keeps movement input current. */
public class MovementInputSystem extends BaseInfinitySystem {

  static Logger log = LoggerFactory.getLogger(MovementInputSystem.class);

  private EntityData ed;
  private PlayerContainer players;
  private MobContainer mobs;
  private final MovementBodyInitializer initializer = new MovementBodyInitializer();
  private PhysicsSpace<EntityId, MBlockShape> space;
  private EngineConfigSystem engineConfigSystem;

  public MovementInputSystem() {
    // no-arg ctor — wiring happens in initialize()
  }

  public PlayerDriver getDriver(final EntityId id) {
    return players.getObject(id);
  }

  @Override
  protected void initialize() {
    this.ed = requireSystem(EntityData.class);
    final MPhysSystem physics = requireSystem(MPhysSystem.class);

    this.space = physics.getPhysicsSpace();
    physics.getBodyFactory().addDynamicInitializer(initializer);

    // Optional; falls back to EngineConfig.DEFAULTS in minimal harnesses.
    this.engineConfigSystem = getSystem(EngineConfigSystem.class);
  }

  @Override
  protected void terminate() {
    // no-op: no EntitySets held; PlayerContainer/MobContainer lifecycle is in start()/stop()
  }

  @Override
  public void start() {
    players = new PlayerContainer(ed);
    players.start();
    mobs = new MobContainer(ed);
    mobs.start();
  }

  @Override
  public void update(final SimTime time) {
    players.update();
    mobs.update();
  }

  @Override
  public void stop() {
    players.stop();
    players = null;
    mobs.stop();
    mobs = null;
  }

  private class PlayerContainer extends EntityContainer<PlayerDriver> {

    public PlayerContainer(final EntityData ed) {
      super(ed, MovementInput.class);
    }

    @Override
    public PlayerDriver[] getArray() {
      return super.getArray();
    }

    @Override
    protected PlayerDriver addObject(final Entity e) {
      log.info("addObject({})", e);

      PlayerDriver result = new PlayerDriver(e.getId(), ed, engineConfigSystem);

      // See if the physics engine already has a body for this entity
      RigidBody<EntityId, MBlockShape> body = space.getBinIndex().getRigidBody(e.getId());
      log.info("existing body:{}", body);
      if (body != null) {
        body.setControlDriver(result);
      }

      return result;
    }

    @Override
    protected void updateObject(final PlayerDriver driver, final Entity e) {
      if (log.isTraceEnabled()) {
        log.trace("updateObject(" + e + ")");
      }
      MovementInput ms = e.get(MovementInput.class);
      driver.applyMovementInput(ms);
    }

    @Override
    protected void removeObject(final PlayerDriver driver, final Entity e) {
      log.info("removeObject({})", e);
      driver.release();
    }
  }

  private class MobContainer extends EntityContainer<UprightDriver<EntityId, MBlockShape>> {

    public MobContainer(final EntityData ed) {
      super(ed, CharacterInput.class);
    }

    @Override
    public UprightDriver[] getArray() {
      return super.getArray();
    }

    @Override
    protected UprightDriver addObject(final Entity e) {

      UprightDriver<EntityId, MBlockShape> result = new UprightDriver<>();

      // See if the physics engine already has a body for this entity
      RigidBody<EntityId, MBlockShape> body = space.getBinIndex().getRigidBody(e.getId());
      log.info("existing body:{}", body);
      if (body != null) {
        body.setControlDriver(result);
      }

      return result;
    }

    @Override
    protected void updateObject(final UprightDriver driver, final Entity e) {
      if (log.isTraceEnabled()) {
        log.trace("updateObject(" + e + ")");
      }
    }

    @Override
    protected void removeObject(final UprightDriver driver, final Entity e) {
      log.info("removeObject({})", e);
    }
  }

  private class MovementBodyInitializer
      implements Function<RigidBody<EntityId, MBlockShape>, Void> {
    public Void apply(final RigidBody<EntityId, MBlockShape> body) {
      // See if this is one of the ones we need to add a player driver to
      PlayerDriver driver = players.getObject(body.id);

      if (driver != null) {
        body.setControlDriver(driver);
      }

      // or a character driver
      UprightDriver<EntityId, MBlockShape> charDriver = mobs.getObject(body.id);
      if (charDriver != null) {
        body.setControlDriver(charDriver);
      }

      return null;
    }
  }
}
