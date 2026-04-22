/*
 * Copyright (c) 2018-2026, Asser Fahrenholz
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
    log.info(String.format("self:%s", self));
    BodyPosition bodyPos = self.get(BodyPosition.class);
    log.info(String.format("self pos:%s", bodyPos));
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
      log.info(String.format("self pos update:%s", bodyPos));
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
        log.warn(String.format("no transition frame for time:%d", t));
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
