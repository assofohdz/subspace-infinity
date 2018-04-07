/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package warpTester;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import example.es.GravityWell;
import example.sim.AccountLevels;
import example.sim.AccountManager;
import example.sim.BaseGameModule;
import example.sim.ChatHostedPoster;
import example.sim.CommandConsumer;
import example.sim.ModuleGameEntities;
import java.util.regex.Pattern;
import org.ini4j.Ini;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Asser
 */
public class warpTester extends BaseGameModule {

    static Logger log = LoggerFactory.getLogger(warpTester.class);
    private EntityData ed;
    private final Pattern prizeTesterCommand = Pattern.compile("\\~warpTester\\s(\\w+)");

    public warpTester(Ini settings, ChatHostedPoster chp, AccountManager am) {
        super(settings, chp, am);

    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        
        ModuleGameEntities.createBountySpawner(new Vec3d(0, 0, 0), 13, ed, this.getSettings());
        
        ModuleGameEntities.createWormhole(new Vec3d(-7,7,0), 5, 5, 5000, GravityWell.PULL, new Vec3d(7,7,0), ed, this.getSettings());
        ModuleGameEntities.createOver5(new Vec3d(7,7,0), 5, 5000, GravityWell.PUSH, ed, this.getSettings());
        
        
        ModuleGameEntities.createWormhole(new Vec3d(7,-7,0), 5, 5, 5000, GravityWell.PULL, new Vec3d(-7,-7,0), ed, this.getSettings());
        ModuleGameEntities.createOver5(new Vec3d(-7,-7,0), 5, 5000, GravityWell.PUSH, ed, this.getSettings());
    }

    @Override
    protected void terminate() {

    }

    @Override
    public void start() {
        //EventBus.addListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
        //
        this.getChp().registerPatternBiConsumer(prizeTesterCommand, "The command to make this warpTester do stuff is ~warpTester <command>, where <command> is the command you want to execute", new CommandConsumer(AccountLevels.PLAYER_LEVEL, (id, s) -> this.messageHandler(id, s)));

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
        log.info("Received command"+s);
    }
}
