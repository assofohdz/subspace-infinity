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

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.simsilica.bpos.BodyPosition;
import com.simsilica.bpos.ChildPositionTransition3d;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityId;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.mathd.trans.TransitionBuffer;
import java.util.Objects;

/** Visual companion to a server-side rigid body — drives a {@link Model}'s transform from a {@link BodyPosition} buffer. */
class Body {
  private final ModelViewState owner;
  private final Entity entity;
  final Model model;
  private boolean visible;
  // Field kept for parity with the previous inner-class shape; not currently
  // mutated by any code path.
  @SuppressWarnings("unused")
  private boolean forceInvisible;
  private TransitionBuffer<ChildPositionTransition3d> buffer;
  private EntityId lastParent;

  Body(final ModelViewState owner, final Entity entity) {
    this.owner = owner;
    this.entity = entity;
    this.model = owner.getModel(entity.getId(), true);
    model.setDynamic(true);
  }

  public void setShape(final ShapeInfo shapeInfo) {
    model.setShape(shapeInfo);
  }

  public void setPosition(final BodyPosition pos) {
    // BodyPosition requires special management to make
    // sure all instances of BodyPosition are sharing the same
    // thread-safe history buffer.  Everywhere it's used, it should
    // be 'initialized'.
    pos.initialize(entity.getId(), 12);
    this.buffer = pos.getBuffer();
  }

  @SuppressWarnings("PMD.CompareObjectsWithEquals") // scene-graph identity: parent Spatial reference
  public void update(final long time) {
    // Look back in the brief history that we've kept and
    // pull an interpolated value.  To do this, we grab the
    // span of time that contains the time we want.  PositionTransition3d
    // represents a starting and an ending pos+rot over a span of time.
    final ChildPositionTransition3d trans = buffer.getTransition(time);
    if (trans == null) {
      return;
    }
    final Vector3f transPos = trans.getPosition(time, true).toVector3f();
    final Quaternion rot = trans.getRotation(time, true).toQuaternion();

    if (model.spatial.getParent() == owner.getViewRoot()) {
      // Make the position relative to our "conveyor"
      transPos.subtractLocal(owner.centerWorld.toVector3f());
    }
    model.spatial.setLocalTranslation(transPos);
    model.spatial.setLocalRotation(rot);

    setVisible(trans.getVisibility(time));

    // See if it's connected to a parent
    final EntityId parentId = trans.getParentId(time, true);

    if (!Objects.equals(parentId, lastParent)) {
      // Now make the parent right
      if (parentId == null) {
        owner.getViewRoot().attachChild(model.spatial);
        lastParent = parentId;
      } else {
        // See if we have a parent model already.  We look it up directly so
        // it doesn't trigger an "acquire" — the usage count is for managing
        // bodies versus statics, not for parent-of relationships.
        final Model parent = owner.modelIndex.get(parentId);
        if (parent != null) {
          ((Node) parent.spatial).attachChild(model.spatial);
        }
        lastParent = parentId;
      }
    }
  }

  void setVisible(final boolean f) {
    if (this.visible == f) {
      return;
    }
    this.visible = f;
    if (visible) {
      model.markVisible();
    } else {
      model.markInvisible();
    }
  }

  public void release() {
    owner.releaseModel(entity.getId());
    model.setDynamic(false);
  }
}
