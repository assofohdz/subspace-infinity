// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
import com.simsilica.bpos.BodyPosition;
import com.simsilica.bpos.ChildPositionTransition3d;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.WatchedEntity;
import com.simsilica.ethereal.TimeSource;
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
import infinity.es.input.MovementInput;
import infinity.net.GameSession;
import infinity.systems.ship.ConsumableSystem;
import infinity.systems.AvatarSystem;
import infinity.systems.ship.WeaponsSystem;
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
  private GameSession session;
  private long lastPositionUpdate;
  private boolean shiftPressed = false;

  // Interpolated position source (SimEthereal TransitionBuffer via BodyPosition)
  private EntityData ed;
  private TimeSource timeSource;
  private WatchedEntity avatarWatch;
  private EntityId avatarId;
  private BodyPosition avatarBodyPos;

  // Current movement values (set by valueActive, sent in update)
  private double currentRotation = 0;
  private double currentThrust = 0;

  @Override
  protected void initialize(final Application app) {

    BlackboardState blackboard = getState(BlackboardState.class, true);
    blackboard.set("position", posHolder);

    ed = getState(ConnectionState.class).getEntityData();
    timeSource = getState(ConnectionState.class).getRemoteTimeSource();

    log.debug("initialize()");

    if (inputMapper == null) {
      inputMapper = GuiGlobals.getInstance().getInputMapper();
    }

    // Movement and weapons use analog listeners - valueActive() called every frame while held
    inputMapper.addAnalogListener(
        this,
        AvatarMovementFunctions.F_TURN,
        AvatarMovementFunctions.F_THRUST,
        AvatarMovementFunctions.F_BOMB,
        AvatarMovementFunctions.F_BURST,
        AvatarMovementFunctions.F_THOR,
        AvatarMovementFunctions.F_SHOOT,
        AvatarMovementFunctions.F_GRAVBOMB,
        AvatarMovementFunctions.F_MINE,
        AvatarMovementFunctions.F_REPEL);

    // State listeners for non-continuous actions
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
        AvatarMovementFunctions.F_WARP,
        AvatarMovementFunctions.F_SHIFT);

    // Position / Speed displays were previously published into DebugHudState here;
    // PositionHudState now owns the on-screen world+arena coord readout.
  }

  @Override
  protected void cleanup(final Application app) {

    // Remove analog listeners
    inputMapper.removeAnalogListener(
        this,
        AvatarMovementFunctions.F_TURN,
        AvatarMovementFunctions.F_THRUST,
        AvatarMovementFunctions.F_BOMB,
        AvatarMovementFunctions.F_BURST,
        AvatarMovementFunctions.F_THOR,
        AvatarMovementFunctions.F_SHOOT,
        AvatarMovementFunctions.F_GRAVBOMB,
        AvatarMovementFunctions.F_MINE,
        AvatarMovementFunctions.F_REPEL);

    // Remove state listeners
    inputMapper.removeStateListener(
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
        AvatarMovementFunctions.F_WARP,
        AvatarMovementFunctions.F_SHIFT);

    if (avatarWatch != null) {
      avatarWatch.release();
      avatarWatch = null;
    }
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
    inputMapper.activateGroup(AvatarMovementFunctions.G_ALTERNATIVE);

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
    inputMapper.deactivateGroup(AvatarMovementFunctions.G_ALTERNATIVE);
  }

  @Override
  public void update(final float tpf) {

    // Send movement input every frame while there's active input
    // (valueActive updates currentRotation/currentThrust each frame while keys are held)
    thrust.x = currentRotation * ROTATESPEED;
    thrust.z = currentThrust * speed;
    MovementInput movementInput = new MovementInput(thrust.clone(), facing.clone(), FLAGS);
    session.move(movementInput);

    // Reset for next frame - valueActive will set them again if keys are still held
    currentRotation = 0;
    currentThrust = 0;

    // Prefer the SimEthereal-interpolated BodyPosition so the viewRoot translation
    // (driven by posHolder) uses the same smoothed time source that places the
    // avatar ship spatial. Falls back to the RMI location until the buffer fills.
    Vec3d newPos = getInterpolatedAvatarPosition();
    if (newPos == null) {
      newPos = session.getPlayerLocation();
    }

    // Update display of position
    long time = System.nanoTime();
    if (time - lastPositionUpdate > UPDATE_POSITION_FREQUENCY) {
      updateShipLocation(newPos);
      lastPositionUpdate = time;
    }

    setLocation(newPos.toVector3f());
  }

  private Vec3d getInterpolatedAvatarPosition() {
    if (timeSource == null || ed == null) {
      return null;
    }
    if (avatarWatch == null) {
      EntityId id = getState(GameSessionState.class).getAvatarEntityId();
      if (id == null || EntityId.NULL_ID.equals(id)) {
        return null;
      }
      avatarId = id;
      avatarWatch = ed.watchEntity(id, BodyPosition.class);
      BodyPosition bp = avatarWatch.get(BodyPosition.class);
      if (bp != null) {
        bp.initialize(id, 12);
        avatarBodyPos = bp;
      }
    } else if (avatarWatch.applyChanges()) {
      BodyPosition bp = avatarWatch.get(BodyPosition.class);
      if (bp != null) {
        bp.initialize(avatarId, 12);
        avatarBodyPos = bp;
      }
    }
    if (avatarBodyPos == null) {
      return null;
    }
    long t = timeSource.getTime();
    ChildPositionTransition3d frame = avatarBodyPos.getFrame(t);
    if (frame == null) {
      return null;
    }
    return frame.getPosition(t, true);
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

    if (value == InputState.Positive) {
      if (func == AvatarMovementFunctions.F_SHIFT) {
        this.shiftPressed = true;
      }
    }

    if (value == InputState.Off) {
      // Ship selection triggers on key release
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
        session.action(ConsumableSystem.WARP);
      } else if (func == AvatarMovementFunctions.F_SHIFT){
        this.shiftPressed = false;
      }
    }
  }

  protected Vec3d updateShipLocation(Vec3d loc) {
    Vec3d newLoc = loc.clone();

    long time = System.nanoTime();
    if (lastSpeedTime != 0) {
      // Speed tracking — was previously displayed via DebugHudState; the value is
      // now unused but kept so a future consumer (e.g. throttle indicator) can read
      // speedAverage without recomputing it.
      double localSpeed = newLoc.subtract(lastPosition).length();
      localSpeed = localSpeed * 1000000000.0 / (time - lastSpeedTime);
      speedAverage = (speedAverage * 2 + localSpeed) / 3;
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
    // Movement - valueActive is called every frame while input is active
    if (func == AvatarMovementFunctions.F_TURN) {
      currentRotation = value;
    } else if (func == AvatarMovementFunctions.F_THRUST) {
      currentThrust = value;
    }
    // Weapons - continuous fire while held
    else if (func == AvatarMovementFunctions.F_GRAVBOMB) {
      session.attack(WeaponsSystem.GRAVBOMB);
    } else if (func == AvatarMovementFunctions.F_BOMB && !shiftPressed) {
      session.attack(WeaponsSystem.BOMB);
    } else if (func == AvatarMovementFunctions.F_BOMB && shiftPressed) {
      session.attack(WeaponsSystem.MINE);
    } else if (func == AvatarMovementFunctions.F_THOR) {
      session.action(ConsumableSystem.FIRETHOR);
    } else if (func == AvatarMovementFunctions.F_REPEL) {
      session.action(ConsumableSystem.REPEL);
    } else if (func == AvatarMovementFunctions.F_BURST) {
      session.action(ConsumableSystem.FIREBURST);
    } else if (func == AvatarMovementFunctions.F_SHOOT && !shiftPressed) {
      session.attack(WeaponsSystem.GUN);
    }
  }
}
