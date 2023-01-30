/*
 * Copyright (c) 2018, Asser Fahrenholz
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

import com.badlogic.gdx.math.Vector3;
import com.jme3.app.Application;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.simsilica.bpos.BodyPosition;
import com.simsilica.bpos.ChildPositionTransition3d;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.WatchedEntity;
import com.simsilica.ethereal.TimeSource;
import com.simsilica.mathd.Quatd;
import com.simsilica.mathd.Vec3d;
import com.simsilica.state.CameraState;
import infinity.client.ConnectionState;
import infinity.client.GameSessionClientService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A state to manage in-game camera. It simply follows the avatar of the player.
 *
 * @author Asser
 */
public class InfinityCameraState extends CameraState {

  public static final float DISTANCETOPLANE = 100;
  static Logger log = LoggerFactory.getLogger(InfinityCameraState.class);
  private final TimeSource time;
  private GameSessionClientService session;
  private final EntityId avatarId;
  private WatchedEntity self;
  private EntityData ed;

  public InfinityCameraState(EntityId avatar, TimeSource timeSource) {
    super();
    this.avatarId = avatar;
    this.time = timeSource;
  }

  @Override
  protected void initialize(final Application app) {

    this.ed = getState(ConnectionState.class).getEntityData();

    session = getState(ConnectionState.class).getService(GameSessionClientService.class);

    self = ed.watchEntity(avatarId, BodyPosition.class);
    log.info("self:" + self);
    BodyPosition bodyPos = self.get(BodyPosition.class);
    log.info("self pos:" + bodyPos);
    if (bodyPos != null) {
      // Need to initialize the shared transition buffer
      bodyPos.initialize(avatarId, 12);
    }

    //Flip camera to loo
    //getApplication().getCamera().setRotation(new Quaternion().fromAngleAxis(FastMath.PI, new Vector3f(0,0,1));
    //getApplication().getCamera().lookAt(new Vector3f(0,0,0), Vector3f.UNIT_Y);
    // Vector3f(0,1,0)));
  }

  @Override
  protected void cleanup(final Application app) {
    // Nothing tp do here for now
  }

  @Override
  public void update(final float tpf) {
    if (self.applyChanges()) {
      log.info("self changes");
      BodyPosition bodyPos = self.get(BodyPosition.class);
      log.info("self pos update:" + bodyPos);
      if (bodyPos != null) {
        // Need to initialize the shared transition buffer
        bodyPos.initialize(avatarId, 12);
      }
    } else {
      BodyPosition bodyPos = self.get(BodyPosition.class);
      updateAvatarPosition(bodyPos);
    }
  }

  private void updateAvatarPosition(BodyPosition bodyPos) {
    // Note to future self: We use the frame positions and not bodyPos.getLastLocation() because
    // bodyPos.getLastLocation() is a server side method
    long t = time.getTime();
    ChildPositionTransition3d frame = bodyPos.getFrame(t);
    if (frame == null) {
      if (t != 0) {
        log.warn("no transition frame for time: {0}", t);
      }
      return;
    }

    // Only care about position at the moment
    Vec3d v = frame.getPosition(t, true);

    v.addLocal(0, InfinityCameraState.DISTANCETOPLANE, 0);

    getState(WorldViewState.class).setViewLocation(v.toVector3f());

    session.setView(new Quatd(getApplication().getCamera().getRotation()), v);
  }

  @Override
  public String getId() {
    return "InfinityCameraState";
  }

  @Override
  protected void onEnable() {
    getApplication().getCamera().setLocation(new Vector3f(0, InfinityCameraState.DISTANCETOPLANE, 0));
    getApplication().getCamera().lookAt(new Vector3f(0, 0, 0), Vector3f.UNIT_Y);
  }

  @Override
  protected void onDisable() {
    // Nothing tp do here for now
  }
}
