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
package wangTester;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import infinity.api.es.GravityWell;
import infinity.api.sim.AccessLevel;
import infinity.api.sim.AccountManager;
import infinity.api.sim.AdaptiveLoader;
import infinity.api.sim.ArenaManager;
import infinity.api.sim.BaseGameModule;
import infinity.api.sim.ChatHostedPoster;
import infinity.api.sim.CommandConsumer;
import infinity.api.sim.ModuleGameEntities;
import java.io.IOException;
import java.util.logging.Level;
import java.util.regex.Pattern;
import org.ini4j.Ini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Asser
 */
public class wangTester extends BaseGameModule {

    static Logger log = LoggerFactory.getLogger(wangTester.class);
    private EntityData ed;
    private final Pattern prizeTesterCommand = Pattern.compile("\\~wangTester\\s(\\w+)");

    private Ini settings;

    public wangTester(ChatHostedPoster chp, AccountManager am, AdaptiveLoader loader, ArenaManager arenas) {
        super(chp, am, loader, arenas);
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        try {
            settings = this.getLoader().loadSettings("wangTester");
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(wangTester.class.getName()).log(Level.SEVERE, null, ex);
        }

        ModuleGameEntities.createBountySpawner(this.getArenas().getDefaultArenaId(), new Vec3d(), 20, ed, settings);
        ModuleGameEntities.createWormhole(new Vec3d(), 20, 5, 500, GravityWell.PULL, new Vec3d(100, 100, 0), ed, settings);
    }

    @Override
    protected void terminate() {

    }

    @Override
    public void start() {
        //EventBus.addListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
        //
        this.getChp().registerPatternBiConsumer(prizeTesterCommand, "The command to make this wangTester do stuff is ~wangTester <command>, where <command> is the command you want to execute", new CommandConsumer(AccessLevel.PLAYER_LEVEL, (id, s) -> this.messageHandler(id, s)));

        //startGame();
    }

    @Override
    public void stop() {
        //EventBus.removeListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
        //endGame();
    }

    /**
     * Handle the message events
     *
     * @param id The entity id of the sender
     * @param s The message to handle
     */
    public void messageHandler(EntityId id, String s) {
        log.info("Received command" + s);
    }
}
