// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.renderer.Camera;
import com.simsilica.es.EntityData;
import com.simsilica.es.EntityId;
import com.simsilica.es.WatchedEntity;
import com.simsilica.ethereal.TimeSource;
import infinity.client.ConnectionState;
import infinity.client.GameSessionState;
import infinity.es.Jitter;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Camera-shake driven by the local avatar's {@link Jitter} component.
 *
 * <p>Attach order matters: must run <em>after</em> {@link InfinityCameraState} so the
 * per-frame offset stacks on top of CameraState's tracked location. CameraState
 * rewrites the location each frame, so the perturbation is non-accumulating.
 *
 * <p>P4 split: the {@code Jitter} read targets the current ship (which dies + respawns);
 * the watcher rebinds via {@code GameSessionState.getCurrentShipId()}. Ghost state =
 * no current ship = no jitter (no shake to render anyway).
 */
public final class JitterState extends BaseAppState {

  // Tunable candidate — promote to engine.groovy when a per-zone need materializes.
  private static final float MAX_OFFSET = 0.5f;

  static final Logger log = LoggerFactory.getLogger(JitterState.class);

  private EntityData ed;
  private TimeSource timeSource;
  private EntityId watchedShipId;
  private WatchedEntity self;

  @Override
  protected void initialize(final Application app) {
    final ConnectionState conn = getState(ConnectionState.class);
    ed = conn.getEntityData();
    timeSource = conn.getRemoteTimeSource();
  }

  @Override
  protected void cleanup(final Application app) {
    if (self != null) {
      self.release();
      self = null;
    }
  }

  @Override
  protected void onEnable() {
    // no-op
  }

  @Override
  protected void onDisable() {
    // no-op
  }

  @Override
  public void update(final float tpf) {
    final EntityId currentShip = getState(GameSessionState.class).getCurrentShipId();
    if (currentShip == null) {
      // Ghost state — release any prior watcher and skip; no ship → no jitter.
      if (self != null) {
        self.release();
        self = null;
        watchedShipId = null;
      }
      return;
    }
    if (!currentShip.equals(watchedShipId)) {
      if (self != null) {
        self.release();
      }
      self = ed.watchEntity(currentShip, Jitter.class);
      watchedShipId = currentShip;
    }
    self.applyChanges();

    final Jitter j = self.get(Jitter.class);
    if (j == null) {
      return;
    }
    final long now = timeSource.getTime();
    final long endTime = j.getEndTime();
    if (now >= endTime) {
      return; // expired (server reaper hasn't cleared it yet, or we beat the tick)
    }
    final long startTime = j.getStartTime();
    final long span = endTime - startTime;
    if (span <= 0L) {
      return; // degenerate, skip this frame
    }
    final double progress = (double) (now - startTime) / (double) span;
    final float amplitude = MAX_OFFSET * (float) (1.0 - progress);

    final ThreadLocalRandom rng = ThreadLocalRandom.current();
    final float dx = (rng.nextFloat() * 2f - 1f) * amplitude;
    final float dz = (rng.nextFloat() * 2f - 1f) * amplitude;

    final Camera cam = getApplication().getCamera();
    cam.setLocation(cam.getLocation().add(dx, 0f, dz));
  }
}
