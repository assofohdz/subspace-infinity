// SPDX-License-Identifier: BSD-3-Clause
// Copyright (c) 2018-2026 Asser Fahrenholz

package infinity.client.states;

import com.jme3.app.Application;
import com.jme3.app.state.BaseAppState;
import com.jme3.math.Vector3f;
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
 * Camera-shake feedback state. Watches the local avatar's {@link Jitter}
 * component and, while the deadline is in the future, additively perturbs
 * the camera's world location each frame with a decaying-amplitude random
 * offset.
 *
 * <p>Attach order matters: this state must run <em>after</em>
 * {@link InfinityCameraState} so the per-frame offset is applied on top of
 * CameraState's tracked position. CameraState rewrites the location each
 * frame, so the offset is non-accumulating.
 *
 * <p>Avatar id is lazy-resolved in {@link #update(float)} because
 * {@link GameSessionState} fetches it via RMI and the value may be
 * {@code null} / {@code NULL_ID} when this state initializes. Mirrors the
 * lazy-resolve pattern used in {@link PositionHudState} and elsewhere.
 *
 * <p>Slice 9c-JitterTime, Q6=(a + ii): camera-location perturbation +
 * decaying amplitude. {@link #MAX_OFFSET} is hardcoded today; logged in the
 * polish bag for promotion to engine.groovy per CLAUDE.md rule #4.
 */
public final class JitterState extends BaseAppState {

  /**
   * Peak shake amplitude in world units. Subspace-canon JitterTime values
   * (~0.5-1 second) at this magnitude produce a visible-but-not-jarring
   * shake at the default camera distance (75 world units, jME tile scale).
   * Pattern 4 candidate — promote to {@code engine.groovy} when a per-zone
   * tunable need materializes.
   */
  private static final float MAX_OFFSET = 0.5f;

  static final Logger log = LoggerFactory.getLogger(JitterState.class);

  private EntityData ed;
  private TimeSource timeSource;
  private EntityId avatarEntityId;
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
    if (avatarEntityId == null) {
      final EntityId id = getState(GameSessionState.class).getAvatarEntityId();
      if (id == null || EntityId.NULL_ID.equals(id)) {
        return;
      }
      avatarEntityId = id;
    }
    if (self == null) {
      self = ed.watchEntity(avatarEntityId, Jitter.class);
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
