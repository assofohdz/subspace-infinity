/*
 * $Id$
 *
 * Copyright (c) 2018, Simsilica, LLC
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package infinity.client.states;

import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.Mass;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import java.util.Objects;

/**
 * Visual model bound to one entity. A single entity can be matched by both
 * {@link BodyContainer} (has {@code BodyPosition}) and {@link ModelContainer}
 * (has {@code SpawnPosition}) — for example, every rigid body has both. The
 * ref-count keeps the spatial alive until both containers drop it, and
 * {@code dynamic = true} suppresses {@code SpawnPosition} writes (the body
 * track always wins).
 *
 * <p>A {@code BodyPosition} can also arrive without a {@code SpawnPosition}
 * (entity moved into a paged zone we observe before crossing the spawn cell),
 * so the ref-count handles either container appearing first.
 */
class Model {
  private final ModelViewState owner;
  final EntityId entityId;
  Spatial spatial;
  private ShapeInfo shapeInfo;
  int useCount;
  boolean dynamic;
  private SpawnPosition pos;
  private int visibleCount;

  Model(final ModelViewState owner, final EntityId entityId) {
    this.owner = owner;
    this.entityId = entityId;
  }

  public void acquire() {
    useCount++;
  }

  public boolean release() {
    useCount--;
    if (useCount <= 0) {
      if (spatial != null) {
        spatial.removeFromParent();
      }
      return true;
    }
    return false;
  }

  public void setShape(final ShapeInfo shapeInfo) {
    if (Objects.equals(this.shapeInfo, shapeInfo)) {
      return;
    }
    this.shapeInfo = shapeInfo;
    if (spatial != null) {
      spatial.removeFromParent();
    }
    final Mass mass = owner.ed.getComponent(entityId, Mass.class);
    spatial = owner.createModel(entityId, shapeInfo, mass);
    if (spatial != null) {
      owner.getViewRoot().attachChild(spatial);
      resetVisibility();
    }
  }

  public void setPosition(final SpawnPosition pos) {
    this.pos = pos;
    updateRelativePosition();
  }

  public void updateRelativePosition() {
    if (!dynamic) {
      if (pos == null) {
        // We are not a static model and we are probably being removed
        ModelViewState.log.info("dynamic=false, pos=null, useCount={}", useCount);
      } else {
        final Vector3f loc = pos.getLocation().toVector3f();

        // Make the position relative to our "conveyor"
        loc.subtractLocal(owner.centerWorld.toVector3f());

        spatial.setLocalTranslation(loc);
        ModelViewState.log.info("updateRelPos({}):{}", entityId, loc);
        spatial.setLocalRotation(pos.getOrientation().toQuaternion());
      }
    }
  }

  public void setDynamic(final boolean dynamic) {
    if (this.dynamic == dynamic) {
      return;
    }
    this.dynamic = dynamic;
    if (!dynamic) {
      updateRelativePosition();
    }
  }

  void markVisible() {
    visibleCount++;
    resetVisibility();
  }

  void markInvisible() {
    visibleCount--;
    resetVisibility();
  }

  void resetVisibility() {
    ModelViewState.log.info("resetVisibility():{}", visibleCount);
    if (visibleCount > 0) {
      // Spatials marked "arena" opt out of frustum culling — the wireframe
      // cube extends y=0..1024 but the camera sits ~75 above the avatar,
      // so most of the bounding box is behind the camera and JME's frustum
      // test would otherwise drop the spatial when alongside.
      final boolean noCull = spatial.getUserData("arena") != null;
      ModelViewState.log.info("visible:{}", entityId);
      spatial.setCullHint(noCull ? Spatial.CullHint.Never : Spatial.CullHint.Inherit);
    } else {
      ModelViewState.log.info("invisible:{}", entityId);
      spatial.setCullHint(Spatial.CullHint.Always);
    }
  }
}
