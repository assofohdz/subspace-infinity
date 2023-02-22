/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package infinity.client;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.input.InputManager;
import com.jme3.math.Vector3f;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.core.VersionedHolder;
import com.simsilica.lemur.core.VersionedReference;
import com.simsilica.lemur.input.AnalogFunctionListener;
import com.simsilica.lemur.input.FunctionId;
import com.simsilica.lemur.input.InputMapper;
import com.simsilica.lemur.input.InputState;
import com.simsilica.lemur.input.StateFunctionListener;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.state.BlackboardState;
import com.simsilica.state.DebugHudState;
import infinity.es.input.MovementInput;
import infinity.net.GameSession;
import infinity.systems.ActionSystem;
import infinity.systems.AvatarSystem;
import infinity.systems.WeaponsSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This state manages the movement of the local avatar. It is responsible for updating the avatar's
 * position and rotation based on the input state.
 *
 * @author AFahrenholz
 */
public class AvatarMovementState extends BaseAppState
    implements StateFunctionListener, AnalogFunctionListener {

  private static final double ROTATESPEED = 1.5;
  private static final byte FLAGS = (byte) 0;
  private static final double UPDATE_POSITION_FREQUENCY =
      1000000000L / (double) 5; // 5 times a second, every 200 ms
  static Logger log = LoggerFactory.getLogger(AvatarMovementState.class);
  private final Vec3d thrust = new Vec3d(); // not a direction, just 3 values
  private final Quatd facing = new Quatd();
  private final Vec3d lastPosition = new Vec3d();
  private final Vec3d position = new Vec3d();
  private final VersionedHolder<Vec3d> posHolder = new VersionedHolder<>(position);
  double speedAverage = 0;
  long lastSpeedTime = 0;
  private InputMapper inputMapper;
  // Picking up the input from the client
  private double speed = 1;
  private InputManager inputManager;
  private GameSession session;
  private long lastPositionUpdate;
  private VersionedHolder<String> positionDisplay;
  private VersionedHolder<String> speedDisplay;
  private boolean move;

  @Override
  protected void initialize(final Application app) {

    BlackboardState blackboard = getState(BlackboardState.class, true);
    blackboard.set("position", posHolder);

    log.debug("initialize()");

    if (inputMapper == null) {
      inputMapper = GuiGlobals.getInstance().getInputMapper();
    }

    if (inputManager == null) {
      inputManager = app.getInputManager();
    }

    inputMapper.addDelegate(AvatarMovementFunctions.F_TURN, this, "rotatePressed", true);
    inputMapper.addDelegate(AvatarMovementFunctions.F_THRUST, this, "thrustPressed", true);

    // We use analog listeners when we want to continuously receive the
    // analog value of a function.  This is useful for things like
    // attacks.
    inputMapper.addAnalogListener(
        this,
        AvatarMovementFunctions.F_BOMB,
        AvatarMovementFunctions.F_BURST,
        AvatarMovementFunctions.F_THOR,
        AvatarMovementFunctions.F_SHOOT,
        AvatarMovementFunctions.F_GRAVBOMB,
        AvatarMovementFunctions.F_MINE,
        AvatarMovementFunctions.F_REPEL);

    // We use statelisteners when we only want to know when a function
    // is pressed or released.  We don't care about the analog value.
    inputMapper.addStateListener(
        this,
        AvatarMovementFunctions.F_RUN,
        AvatarMovementFunctions.F_WARBIRD,
        AvatarMovementFunctions.F_JAVELIN,
        AvatarMovementFunctions.F_SPIDER,
        AvatarMovementFunctions.F_LEVI,
        AvatarMovementFunctions.F_TERRIER,
        AvatarMovementFunctions.F_WEASEL,
        AvatarMovementFunctions.F_LANC,
        AvatarMovementFunctions.F_SHARK,
        AvatarMovementFunctions.F_WARP);

    if (getState(DebugHudState.class) != null) {
      DebugHudState debug = getState(DebugHudState.class);
      this.positionDisplay = debug.createDebugValue("Position", DebugHudState.Location.Top);
      this.speedDisplay = debug.createDebugValue("Speed", DebugHudState.Location.Top);
    }
  }

  @Override
  protected void cleanup(final Application app) {

    inputMapper.removeStateListener(
        this,
        // Weapons
        AvatarMovementFunctions.F_BOMB,
        AvatarMovementFunctions.F_BURST,
        AvatarMovementFunctions.F_THOR,
        AvatarMovementFunctions.F_SHOOT,
        AvatarMovementFunctions.F_GRAVBOMB,
        AvatarMovementFunctions.F_MINE,
        // Actions
        AvatarMovementFunctions.F_REPEL,
        AvatarMovementFunctions.F_WARP,
        // Ships
        AvatarMovementFunctions.F_WARBIRD,
        AvatarMovementFunctions.F_JAVELIN,
        AvatarMovementFunctions.F_SPIDER,
        AvatarMovementFunctions.F_LEVI,
        AvatarMovementFunctions.F_TERRIER,
        AvatarMovementFunctions.F_WEASEL,
        AvatarMovementFunctions.F_LANC,
        AvatarMovementFunctions.F_SHARK);
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

    if (move) {
      MovementInput movementInput = new MovementInput(thrust, facing, FLAGS);
      session.move(movementInput);
      move = false;
    }

    // Get position from server
    Vec3d newPos = session.getPlayerLocation();
    // Update display of position
    long time = System.nanoTime();
    if (time - lastPositionUpdate > UPDATE_POSITION_FREQUENCY) {
      updateShipLocation(newPos);
      lastPositionUpdate = time;
    }

    setLocation(newPos.toVector3f());
  }

  /**
   * Called when player is pressing the turn button (left or right by default).
   *
   * @param value the value of the rotate button
   */
  public void rotatePressed(InputState value) {
    move = true;
    thrust.x = (float) (value.asNumber() * ROTATESPEED);
  }

  /**
   * Called when player is pressing the thrust button (up or down by default). This delegate way can
   * be used when we need to know the value of the button.
   *
   * @param value the value of the thrust button
   */
  public void thrustPressed(InputState value) {
    move = true;
    thrust.z = (float) (value.asNumber() * speed); // Z is forward
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
      if (func == AvatarMovementFunctions.F_WARBIRD) {
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
      } else if (func == AvatarMovementFunctions.F_WARP) {
        session.action(ActionSystem.WARP);
      }
    }
  }

  protected Vec3d updateShipLocation(Vec3d loc) {
    Vec3d newLoc = loc.clone();
    newLoc.x *= -1;

    String s = String.format("%.2f, %.2f, %.2f", newLoc.x, newLoc.y, newLoc.z);
    positionDisplay.setObject(s);

    long time = System.nanoTime();
    if (lastSpeedTime != 0) {
      // Let's go ahead and calculate speed
      double localSpeed = newLoc.subtract(lastPosition).length();

      // And de-integrate it based on the time delta
      localSpeed = localSpeed * 1000000000.0 / (time - lastSpeedTime);

      // A slight smoothing of the value
      speedAverage = (speedAverage * 2 + localSpeed) / 3;

      s = String.format("%.2f", speedAverage);
      speedDisplay.setObject(s);
    }
    lastPosition.set(newLoc);
    lastSpeedTime = time;

    return lastPosition;
  }

  public VersionedReference<Vec3d> createPositionReference() {
    return posHolder.createReference();
  }

  private void setLocation(Vector3f loc) {
    position.set(loc);
    posHolder.incrementVersion();
  }

  @Override
  public void valueActive(FunctionId func, double value, double tpf) {
    if (func == AvatarMovementFunctions.F_BOMB) {
      session.attack(WeaponsSystem.BOMB);
    } else if (func == AvatarMovementFunctions.F_SHOOT) {
      session.attack(WeaponsSystem.GUN);
    } else if (func == AvatarMovementFunctions.F_GRAVBOMB) {
      session.attack(WeaponsSystem.GRAVBOMB);
    } else if (func == AvatarMovementFunctions.F_MINE) {
      session.attack(WeaponsSystem.MINE);
    } else if (func == AvatarMovementFunctions.F_THOR) {
      session.action(ActionSystem.FIRETHOR);
    } else if (func == AvatarMovementFunctions.F_REPEL) {
      session.action(ActionSystem.REPEL);
    } else if (func == AvatarMovementFunctions.F_BURST) {
      session.action(ActionSystem.FIREBURST);
    }
  }
}
