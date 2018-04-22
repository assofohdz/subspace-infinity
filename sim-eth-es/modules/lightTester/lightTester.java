/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package lightTester;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.event.EventBus;
import com.simsilica.mathd.Vec3d;
import example.sim.AccountLevels;
import example.sim.AccountManager;
import example.sim.BaseGameModule;
import example.sim.ChatHostedPoster;
import example.sim.CommandConsumer;
import example.sim.ModuleGameEntities;
import example.sim.events.ShipEvent;
import java.util.regex.Pattern;
import org.ini4j.Ini;

/**
 *
 * @author Asser
 */
public class lightTester extends BaseGameModule {

    private Pattern lightCommand = Pattern.compile("\\~lightTester\\s(\\w+)");
    private EntityData ed;

    public lightTester(Ini settings, ChatHostedPoster chp, AccountManager am) {
        super(settings, chp, am);
    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);
        
        ModuleGameEntities.createLight(new Vec3d(10, 10, 0), ed, this.getSettings());
        ModuleGameEntities.createLight(new Vec3d(-10, 10, 0), ed, this.getSettings());
        ModuleGameEntities.createLight(new Vec3d(10, -10, 0), ed, this.getSettings());
        ModuleGameEntities.createLight(new Vec3d(-10, -10, 0), ed, this.getSettings());
    }

    @Override
    protected void terminate() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void start() {
        EventBus.addListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
        this.getChp().registerPatternBiConsumer(lightCommand, "The command to make this arena1 do stuff is ~arena1 <command>, where <command> is the command you want to execute", new CommandConsumer(AccountLevels.PLAYER_LEVEL, (id, s) -> this.messageHandler(id, s)));
    }

    @Override
    public void stop() {
        EventBus.removeListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
    }

    private CommandConsumer messageHandler(EntityId id, String s) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
