/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package infinity.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;

import infinity.es.input.MovementInput;
import infinity.net.GameSession;
import infinity.systems.ActionSystem;
import infinity.systems.WeaponsSystem;
import infinity.systems.AvatarSystem;

/**
 *
 * @author AFahrenholz
 */
public class AvatarMovementState extends BaseAppState implements AnalogFunctionListener, StateFunctionListener {

    // private float timeSinceLastSend = 0;
    // private final float sendFrequency = 1f / 20f; // 20 times a second, every 50
    // ms

    static Logger log = LoggerFactory.getLogger(AvatarMovementState.class);
    // private MovementTarget target;
    private InputMapper inputMapper;

    // Picking up the input from the client
    private double forward;
    private double rotate;
    // private double mouse1;
    // private double mouse3;
    // private double mouse2;

    // The information that will be sent to the server
    private double speed = 1;

    private final double rotateSpeed = 1.5;
    private final Vec3d thrust = new Vec3d(); // not a direction, just 3 values
    private MovementInput movementInput;
    private final Quatd facing = new Quatd();
    private final byte flags = (byte) 0;
    private double localValue = 0;

    private InputManager inputManager;
    private GameSession session;
    // private Camera cam;

    @Override
    protected void initialize(final Application app) {

        // cam = getApplication().getCamera();

        log.debug("initialize()");

        if (inputMapper == null) {
            inputMapper = GuiGlobals.getInstance().getInputMapper();
        }

        if (inputManager == null) {
            inputManager = app.getInputManager();
        }

        // Most of the movement functions are treated as analog.
        inputMapper.addAnalogListener(this,
                // Movement:-->
                AvatarMovementFunctions.F_THRUST, AvatarMovementFunctions.F_TURN
        // <--
        );

        inputMapper.addStateListener(this,
                // Movement
                AvatarMovementFunctions.F_RUN,
                // Weapons
                AvatarMovementFunctions.F_BOMB, AvatarMovementFunctions.F_BURST, AvatarMovementFunctions.F_THOR,
                AvatarMovementFunctions.F_SHOOT, AvatarMovementFunctions.F_GRAVBOMB, AvatarMovementFunctions.F_MINE,
                // Actions
                AvatarMovementFunctions.F_REPEL, AvatarMovementFunctions.F_WARP,
                // Ships
                AvatarMovementFunctions.F_WARBIRD, AvatarMovementFunctions.F_JAVELIN, AvatarMovementFunctions.F_SPIDER,
                AvatarMovementFunctions.F_LEVI, AvatarMovementFunctions.F_TERRIER, AvatarMovementFunctions.F_WEASEL,
                AvatarMovementFunctions.F_LANC, AvatarMovementFunctions.F_SHARK);

    }

    @Override
    protected void cleanup(final Application app) {

        inputMapper.removeAnalogListener(this, AvatarMovementFunctions.F_THRUST, AvatarMovementFunctions.F_TURN);
        inputMapper.removeStateListener(this,
                // Weapons
                AvatarMovementFunctions.F_BOMB, AvatarMovementFunctions.F_BURST, AvatarMovementFunctions.F_THOR,
                AvatarMovementFunctions.F_SHOOT, AvatarMovementFunctions.F_GRAVBOMB, AvatarMovementFunctions.F_MINE,
                // Actions
                AvatarMovementFunctions.F_REPEL, AvatarMovementFunctions.F_WARP,
                // Ships
                AvatarMovementFunctions.F_WARBIRD, AvatarMovementFunctions.F_JAVELIN, AvatarMovementFunctions.F_SPIDER,
                AvatarMovementFunctions.F_LEVI, AvatarMovementFunctions.F_TERRIER, AvatarMovementFunctions.F_WEASEL,
                AvatarMovementFunctions.F_LANC, AvatarMovementFunctions.F_SHARK);
    }

    @Override
    protected void onEnable() {
        // Make sure our input group is enabled
        inputMapper.activateGroup(AvatarMovementFunctions.G_MOVEMENT);
        inputMapper.activateGroup(AvatarMovementFunctions.G_MAP);
        inputMapper.activateGroup(AvatarMovementFunctions.G_SHIPSELECTION);
        inputMapper.activateGroup(AvatarMovementFunctions.G_WEAPON);
        inputMapper.activateGroup(AvatarMovementFunctions.G_TOGGLE);
        inputMapper.activateGroup(AvatarMovementFunctions.G_TOWER);
        inputMapper.activateGroup(AvatarMovementFunctions.G_ACTION);

        session = getState(ConnectionState.class).getService(GameSessionClientService.class);
    }

    @Override
    protected void onDisable() {
        inputMapper.deactivateGroup(AvatarMovementFunctions.G_MOVEMENT);
        inputMapper.deactivateGroup(AvatarMovementFunctions.G_MAP);
        inputMapper.deactivateGroup(AvatarMovementFunctions.G_SHIPSELECTION);
        inputMapper.deactivateGroup(AvatarMovementFunctions.G_WEAPON);
        inputMapper.deactivateGroup(AvatarMovementFunctions.G_TOGGLE);
        inputMapper.deactivateGroup(AvatarMovementFunctions.G_TOWER);
        inputMapper.deactivateGroup(AvatarMovementFunctions.G_ACTION);
    }

