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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;

import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.EntitySet;
import com.simsilica.ext.mphys.MPhysSystem;
import com.simsilica.mblock.phys.MBlockShape;
import com.simsilica.mphys.PhysicsSpace;
import com.simsilica.mphys.RigidBody;
import com.simsilica.sim.AbstractGameSystem;
import com.simsilica.sim.SimTime;

import infinity.es.input.MovementInput;
import infinity.sim.PlayerDriver;

/**
 * Manages the control drivers of entities with MovementInput components and
 * makes sure that have the latest movement input data.
 *
 * @author Paul Speed
 */
public class MovementSystem extends AbstractGameSystem {

    static Logger log = LoggerFactory.getLogger(MovementSystem.class);

    private EntityData ed;
    private MPhysSystem<MBlockShape> physics;
    private PlayerContainer players;
    private final MovementBodyInitializer initializer = new MovementBodyInitializer();
    private PhysicsSpace<EntityId, MBlockShape> space;
    private EntitySet thors, mines, gravityBombs, bursts, bombs, guns;
    private EnergySystem health;

    public MovementSystem() {
    }

    @Override
    protected void initialize() {
        ed = getSystem(EntityData.class);
        if (ed == null) {
            throw new RuntimeException(getClass().getName() + " system requires an EntityData object.");
        }
        physics = getSystem(MPhysSystem.class);
        if (physics == null) {
            throw new RuntimeException(getClass().getName() + " system requires the MPhysSystem system.");
        }

        space = physics.getPhysicsSpace();
        physics.getBodyFactory().addDynamicInitializer(initializer);

        // There are two ways that a PlayerDriver can be set on a
        // RigidBody.
        // 1) when the body is created on demand, if the entity is already
        // being managed by the 'players' EntityContainer then the above
        // initialize will just set the existing driver.
        // 2) if the rigid body already exists when the entity as added
        // to the 'players' EntityContainer then the created driver is set
        // on the body then.
        //
        // This covers all use-cases... bin becoming active before the player
        // container saw the entity, entity having its MovementInput removed/added
        // on the fly, body's bin going to sleep and getting activated again, etc..
        // All possible life cycle paths are handled by this two-prong approach.
        // ...and probably a few I haven't thought of. (One case we don't handle
        // but would be easily handled is the case where we get a new MovementInput
        // but the body is not active... we don't specifically activate it. We
        // assume in our simple demo that the 'just added' body hasn't had a chance
        // to fall asleep yet.
        //
        // This control driver handling is trickier than in some of the simpler ES
        // demos or the SiO2 bullet-char demo because the rigid bodies can be
        // dynamically
        // loaded and unloaded in mphys.
    }

    @Override
    protected void terminate() {
    }

    @Override
    public void start() {
        players = new PlayerContainer(ed);
        players.start();
    }

    @Override
    public void update(final SimTime time) {
        players.update();
    }

    @Override
    public void stop() {
        players.stop();
        players = null;
    }

    /**
     * All moving ships will be mapped to a driver. We use this to lookup the
     * drivers when we need to fire a weapon on that ship
     */
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
            log.info("addObject(" + e + ")");
            final PlayerDriver result = new PlayerDriver(e.getId(), ed, getSystem(SettingsSystem.class));

            // See if the physics engine already has a body for this entity
            final RigidBody<EntityId, MBlockShape> body = space.getBinIndex().getRigidBody(e.getId());
            log.info("existing body:" + body);
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
            final MovementInput ms = e.get(MovementInput.class);
            driver.applyMovementState(ms);
        }

        @Override
        protected void removeObject(final PlayerDriver driver, final Entity e) {
            log.info("removeObject(" + e + ")");
            // physics.setControlDriver(e.getId(), null);
        }
    }

    private class MovementBodyInitializer implements Function<RigidBody<EntityId, MBlockShape>, Void> {

        @Override
        public Void apply(final RigidBody<EntityId, MBlockShape> body) {
            // See if this is one of the ones we need to add a player driver to
            final PlayerDriver driver = players.getObject(body.id);
            log.info("MovementBodyInitializer.apply(" + body + ")  driver:" + driver);
            if (driver != null) {
                body.setControlDriver(driver);
            }
            return null;
        }
    }
}
