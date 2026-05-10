// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.states;

import com.jme3.app.Application;
import com.jme3.math.Vector3f;
import com.simsilica.bpos.BodyPosition;
import com.simsilica.bpos.ChildPositionTransition3d;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.WatchedEntity;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.lemur.GuiGlobals;
import com.simsilica.lemur.input.AnalogFunctionListener;
import com.simsilica.lemur.input.Axis;
import com.simsilica.lemur.input.FunctionId;
import com.simsilica.lemur.input.InputMapper;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.state.CameraState;
import infinity.client.ConnectionState;
import infinity.client.GameSessionClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A state to manage in-game camera. It simply follows the avatar of the player.
 * Mouse wheel scrolling zooms the camera in and out.
 *
 * @author Asser
 */
public class InfinityCameraState extends CameraState implements AnalogFunctionListener {

  public static final float DEFAULT_DISTANCE = 75;
  public static final float MIN_DISTANCE = 10;
  public static final float MAX_DISTANCE = 300;
  public static final float ZOOM_SPEED = 10;

  /** @deprecated use DEFAULT_DISTANCE */
  @Deprecated
  public static final float DISTANCETOPLANE = DEFAULT_DISTANCE;

  static Logger log = LoggerFactory.getLogger(InfinityCameraState.class);

  private final TimeSource time;
  private GameSessionClientService session;
  private final EntityId avatarId;
  private WatchedEntity self;

  private float distance = DEFAULT_DISTANCE;

  public InfinityCameraState(final EntityId avatar, final TimeSource timeSource) {
    super();
    this.avatarId = avatar;
    this.time = timeSource;
  }

  @Override
  protected void initialize(final Application app) {
    EntityData ed = getState(ConnectionState.class).getEntityData();
    session = getState(ConnectionState.class).getService(GameSessionClientService.class);
    self = ed.watchEntity(avatarId, BodyPosition.class);
    log.info("self:{}", self);
    BodyPosition bodyPos = self.get(BodyPosition.class);
    log.info("self pos:{}", bodyPos);
    if (bodyPos != null) {
      bodyPos.initialize(avatarId, 12);
    }
  }

  @Override
  protected void cleanup(final Application app) {
  }

  @Override
  public void update(final float tpf) {
    if (self.applyChanges()) {
      log.info("self changes");
      BodyPosition bodyPos = self.get(BodyPosition.class);
      log.info("self pos update:{}", bodyPos);
      if (bodyPos != null) {
        bodyPos.initialize(avatarId, 12);
      }
    } else {
      BodyPosition bodyPos = self.get(BodyPosition.class);
      updateAvatarPosition(bodyPos);
    }
  }

  private void updateAvatarPosition(final BodyPosition bodyPos) {
    long t = time.getTime();
    ChildPositionTransition3d frame = bodyPos.getFrame(t);
    if (frame == null) {
      if (t != 0) {
        log.warn("no transition frame for time:{}", t);
      }
      return;
    }

    Vec3d v = frame.getPosition(t, true);
    v.addLocal(0, distance, 0);
    session.setView(new Quatd(getApplication().getCamera().getRotation()), v);
  }

  @Override
  public String getId() {
    return "InfinityCameraState";
  }

  @Override
  protected void onEnable() {
    getApplication().getCamera().setLocation(new Vector3f(0, distance, 0));
    getApplication().getCamera().lookAt(new Vector3f(0, 0, 0), Vector3f.UNIT_Y);

    InputMapper inputMapper = GuiGlobals.getInstance().getInputMapper();
    inputMapper.map(CameraMovementFunctions.F_ZOOM, Axis.MOUSE_WHEEL);
    inputMapper.addAnalogListener(this, CameraMovementFunctions.F_ZOOM);
  }

  @Override
  protected void onDisable() {
    InputMapper inputMapper = GuiGlobals.getInstance().getInputMapper();
    inputMapper.removeAnalogListener(this, CameraMovementFunctions.F_ZOOM);
  }

  @Override
  public void valueActive(final FunctionId func, final double value, final double tpf) {
    if (func == CameraMovementFunctions.F_ZOOM) {
      // positive value = scroll up = zoom in (decrease distance)
      distance = (float) Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance - value * ZOOM_SPEED));
    }
  }
}
