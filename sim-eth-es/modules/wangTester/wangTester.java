package wangTester;

import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.mathd.Vec3d;
import example.es.GravityWell;
import example.es.TileTypes;
import example.sim.AccountLevels;
import example.sim.AccountManager;
import example.sim.BaseGameModule;
import example.sim.ChatHostedPoster;
import example.sim.CommandConsumer;
import example.sim.ModuleGameEntities;
import java.util.regex.Pattern;
import org.dyn4j.geometry.Rectangle;
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

    public wangTester(Ini settings, ChatHostedPoster chp, AccountManager am) {
        super(settings, chp, am);

    }

    @Override
    protected void initialize() {
        this.ed = getSystem(EntityData.class);

        ModuleGameEntities.createMapTile("Materials/WangBlobTest.j3m", (short) 1, new Vec3d(), new Rectangle(1, 1), 0, TileTypes.WANGBLOB, ed, this.getSettings());
    }

    @Override
    protected void terminate() {

    }

    @Override
    public void start() {
        //EventBus.addListener(this, ShipEvent.shipDestroyed, ShipEvent.shipSpawned);
        //
        this.getChp().registerPatternBiConsumer(prizeTesterCommand, "The command to make this wangTester do stuff is ~wangTester <command>, where <command> is the command you want to execute", new CommandConsumer(AccountLevels.PLAYER_LEVEL, (id, s) -> this.messageHandler(id, s)));

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