    @Override
    public void update(final float tpf) {
        // timeSinceLastSend += tpf;

        // if (timeSinceLastSend > sendFrequency) {

        thrust.x = (float) (rotate * rotateSpeed);
        // thrust.y is left out because y is the upwards axis
        thrust.z = (float) (forward * speed); // Z is forward

        movementInput = new MovementInput(thrust, facing, flags);

        // if (thrust.x != 0.0 || thrust.y != 0.0) {

        //Send input only if we are pressing a key, or if we have just released the key
        if (localValue == 1 || localValue == 0 || localValue == -1){
            session.move(movementInput);

            //If value is 0 then we released the key, mark value so we dont send more input
            if(localValue == 0){
                localValue = -2;
            }
        }
        // }

        // timeSinceLastSend = 0;
/* 
        /*
         * if (this.entity.getId().getId() == watchedAvatar.getId().getId()) {
         *
         * Quatd cameraRotation = new
         * Quatd(getApplication().getCamera().getRotation().clone()); Vec3d
         * avatarWorldTranslation = new
         * Vec3d(model.spatial.getWorldTranslation().clone());
         *
         * gameSession.setView(cameraRotation, avatarWorldTranslation);
         *
         * Vector3f cameraWorldTranslation = new Vec3d(avatarWorldTranslation).add(0,
         * 40, 0).toVector3f();
         * getState(WorldViewState.class).setViewLocation(cameraWorldTranslation); }
         */ 
        // }
    }

    @Override
    public void valueActive(final FunctionId func, final double value, final double tpf) {
        //Set value, so we know if we have to send input. 1 and -1 is received repeatedly. 0 is received once.
        localValue = value;
        if (func == AvatarMovementFunctions.F_THRUST) {
            forward = value;

        } else if (func == AvatarMovementFunctions.F_TURN) {
            rotate = value;
        } else if (func == AvatarMovementFunctions.F_MOUSE1) {
            // mouse1 = value;
        } else if (func == AvatarMovementFunctions.F_MOUSE2) {
            // mouse2 = value;
        } else if (func == AvatarMovementFunctions.F_MOUSE3) {
            // mouse3 = value;
        }
    }

    @Override
    public void valueChanged(final FunctionId func, final InputState value, final double tpf) {
        final boolean b = value == InputState.Positive;

        if (func == AvatarMovementFunctions.F_RUN) {
            if (b) {
                speed = 2;
            } else {
                speed = 1;
            }
        }

        if (value == InputState.Off) {
            // Attack functions first:
            if (func == AvatarMovementFunctions.F_SHOOT) {
                session.attack(WeaponsSystem.GUN);
            } else if (func == AvatarMovementFunctions.F_BOMB) {
                session.attack(WeaponsSystem.BOMB);
            } else if (func == AvatarMovementFunctions.F_GRAVBOMB) {
                session.attack(WeaponsSystem.GRAVBOMB);
            } else if (func == AvatarMovementFunctions.F_MINE) {
                session.attack(WeaponsSystem.MINE);
                // <..
                // Actions-->
            } else if (func == AvatarMovementFunctions.F_THOR) {
                session.action(ActionSystem.FIRETHOR);
            } else if (func == AvatarMovementFunctions.F_REPEL) {
                session.action(ActionSystem.REPEL);
            } else if (func == AvatarMovementFunctions.F_BURST) {
                session.action(ActionSystem.FIREBURST);
            } else if (func == AvatarMovementFunctions.F_WARP) {
                session.action(ActionSystem.WARP);
            }
            // <..
            // Avatar functions:-->
            else if (func == AvatarMovementFunctions.F_WARBIRD) {
                session.avatar(AvatarSystem.WARBIRD);
            } else if (func == AvatarMovementFunctions.F_JAVELIN) {
                session.avatar(AvatarSystem.JAVELIN);
            } else if (func == AvatarMovementFunctions.F_SPIDER) {
                session.avatar(AvatarSystem.SPIDER);
            } else if (func == AvatarMovementFunctions.F_LEVI) {
                session.avatar(AvatarSystem.LEVI);
            } else if (func == AvatarMovementFunctions.F_TERRIER) {
                session.avatar(AvatarSystem.TERRIER);
            } else if (func == AvatarMovementFunctions.F_WEASEL) {
                session.avatar(AvatarSystem.WEASEL);
            } else if (func == AvatarMovementFunctions.F_LANC) {
                session.avatar(AvatarSystem.LANCASTER);
            } else if (func == AvatarMovementFunctions.F_SHARK) {
                session.avatar(AvatarSystem.SHARK);
            }
            // <..
            /*
             * for (FunctionId funcId : functionStates.keySet()) {
             * functionStates.put(funcId, Boolean.FALSE); }
             */
        }
    }

    /**
     * Sets the current movement target. The default implementation sets up a
     * CameraMovementTarget during initialization if no other target has been
     * provided.
     */
    public void setMovementTarget(@SuppressWarnings("unused") final MovementTarget target) {
        // this.target = target;
    }

    void setSession(final GameSession session) {
        this.session = session;
    }
}
