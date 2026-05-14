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
import infinity.es.ship.weapons.WeaponType;
import infinity.net.ConsumableTypeId;
import infinity.net.GameSession;
import infinity.net.ShipTypeId;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Translates input state into {@link MovementInput} + weapon RMI calls for the local avatar,
 * and publishes the avatar's interpolated world position to {@code BlackboardState["position"]}
 * for other states ({@link infinity.client.states.PositionHudState},
 * {@link infinity.client.states.LocalViewState}) to read.
 *
 * <p>Position comes from the SimEthereal-interpolated {@link BodyPosition} buffer, not
 * {@code ed.getComponent} — the avatar id is lazy-resolved (RMI roundtrip) and watched
 * via {@link com.simsilica.es.EntityData#watchEntity}, per {@code client-read-only.md}.
 * Falls back to {@code session.getPlayerLocation()} until the buffer fills.
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
        AvatarMovementFunctions.F_REPEL,
        AvatarMovementFunctions.F_ROCKET);

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
        AvatarMovementFunctions.F_REPEL,
        AvatarMovementFunctions.F_ROCKET);

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
      lazyBindAvatarWatch();
    } else if (avatarWatch.applyChanges()) {
      refreshAvatarBodyPos();
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

  /** Resolve the avatar entity id and start a watch + initial BodyPosition bind. */
  private void lazyBindAvatarWatch() {
    EntityId id = getState(GameSessionState.class).getAvatarEntityId();
    if (id == null || EntityId.NULL_ID.equals(id)) {
      return;
    }
    avatarId = id;
    avatarWatch = ed.watchEntity(id, BodyPosition.class);
    BodyPosition bp = avatarWatch.get(BodyPosition.class);
    if (bp != null) {
      bp.initialize(id, 12);
      avatarBodyPos = bp;
    }
  }

  /** Re-bind the latest BodyPosition after the watch reported a change. */
  private void refreshAvatarBodyPos() {
    BodyPosition bp = avatarWatch.get(BodyPosition.class);
    if (bp != null) {
      bp.initialize(avatarId, 12);
      avatarBodyPos = bp;
    }
  }

  @Override
  public void valueChanged(final FunctionId func, final InputState value, final double tpf) {
    final boolean b = value == InputState.Positive;

    if (func == AvatarMovementFunctions.F_RUN) {
      speed = b ? 2 : 1;
    }

    if (value == InputState.Positive && func == AvatarMovementFunctions.F_SHIFT) {
      this.shiftPressed = true;
    }

    if (value == InputState.Off) {
      handleKeyReleased(func);
    }
  }

  /** Ship selection + warp + shift-release dispatch on key release. */
  private void handleKeyReleased(final FunctionId func) {
    final byte ship = shipForFunction(func);
    if (ship >= 0) {
      session.avatar(ship);
      return;
    }
    if (func == AvatarMovementFunctions.F_WARP) {
      session.action(ConsumableTypeId.WARP.wireId());
    } else if (func == AvatarMovementFunctions.F_SHIFT) {
      this.shiftPressed = false;
    }
  }

  /** Ship-selection key → {@link ShipTypeId} wire byte. {@link #shipForFunction} returns -1 on miss. */
  private static final Map<FunctionId, Byte> SHIP_KEYS = buildShipKeys();

  private static Map<FunctionId, Byte> buildShipKeys() {
    final Map<FunctionId, Byte> m = new HashMap<>();
    m.put(AvatarMovementFunctions.F_WARBIRD, ShipTypeId.WARBIRD.wireId());
    m.put(AvatarMovementFunctions.F_JAVELIN, ShipTypeId.JAVELIN.wireId());
    m.put(AvatarMovementFunctions.F_SPIDER, ShipTypeId.SPIDER.wireId());
    m.put(AvatarMovementFunctions.F_LEVI, ShipTypeId.LEVI.wireId());
    m.put(AvatarMovementFunctions.F_TERRIER, ShipTypeId.TERRIER.wireId());
    m.put(AvatarMovementFunctions.F_WEASEL, ShipTypeId.WEASEL.wireId());
    m.put(AvatarMovementFunctions.F_LANC, ShipTypeId.LANCASTER.wireId());
    m.put(AvatarMovementFunctions.F_SHARK, ShipTypeId.SHARK.wireId());
    return Map.copyOf(m);
  }

  /** Map a ship-selection function key to its {@link ShipTypeId} wire byte, or {@code -1} if none. */
  private static byte shipForFunction(final FunctionId func) {
    final Byte b = SHIP_KEYS.get(func);
    return b == null ? -1 : b;
  }

  protected Vec3d updateShipLocation(final Vec3d loc) {
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

  private void setLocation(final Vector3f loc) {
    position.set(loc);
    posHolder.incrementVersion();
  }

  @Override
  public void valueActive(final FunctionId func, final double value, final double tpf) {
    // Movement - valueActive is called every frame while input is active
    if (func == AvatarMovementFunctions.F_TURN) {
      currentRotation = value;
      return;
    }
    if (func == AvatarMovementFunctions.F_THRUST) {
      currentThrust = value;
      return;
    }
    dispatchHeldWeapon(func);
  }

  /** Continuous fire while held — bombs/mines/thor/repel/burst/bullets. */
  private void dispatchHeldWeapon(final FunctionId func) {
    if (func == AvatarMovementFunctions.F_BOMB) {
      // Shift+TAB swaps bomb→mine; bare TAB stays bomb.
      session.attack(shiftPressed ? WeaponType.MINE : WeaponType.BOMB);
      return;
    }
    if (func == AvatarMovementFunctions.F_SHOOT) {
      // Shift suppresses primary fire (reserved for future toggles).
      if (!shiftPressed) {
        session.attack(WeaponType.BULLET);
      }
      return;
    }
    dispatchSimpleWeapon(func);
  }

  /** Plain func → session.attack/action with no shift modifier. */
  private void dispatchSimpleWeapon(final FunctionId func) {
    if (func == AvatarMovementFunctions.F_GRAVBOMB) {
      session.attack(WeaponType.GRAVBOMB);
    } else if (func == AvatarMovementFunctions.F_THOR) {
      session.action(ConsumableTypeId.FIRETHOR.wireId());
    } else if (func == AvatarMovementFunctions.F_REPEL) {
      session.action(ConsumableTypeId.REPEL.wireId());
    } else if (func == AvatarMovementFunctions.F_BURST) {
      session.action(ConsumableTypeId.FIREBURST.wireId());
    } else if (func == AvatarMovementFunctions.F_ROCKET) {
      session.action(ConsumableTypeId.FIREROCKET.wireId());
    }
  }
}
