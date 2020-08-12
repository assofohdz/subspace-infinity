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
package lightTester;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.ini4j.Ini;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.event.EventBus;
import com.simsilica.mathd.Vec3d;

import infinity.events.ShipEvent;
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
public class lightTester extends BaseGameModule {

    private Pattern lightCommand = Pattern.compile("\\~lightTester\\s(\\w+)");
    private EntityData ed;

    private Ini settings;

    public lightTester(ChatHostedPoster chp, AccountManager am, AdaptiveLoader loader, ArenaManager arenas,
            TimeManager time, PhysicsManager physics) {
        super(chp, am, loader, arenas, time, physics);
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        try {
            settings = this.getLoader().loadSettings("lightTester");
        } catch (IOException ex) {
            Logger.getLogger(lightTester.class.getName()).log(Level.SEVERE, null, ex);
        }

        GameEntities.createLight(ed, EntityId.NULL_ID, this.getPhysicsManager().getPhysics(),
                this.getTimeManager().getTime(), new Vec3d(10, 0, 10));
        GameEntities.createLight(ed, EntityId.NULL_ID, this.getPhysicsManager().getPhysics(),
                this.getTimeManager().getTime(), new Vec3d(10, 0, -10));
        GameEntities.createLight(ed, EntityId.NULL_ID, this.getPhysicsManager().getPhysics(),
                this.getTimeManager().getTime(), new Vec3d(-10, 0, 10));
        GameEntities.createLight(ed, EntityId.NULL_ID, this.getPhysicsManager().getPhysics(),
                this.getTimeManager().getTime(), new Vec3d(-10, 0, -10));
    }

    @Override
    protected void terminate() {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }

    @Override
    public void start() {
        EventBus.addListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
        this.getChp().registerPatternBiConsumer(lightCommand,
                "The command to make this arena1 do stuff is ~arena1 <command>, where <command> is the command you want to execute",
                new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, s) -> this.messageHandler(id, s)));
    }

    @Override
    public void stop() {
        EventBus.removeListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
    }

    private CommandConsumer messageHandler(EntityId id, String s) {
        throw new UnsupportedOperationException("Not supported yet."); // To change body of generated methods, choose
                                                                       // Tools | Templates.
    }
}
