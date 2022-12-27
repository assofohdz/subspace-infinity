/*
 * Copyright (c) 2018, Asser Fahrenholz
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

package warpTester;

import java.io.IOException;
import java.util.logging.Level;
import java.util.regex.Pattern;

import org.ini4j.Ini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;

import infinity.es.GravityWell;
import infinity.sim.AccessLevel;
import infinity.sim.AccountManager;
import infinity.sim.AdaptiveLoader;
import infinity.sim.ArenaManager;
import infinity.sim.BaseGameModule;
import infinity.sim.ChatHostedPoster;
import infinity.sim.CommandConsumer;
import infinity.sim.GameEntities;
import infinity.sim.PhysicsManager;
import infinity.sim.TimeManager;

/**
 *
 * @author Asser
 */
public class warpTester extends BaseGameModule {

    static Logger log = LoggerFactory.getLogger(warpTester.class);
    private EntityData ed;
    private final Pattern warpTesterCommand = Pattern.compile("\\~warpTester\\s(\\w+)");

    @SuppressWarnings("unused")
    private Ini settings;

    public warpTester(final ChatHostedPoster chp, final AccountManager am, final AdaptiveLoader loader,
            final ArenaManager arenas, final TimeManager time, final PhysicsManager physics) {
        super(chp, am, loader, arenas, time, physics);
    }

    @Override
    protected void initialize() {
        ed = getSystem(EntityData.class);

        try {
            settings = getLoader().loadSettings("warpTester");
        } catch (final IOException ex) {
            java.util.logging.Logger.getLogger(warpTester.class.getName()).log(Level.SEVERE, null, ex);
        }

        GameEntities.createPrizeSpawner(ed, EntityId.NULL_ID, getPhysicsManager().getPhysics(),
                getTimeManager().getTime(), new Vec3d(), 13);

        GameEntities.createWormhole(ed, EntityId.NULL_ID, getPhysicsManager().getPhysics(), getTimeManager().getTime(),
                new Vec3d(-7, 0, 7), 5, 5, 5000, GravityWell.PULL, new Vec3d(7, 0, 7));
        GameEntities.createAsteroidLarge(ed, EntityId.NULL_ID, getPhysicsManager().getPhysics(), getTimeManager().getTime(),
                new Vec3d(7, 7, 0)
                //, 5, 5000, GravityWell.PUSH
                );

        GameEntities.createWormhole(ed, EntityId.NULL_ID, getPhysicsManager().getPhysics(), getTimeManager().getTime(),
                new Vec3d(7, 0, -7), 5, 5, 5000, GravityWell.PULL, new Vec3d(-7, 0, -7));
        GameEntities.createAsteroidLarge(ed, EntityId.NULL_ID, getPhysicsManager().getPhysics(), getTimeManager().getTime(),
                new Vec3d(-7, -7, 0)
                //, 5, 5000, GravityWell.PUSH
                );
    }

    @Override
    protected void terminate() {
        return;
    }

    @Override
    public void start() {
        // EventBus.addListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
        //
        getChp().registerPatternBiConsumer(warpTesterCommand,
                "The command to make this warpTester do stuff is ~warpTester <command>, where <command> is the command you want to execute",
                new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, s) -> messageHandler(id, s)));

        // startGame();
    }

    @Override
    public void stop() {
        // EventBus.removeListener(this, ShipEvent.shipDestroyed,
        // ShipEvent.shipSpawned);
        // endGame();
    }

    /**
     * Handle the message events
     *
     * @param id The entity id of the sender
     * @param s  The message to handle
     */
    public void messageHandler(final EntityId id, final String s) {
        log.info("Received command" + s);
    }
}
