/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.client;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.simsilica.input.MovementTarget;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.input.AnalogFunctionListener;
import com.simsilica.lemur.input.FunctionId;
import com.simsilica.lemur.input.InputMapper;
import com.simsilica.lemur.input.InputState;
import com.simsilica.lemur.input.StateFunctionListener;
import com.simsilica.mathd.Vec3d;
import infinity.net.GameSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author AFahrenholz
 */
public class AvatarMovementState extends BaseAppState
        implements AnalogFunctionListener, StateFunctionListener {

    private float timeSinceLastSend = 0;
    private final float sendFrequency = 1f / 20f; // 20 times a second, every 50 ms

    static Logger log = LoggerFactory.getLogger(AvatarMovementState.class);
    private MovementTarget target;
    private InputMapper inputMapper;

    //Picking up the input from the client
    private double forward;
    private double rotate;
    private double mouse1;
    private double mouse3;
    private double mouse2;

    //The information that will be sent to the server
    private double speed = 1;
    
    private double rotateSpeed = 1.5;
    private Vec3d thrust = new Vec3d(); // not a direction, just 3 values

    private InputManager inputManager;
    private GameSession session;

    @Override
    protected void initialize(Application app) {

        log.debug("initialize()");

        if (inputMapper == null) {
            inputMapper = GuiGlobals.getInstance().getInputMapper();
        }

        if (inputManager == null) {
            inputManager = app.getInputManager();
        }

        // Most of the movement functions are treated as analog.        
        inputMapper.addAnalogListener(this,
                //Movement:-->
                PlayerMovementFunctions.F_THRUST,
                PlayerMovementFunctions.F_TURN
                //<--
        );

        inputMapper.addStateListener(this,
                //Movement
                PlayerMovementFunctions.F_RUN,
                //Weapons
                PlayerMovementFunctions.F_BOMB,
                PlayerMovementFunctions.F_BURST,
                PlayerMovementFunctions.F_THOR,
                PlayerMovementFunctions.F_SHOOT,
                PlayerMovementFunctions.F_GRAVBOMB,
                PlayerMovementFunctions.F_MINE,
                //Actions
                PlayerMovementFunctions.F_REPEL,
                PlayerMovementFunctions.F_WARP,
                //Ships
                PlayerMovementFunctions.F_WARBIRD,
                PlayerMovementFunctions.F_JAVELIN,
                PlayerMovementFunctions.F_SPIDER,
                PlayerMovementFunctions.F_LEVI,
                PlayerMovementFunctions.F_TERRIER,
                PlayerMovementFunctions.F_WEASEL,
                PlayerMovementFunctions.F_LANC,
                PlayerMovementFunctions.F_SHARK);

    }

    @Override
    protected void cleanup(Application app) {

        inputMapper.removeAnalogListener(this,
                PlayerMovementFunctions.F_THRUST,
                PlayerMovementFunctions.F_TURN);
        inputMapper.removeStateListener(this,
                //Weapons
                PlayerMovementFunctions.F_BOMB,
                PlayerMovementFunctions.F_BURST,
                PlayerMovementFunctions.F_THOR,
                PlayerMovementFunctions.F_SHOOT,
                PlayerMovementFunctions.F_GRAVBOMB,
                PlayerMovementFunctions.F_MINE,
                //Actions
                PlayerMovementFunctions.F_REPEL,
                PlayerMovementFunctions.F_WARP,
                //Ships
                PlayerMovementFunctions.F_WARBIRD,
                PlayerMovementFunctions.F_JAVELIN,
                PlayerMovementFunctions.F_SPIDER,
                PlayerMovementFunctions.F_LEVI,
                PlayerMovementFunctions.F_TERRIER,
                PlayerMovementFunctions.F_WEASEL,
                PlayerMovementFunctions.F_LANC,
                PlayerMovementFunctions.F_SHARK);
    }

    @Override
    protected void onEnable() {
        // Make sure our input group is enabled
        inputMapper.activateGroup(PlayerMovementFunctions.G_MOVEMENT);
        inputMapper.activateGroup(PlayerMovementFunctions.G_MAP);
        inputMapper.activateGroup(PlayerMovementFunctions.G_SHIPSELECTION);
        inputMapper.activateGroup(PlayerMovementFunctions.G_WEAPON);
        inputMapper.activateGroup(PlayerMovementFunctions.G_TOGGLE);
        inputMapper.activateGroup(PlayerMovementFunctions.G_TOWER);
        inputMapper.activateGroup(PlayerMovementFunctions.G_ACTION);
    }

    @Override
    protected void onDisable() {
        inputMapper.deactivateGroup(PlayerMovementFunctions.G_MOVEMENT);
        inputMapper.deactivateGroup(PlayerMovementFunctions.G_MAP);
        inputMapper.deactivateGroup(PlayerMovementFunctions.G_SHIPSELECTION);
        inputMapper.deactivateGroup(PlayerMovementFunctions.G_WEAPON);
        inputMapper.deactivateGroup(PlayerMovementFunctions.G_TOGGLE);
        inputMapper.deactivateGroup(PlayerMovementFunctions.G_TOWER);
        inputMapper.deactivateGroup(PlayerMovementFunctions.G_ACTION);
    }

    @Override
    public void update(float tpf) {
        if (timeSinceLastSend > sendFrequency) {

            thrust.x = (float) (rotate * rotateSpeed);
            //thrust.y is left out because y is the upwards axis
            thrust.z = (float) (forward * speed);  //Z is forward

            //TODO: Figure out a way to only send when we are pressing keys
            //if (thrust.x != 0.0 || thrust.y != 0.0) {
            session.move(thrust);
            //}

            timeSinceLastSend = 0;
        }
        timeSinceLastSend += tpf;
    }

    @Override
    public void valueActive(FunctionId func, double value, double tpf) {
        if (func == PlayerMovementFunctions.F_THRUST) {
            this.forward = value;
        } else if (func == PlayerMovementFunctions.F_TURN) {
            this.rotate = value;
        } else if (func == PlayerMovementFunctions.F_MOUSE1) {
            this.mouse1 = value;
        } else if (func == PlayerMovementFunctions.F_MOUSE2) {
            this.mouse2 = value;
        } else if (func == PlayerMovementFunctions.F_MOUSE3) {
            this.mouse3 = value;
        }
    }

    @Override
    public void valueChanged(FunctionId func, InputState value, double tpf) {
        boolean b = value == InputState.Positive;
        
        if( func == PlayerMovementFunctions.F_RUN ) {
            if( b ) {
                speed = 2;
            } else {
                speed = 1;
            }
        }
        
        if (value == InputState.Off) {
            if (func == PlayerMovementFunctions.F_SHOOT) {
                session.attackGuns();
            } else if (func == PlayerMovementFunctions.F_BOMB) {
                session.attackBomb();
            } else if (func == PlayerMovementFunctions.F_GRAVBOMB) {
                session.attackGravityBomb();
            } else if (func == PlayerMovementFunctions.F_THOR) {
                session.attackThor();
            } else if (func == PlayerMovementFunctions.F_REPEL) {
                session.repel();
            } else if (func == PlayerMovementFunctions.F_MINE) {
                session.placeMine();
            } else if (func == PlayerMovementFunctions.F_BURST) {
                session.attackBurst();
            } else if (func == PlayerMovementFunctions.F_WARBIRD) {
                session.chooseShip((byte) 1);
            } else if (func == PlayerMovementFunctions.F_JAVELIN) {
                session.chooseShip((byte) 2);
            } else if (func == PlayerMovementFunctions.F_SPIDER) {
                session.chooseShip((byte) 3);
            } else if (func == PlayerMovementFunctions.F_LEVI) {
                session.chooseShip((byte) 4);
            } else if (func == PlayerMovementFunctions.F_TERRIER) {
                session.chooseShip((byte) 5);
            } else if (func == PlayerMovementFunctions.F_WEASEL) {
                session.chooseShip((byte) 6);
            } else if (func == PlayerMovementFunctions.F_LANC) {
                session.chooseShip((byte) 7);
            } else if (func == PlayerMovementFunctions.F_SHARK) {
                session.chooseShip((byte) 8);
            } else if (func == PlayerMovementFunctions.F_WARP) {
                session.warp();
            }
            /*
            for (FunctionId funcId : functionStates.keySet()) {
                functionStates.put(funcId, Boolean.FALSE);
            }*/
        }
    }

    /**
     * Sets the current movement target. The default implementation sets up a
     * CameraMovementTarget during initialization if no other target has been
     * provided.
     */
    public void setMovementTarget(MovementTarget target) {
        this.target = target;
    }

    void setSession(GameSession session) {
        this.session = session;
    }
}
