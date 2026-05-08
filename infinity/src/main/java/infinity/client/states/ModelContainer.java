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

import com.simsilica.es.ComponentFilter;
import com.simsilica.es.Entity;
import com.simsilica.es.EntityContainer;
import com.simsilica.es.EntityData;
import com.simsilica.ext.mphys.ShapeInfo;
import com.simsilica.ext.mphys.SpawnPosition;
import infinity.es.Hidden;

/** Keeps track of the static models in the scene. */
class ModelContainer extends EntityContainer<Model> {
  private final ModelViewState owner;
  private final EntityData ed;

  ModelContainer(final ModelViewState owner, final EntityData ed) {
    super(ed, SpawnPosition.class, ShapeInfo.class);
    this.owner = owner;
    this.ed = ed;
  }

  @Override
  public void setFilter(final ComponentFilter filter) {
    super.setFilter(filter);
  }

  @Override
  public Model[] getArray() {
    return super.getArray();
  }

  @Override
  protected Model addObject(final Entity e) {
    final Model object = owner.getModel(e.getId(), true);
    if (ed.getComponent(e.getId(), Hidden.class) != null) {
      // Slice 8d: server-declared hidden — track the entity (so add/remove
      // edges stay clean in the container's internal map) but skip the
      // spatial bind + visibility queue. Server still owns collision +
      // pickup; the client just doesn't render.
      if (ModelViewState.log.isInfoEnabled()) {
        ModelViewState.log.info("skip hidden model for: {}", e.getId());
      }
      return object;
    }
    if (ModelViewState.log.isInfoEnabled()) {
      ModelViewState.log.info(
          "add model for:{}   at time:{}", e.getId(), owner.timeSource.getTime());
    }
    updateObject(object, e);

    // Add it to the queue to be made visible at a future time
    owner.markerQueue.add(
        new MarkVisible(object, owner.timeSource.getTime() + ModelViewState.VIS_DELAY));

    return object;
  }

  @Override
  protected void updateObject(final Model object, final Entity e) {
    if (ed.getComponent(e.getId(), Hidden.class) != null) {
      // Hidden — never bind shape / position; the model stays empty.
      return;
    }
    object.setShape(e.get(ShapeInfo.class));
    object.setPosition(e.get(SpawnPosition.class));

    // Check if the entity is a flag and if so, update the flag materials
    if (owner.flags.containsId(e.getId())) {
      owner.updateSingleFlagMaterial(owner.avatarFrequency, e);
    }
  }

  @Override
  protected void removeObject(final Model object, final Entity e) {
    if (ModelViewState.log.isInfoEnabled()) {
      ModelViewState.log.info("remove model for:{}", e.getId());
    }
    owner.releaseModel(e.getId());
  }
}
